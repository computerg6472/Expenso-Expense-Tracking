package com.spendwise.app.data.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendwise.app.data.backup.snapshot.BackupCryptoManager
import com.spendwise.app.data.backup.snapshot.SnapshotHeader
import com.spendwise.app.data.drive.DriveRestClient
import com.spendwise.app.data.drive.DriveResult
import com.spendwise.app.data.drive.GoogleDriveAuthManager
import com.spendwise.app.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val driveBackupRepository: DriveBackupRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DriveBackupWorker"
        const val WORK_NAME_PERIODIC = "spendwise_drive_backup_periodic"
        const val WORK_NAME_DEBOUNCED = "spendwise_drive_backup_debounced"
        const val WORK_NAME_IMMEDIATE = "spendwise_drive_backup_immediate"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting DriveBackupWorker execution...")

        val email = appPreferences.userEmail.first()
        if (email.isBlank()) {
            Log.w(TAG, "No Google user email found in preferences; skipping backup.")
            return Result.success()
        }

        val enabled = appPreferences.cloudSyncEnabled.first() || appPreferences.driveBackupEnabled.first()
        if (!enabled) {
            Log.i(TAG, "Cloud backup is disabled in preferences; skipping.")
            return Result.success()
        }

        return when (val outcome = driveBackupRepository.backupNow(forceUpload = false)) {
            is BackupOutcome.Success -> {
                Log.i(TAG, "DriveBackupWorker succeeded: ${outcome.expensesCount} expenses, ${outcome.sizeBytes} bytes")
                Result.success()
            }
            is BackupOutcome.NeedsAuthorization -> {
                Log.w(TAG, "DriveBackupWorker: Google Drive authorization required")
                Result.retry()
            }
            is BackupOutcome.Error -> {
                Log.e(TAG, "DriveBackupWorker failed: ${outcome.message}", outcome.cause)
                Result.retry()
            }
        }
    }
}
