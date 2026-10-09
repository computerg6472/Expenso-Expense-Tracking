package com.spendwise.app.data.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import com.spendwise.app.data.backup.snapshot.BackupCryptoManager
import com.spendwise.app.data.backup.snapshot.CryptoException
import com.spendwise.app.data.backup.snapshot.SnapshotHeader
import com.spendwise.app.data.backup.snapshot.SnapshotSerializer
import com.spendwise.app.data.cloud.vault.CloudBackupVaultManager
import com.spendwise.app.data.cloud.vault.VaultRestoreResult
import com.spendwise.app.data.drive.DriveBackupFile
import com.spendwise.app.data.drive.DriveRestClient
import com.spendwise.app.data.drive.DriveResult
import com.spendwise.app.data.drive.GoogleDriveAuthManager
import com.spendwise.app.data.local.dao.*
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.model.DefaultCategories
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupOutcome {
    data class Success(
        val expensesCount: Int,
        val monthsCount: Int,
        val sizeBytes: Long,
        val timestamp: Long
    ) : BackupOutcome()
    data class NeedsAuthorization(val consentIntent: Intent? = null) : BackupOutcome()
    data class Error(val message: String, val cause: Throwable? = null) : BackupOutcome()
}

@Singleton
class DriveBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val expenseDao: ExpenseDao,
    private val incomeMonthDao: IncomeMonthDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val creditCardLedgerDao: CreditCardLedgerDao,
    private val googleDriveAuthManager: GoogleDriveAuthManager,
    private val driveRestClient: DriveRestClient,
    private val backupCryptoManager: BackupCryptoManager,
    private val snapshotSerializer: SnapshotSerializer,
    private val databaseBackupEngine: DatabaseBackupEngine,
    private val cloudBackupVaultManager: CloudBackupVaultManager,
    private val database: com.spendwise.app.data.local.SpendWiseDatabase,
    private val firebaseCloudSyncRepository: com.spendwise.app.data.firebase.FirebaseCloudSyncRepository
) {
    companion object {
        private const val TAG = "DriveBackupRepo"
    }

    private val _restoreStep = MutableStateFlow<RestoreStep>(RestoreStep.SignedIn)
    val restoreStep: StateFlow<RestoreStep> = _restoreStep.asStateFlow()

    /**
     * Checks if a backup file exists in the user's hidden Google Drive appDataFolder.
     */
    suspend fun checkCloudBackupAvailable(email: String): DriveResult<DriveBackupFile?> = withContext(Dispatchers.IO) {
        driveRestClient.getLatestBackupFile(email)
    }

    /**
     * Complete restore pipeline on new phone or reinstall.
     * 1. Reaches Google Drive hidden appDataFolder.
     * 2. If expenso_backup_latest.enc is found:
     *    - Downloads, decrypts, and verifies checksum.
     *    - Atomically restores all categories, budgets, income months, expenses, and credit card entries.
     * 3. Fallback to local MediaStore vault if Drive is offline or has no backup.
     * 4. Treats as brand-new account only if absolutely no backup exists.
     */
    suspend fun restore(
        uid: String,
        email: String,
        password: String? = null,
        recoveryKey: String? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        _restoreStep.value = RestoreStep.SignedIn
        _restoreStep.value = RestoreStep.ReachingCloud

        Log.i(TAG, "Starting restore pipeline for email=$email, uid=$uid")

        // 1. Primary: Restore directly from Firebase Firestore Cloud Backup
        _restoreStep.value = RestoreStep.CheckingBackup
        when (val fbResult = firebaseCloudSyncRepository.restoreFromCloud()) {
            is com.spendwise.app.data.firebase.FirebaseSyncResult.Success -> {
                Log.i(TAG, "Restored directly from Firebase Cloud: ${fbResult.expensesCount} expenses across ${fbResult.monthsCount} months")
                _restoreStep.value = RestoreStep.LoadingHistory(done = 1, total = 1)
                _restoreStep.value = RestoreStep.Complete
                appPreferences.setOnboardingDone(true)
                return@withContext RestoreResult.Success(
                    expensesRestored = fbResult.expensesCount,
                    monthsRestored = fbResult.monthsCount,
                    fromCache = false
                )
            }
            is com.spendwise.app.data.firebase.FirebaseSyncResult.Error -> {
                Log.w(TAG, "Firebase Cloud restore outcome: ${fbResult.message}")
            }
        }

        // 2. Secondary fallback: Check Google Drive appDataFolder if email is available
        if (email.isNotBlank()) {
            _restoreStep.value = RestoreStep.CheckingBackup
            when (val driveFileResult = driveRestClient.getLatestBackupFile(email)) {
                is DriveResult.Success -> {
                    val latestFile = driveFileResult.data
                    if (latestFile != null) {
                        Log.i(TAG, "Drive backup found: ${latestFile.name}, size=${latestFile.sizeBytes} bytes. Downloading...")
                        _restoreStep.value = RestoreStep.LoadingHistory(done = 0, total = 1)

                        when (val downloadResult = driveRestClient.downloadFile(latestFile.id, email)) {
                            is DriveResult.Success -> {
                                val encryptedBytes = downloadResult.data
                                val restoreOutcome = processAndApplyEncryptedBackup(
                                    encryptedBytes = encryptedBytes,
                                    email = email,
                                    password = password,
                                    recoveryKey = recoveryKey
                                )
                                if (restoreOutcome is RestoreResult.Success) {
                                    _restoreStep.value = RestoreStep.Complete
                                    return@withContext restoreOutcome
                                } else if (restoreOutcome is RestoreResult.Failure.PasswordRequired) {
                                    return@withContext restoreOutcome
                                }
                                Log.w(TAG, "Cloud restore attempt failed; trying local fallbacks...")
                            }
                            is DriveResult.Error.Unauthorized -> {
                                Log.w(TAG, "Drive unauthorized during restore download: ${downloadResult.message}")
                                return@withContext RestoreResult.Failure.PermissionDenied("Google Drive permission required to download backup.")
                            }
                            is DriveResult.Error -> {
                                Log.w(TAG, "Drive download error: ${downloadResult.message}")
                            }
                        }
                    } else {
                        Log.i(TAG, "No backup file found in Google Drive appDataFolder.")
                    }
                }
                is DriveResult.Error.Unauthorized -> {
                    Log.w(TAG, "Google Drive not authorized for $email — checking local safe vault and auto-backup...")
                    try {
                        val vaultResult = cloudBackupVaultManager.restoreBackup(email, uid)
                        if (vaultResult is VaultRestoreResult.Success && (vaultResult.expensesRestored > 0 || vaultResult.monthsRestored > 0)) {
                            Log.i(TAG, "Restored from local safe vault fallback: ${vaultResult.expensesRestored} expenses")
                            _restoreStep.value = RestoreStep.Complete
                            appPreferences.setOnboardingDone(true)
                            return@withContext RestoreResult.Success(
                                expensesRestored = vaultResult.expensesRestored,
                                monthsRestored = vaultResult.monthsRestored,
                                fromCache = true
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Local vault fallback check: ${e.message}")
                    }
                    val localCount = expenseDao.getAllIncludingDeleted().count { !it.isDeleted }
                    if (localCount > 0) {
                        Log.i(TAG, "Local Room database has $localCount expenses")
                        _restoreStep.value = RestoreStep.Complete
                        appPreferences.setOnboardingDone(true)
                        return@withContext RestoreResult.Success(
                            expensesRestored = localCount,
                            monthsRestored = incomeMonthDao.getAllIncludingDeleted().count { !it.isDeleted },
                            fromCache = true
                        )
                    }
                    Log.i(TAG, "No prior backup found anywhere — starting fresh account for $email")
                    seedDefaultCategories()
                    _restoreStep.value = RestoreStep.Complete
                    appPreferences.setOnboardingDone(true)
                    return@withContext RestoreResult.NewAccount(uid)
                }
                is DriveResult.Error -> {
                    Log.w(TAG, "Drive error: ${driveFileResult.message} — checking vault fallback")
                    try {
                        val vaultResult = cloudBackupVaultManager.restoreBackup(email, uid)
                        if (vaultResult is VaultRestoreResult.Success && (vaultResult.expensesRestored > 0 || vaultResult.monthsRestored > 0)) {
                            Log.i(TAG, "Restored from vault fallback: ${vaultResult.expensesRestored} expenses")
                            _restoreStep.value = RestoreStep.Complete
                            appPreferences.setOnboardingDone(true)
                            return@withContext RestoreResult.Success(
                                expensesRestored = vaultResult.expensesRestored,
                                monthsRestored = vaultResult.monthsRestored,
                                fromCache = true
                            )
                        }
                    } catch (_: Exception) {}
                    val localCount = expenseDao.getAllIncludingDeleted().count { !it.isDeleted }
                    if (localCount > 0) {
                        _restoreStep.value = RestoreStep.Complete
                        appPreferences.setOnboardingDone(true)
                        return@withContext RestoreResult.Success(
                            expensesRestored = localCount,
                            monthsRestored = incomeMonthDao.getAllIncludingDeleted().count { !it.isDeleted },
                            fromCache = true
                        )
                    }
                    seedDefaultCategories()
                    _restoreStep.value = RestoreStep.Complete
                    appPreferences.setOnboardingDone(true)
                    return@withContext RestoreResult.NewAccount(uid)
                }
            }
        }

        // 2. Fallback: Check local MediaStore safe vault
        try {
            val vaultResult = cloudBackupVaultManager.restoreBackup(email, uid)
            if (vaultResult is VaultRestoreResult.Success && (vaultResult.expensesRestored > 0 || vaultResult.monthsRestored > 0)) {
                Log.i(TAG, "Restored from local vault: ${vaultResult.expensesRestored} expenses")
                _restoreStep.value = RestoreStep.Complete
                appPreferences.setOnboardingDone(true)
                return@withContext RestoreResult.Success(
                    expensesRestored = vaultResult.expensesRestored,
                    monthsRestored = vaultResult.monthsRestored,
                    fromCache = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vault restore check exception: ${e.message}")
        }

        // 3. Fallback: Check if local Room database already has expenses
        val localCount = expenseDao.getAllIncludingDeleted().count { !it.isDeleted }
        if (localCount > 0) {
            Log.i(TAG, "Local Room database already has $localCount expenses")
            _restoreStep.value = RestoreStep.Complete
            appPreferences.setOnboardingDone(true)
            return@withContext RestoreResult.Success(
                expensesRestored = localCount,
                monthsRestored = incomeMonthDao.getAllIncludingDeleted().count { !it.isDeleted },
                fromCache = true
            )
        }

        // 4. Truly fresh new account: seed default categories
        Log.i(TAG, "No prior backup found anywhere; seeding default categories for fresh account.")
        seedDefaultCategories()
        _restoreStep.value = RestoreStep.Complete
        appPreferences.setOnboardingDone(true)
        RestoreResult.NewAccount(uid)
    }

    /**
     * Decrypts, verifies, and applies the downloaded encrypted snapshot.
     */
    private suspend fun processAndApplyEncryptedBackup(
        encryptedBytes: ByteArray,
        email: String,
        password: String?,
        recoveryKey: String?
    ): RestoreResult {
        return try {
            val mode = backupCryptoManager.inspectEnvelopeMode(encryptedBytes)
            if (mode == SnapshotHeader.ENCRYPTION_MODE_PASSWORD && password.isNullOrBlank() && recoveryKey.isNullOrBlank()) {
                Log.i(TAG, "Backup is password protected; prompt required.")
                return RestoreResult.Failure.PasswordRequired(mode)
            }

            val decryptedGzip = backupCryptoManager.decrypt(
                encryptedFileBytes = encryptedBytes,
                accountEmail = email,
                password = password?.toCharArray(),
                recoveryKeyHex = recoveryKey
            )

            val jsonString = snapshotSerializer.decompressGzip(decryptedGzip)
            val snapshot = snapshotSerializer.fromJson(jsonString)

            // Verify content integrity
            val calculatedHash = snapshotSerializer.computeSha256(jsonString.toByteArray(Charsets.UTF_8))
            if (snapshot.header.contentHash.isNotBlank() && snapshot.header.contentHash != calculatedHash) {
                Log.w(TAG, "Hash mismatch: expected=${snapshot.header.contentHash}, calculated=$calculatedHash")
            }

            // Apply all data atomically into Room
            databaseBackupEngine.applySnapshot(snapshot)

            RestoreResult.Success(
                expensesRestored = snapshot.header.expensesCount,
                monthsRestored = snapshot.header.monthsCount
            )
        } catch (e: CryptoException.WrongPasswordOrKey) {
            Log.e(TAG, "Wrong password or key: ${e.message}")
            RestoreResult.Failure.Unknown("Incorrect password or recovery key", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying backup: ${e.message}", e)
            RestoreResult.Failure.Unknown("Failed to decrypt and restore backup: ${e.message}", e)
        }
    }

    private suspend fun seedDefaultCategories() {
        val existing = categoryDao.getAllIncludingDeleted().map { it.id }.toSet()
        val deviceId = appPreferences.getOrCreateDeviceId()
        DefaultCategories.ALL.forEach {
            if (it.id !in existing) {
                categoryDao.insert(it.copy(deviceId = deviceId))
            }
        }
    }

    /**
     * Executes backup immediately to Google Drive with safe rotation and local fallback vault.
     */
    suspend fun backupNow(forceUpload: Boolean = true): BackupOutcome = withContext(Dispatchers.IO) {
        val email = appPreferences.userEmail.first()
        if (email.isBlank()) {
            return@withContext BackupOutcome.Error("Please sign in with Google to enable cloud backup.")
        }

        val enabled = appPreferences.driveBackupEnabled.first()
        if (!enabled) {
            return@withContext BackupOutcome.Error("Google Drive backup is disabled in settings.")
        }

        appPreferences.setBackupStatus("BACKING_UP")

        // 1. Primary: Back up directly to Firebase Firestore Cloud
        when (val fbOutcome = firebaseCloudSyncRepository.backupToCloud()) {
            is com.spendwise.app.data.firebase.FirebaseSyncResult.Success -> {
                Log.i(TAG, "backupNow: Firebase cloud backup succeeded: ${fbOutcome.expensesCount} expenses")
                appPreferences.setBackupSuccess(
                    timestamp = fbOutcome.timestamp,
                    sizeBytes = 0L,
                    expensesCount = fbOutcome.expensesCount,
                    monthsCount = fbOutcome.monthsCount,
                    generation = 1L,
                    contentHash = "firebase"
                )
                return@withContext BackupOutcome.Success(
                    expensesCount = fbOutcome.expensesCount,
                    monthsCount = fbOutcome.monthsCount,
                    sizeBytes = 0L,
                    timestamp = fbOutcome.timestamp
                )
            }
            is com.spendwise.app.data.firebase.FirebaseSyncResult.Error -> {
                Log.w(TAG, "backupNow: Firebase cloud backup error: ${fbOutcome.message}")
            }
        }

        // Persist local safe vault as safety net
        try {
            val uid = appPreferences.userUid.first()
            cloudBackupVaultManager.persistBackup(email, uid)
        } catch (e: Exception) {
            Log.w(TAG, "Local vault backup warning: ${e.message}")
        }

        try {
            // 1. Verify Drive token / authorization
            val token = googleDriveAuthManager.getValidAccessToken()
            if (token.isNullOrBlank()) {
                val consentIntent = googleDriveAuthManager.getLastRecoverableIntent()
                appPreferences.setBackupStatus("ERROR", "Google Drive authorization required")
                return@withContext BackupOutcome.NeedsAuthorization(consentIntent)
            }

            // 2. Commit all transactions directly into primary .db before snapshot creation
            database.checkpointWal()

            val encryptionModeStr = appPreferences.backupEncryptionMode.first()
            val encryptionMode = when (encryptionModeStr) {
                "PASSWORD" -> SnapshotHeader.ENCRYPTION_MODE_PASSWORD
                "RECOVERY_KEY" -> SnapshotHeader.ENCRYPTION_MODE_RECOVERY_KEY
                else -> SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND
            }
            val includeChat = appPreferences.backupIncludeAiChat.first()

            val (snapshot, gzipBytes) = databaseBackupEngine.createSnapshot(
                accountEmail = email,
                encryptionMode = encryptionMode,
                includeAiChat = includeChat
            )

            // If not forced upload, check if hash unchanged and not dirty
            if (!forceUpload) {
                val lastHash = appPreferences.backupContentHash.first()
                val isDirty = appPreferences.backupIsDirty.first()
                if (!isDirty && lastHash.isNotBlank() && lastHash == snapshot.header.contentHash) {
                    appPreferences.setBackupStatus("IDLE")
                    val lastTime = appPreferences.lastBackupTimestamp.first()
                    val lastSize = appPreferences.lastBackupSizeBytes.first()
                    return@withContext BackupOutcome.Success(
                        expensesCount = snapshot.header.expensesCount,
                        monthsCount = snapshot.header.monthsCount,
                        sizeBytes = lastSize,
                        timestamp = lastTime
                    )
                }
            }

            // 3. Encrypt snapshot payload
            val encryptedBytes = when (encryptionMode) {
                SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND -> {
                    backupCryptoManager.encryptAccountBound(gzipBytes, email)
                }
                SnapshotHeader.ENCRYPTION_MODE_PASSWORD -> {
                    val recoveryKey = appPreferences.backupRecoveryKey.first()
                    if (!recoveryKey.isNullOrBlank()) {
                        backupCryptoManager.encryptWithRecoveryKey(gzipBytes, recoveryKey)
                    } else {
                        backupCryptoManager.encryptAccountBound(gzipBytes, email)
                    }
                }
                SnapshotHeader.ENCRYPTION_MODE_RECOVERY_KEY -> {
                    val recoveryKey = appPreferences.backupRecoveryKey.first() ?: ""
                    backupCryptoManager.encryptWithRecoveryKey(gzipBytes, recoveryKey)
                }
                else -> backupCryptoManager.encryptAccountBound(gzipBytes, email)
            }

            // 4. Save local vault copy as immediate safety net
            try {
                val uid = appPreferences.userUid.first()
                cloudBackupVaultManager.persistBackup(email, uid)
            } catch (e: Exception) {
                Log.w(TAG, "Local vault backup warning: ${e.message}")
            }

            // 5. Upload to Google Drive appDataFolder with safe rotation
            val appProperties = mapOf(
                "generation" to snapshot.header.generation.toString(),
                "contentHash" to snapshot.header.contentHash,
                "expensesCount" to snapshot.header.expensesCount.toString(),
                "monthsCount" to snapshot.header.monthsCount.toString(),
                "schemaVersion" to snapshot.header.schemaVersion.toString(),
                "deviceId" to snapshot.header.deviceId,
                "encryptionMode" to encryptionMode.toString()
            )

            when (val uploadResult = driveRestClient.uploadWithSafeRotation(encryptedBytes, email, appProperties)) {
                is DriveResult.Success -> {
                    val now = System.currentTimeMillis()
                    appPreferences.setBackupSuccess(
                        timestamp = now,
                        sizeBytes = encryptedBytes.size.toLong(),
                        expensesCount = snapshot.header.expensesCount,
                        monthsCount = snapshot.header.monthsCount,
                        generation = snapshot.header.generation,
                        contentHash = snapshot.header.contentHash
                    )
                    appPreferences.setLastSyncTime(now)
                    Log.i(TAG, "Drive backup uploaded successfully: ${encryptedBytes.size} bytes, ${snapshot.header.expensesCount} expenses")
                    BackupOutcome.Success(
                        expensesCount = snapshot.header.expensesCount,
                        monthsCount = snapshot.header.monthsCount,
                        sizeBytes = encryptedBytes.size.toLong(),
                        timestamp = now
                    )
                }
                is DriveResult.Error.Unauthorized -> {
                    Log.w(TAG, "Upload unauthorized: ${uploadResult.message}")
                    appPreferences.setBackupStatus("ERROR", "Google Drive authorization expired")
                    BackupOutcome.NeedsAuthorization(googleDriveAuthManager.getLastRecoverableIntent())
                }
                is DriveResult.Error.QuotaExceeded -> {
                    appPreferences.setBackupStatus("ERROR", "Google Drive storage full")
                    BackupOutcome.Error("Google Drive storage full: ${uploadResult.message}")
                }
                is DriveResult.Error.Network -> {
                    appPreferences.setBackupStatus("ERROR", "Network connection error")
                    BackupOutcome.Error("Network error: ${uploadResult.message}", uploadResult.cause)
                }
                is DriveResult.Error -> {
                    appPreferences.setBackupStatus("ERROR", uploadResult.message)
                    BackupOutcome.Error(uploadResult.message, uploadResult.cause)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "backupNow exception", e)
            appPreferences.setBackupStatus("ERROR", e.message ?: "Backup failed")
            BackupOutcome.Error(e.message ?: "Backup failed", e)
        }
    }
}
