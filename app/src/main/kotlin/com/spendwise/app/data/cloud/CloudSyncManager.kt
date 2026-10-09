package com.spendwise.app.data.cloud

import android.app.Activity
import android.content.Context
import android.util.Log
import com.spendwise.app.data.firebase.FirebaseCloudSyncRepository
import com.spendwise.app.data.firebase.FirebaseSyncResult
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    data class Success(val itemsSynced: Int, val timestamp: Long, val isRestored: Boolean = false) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * CloudSyncManager — triggers seamless Firebase Firestore cloud backups and restore.
 */
@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val firebaseCloudSyncRepository: FirebaseCloudSyncRepository
) {
    suspend fun performSync(activity: Activity? = null): SyncResult = withContext(Dispatchers.IO) {
        val email = appPreferences.userEmail.first()
        if (email.isBlank()) {
            return@withContext SyncResult.Error("Please sign in with Google to enable cloud backup.")
        }

        when (val outcome = firebaseCloudSyncRepository.backupToCloud()) {
            is FirebaseSyncResult.Success -> {
                SyncResult.Success(
                    itemsSynced = outcome.expensesCount,
                    timestamp = outcome.timestamp,
                    isRestored = false
                )
            }
            is FirebaseSyncResult.Error -> {
                SyncResult.Error(outcome.message)
            }
        }
    }

    suspend fun rebuildCacheFromCloud(): SyncResult = withContext(Dispatchers.IO) {
        val email = appPreferences.userEmail.first()
        if (email.isBlank()) {
            return@withContext SyncResult.Error("Please sign in with Google to restore from cloud backup.")
        }

        when (val result = firebaseCloudSyncRepository.restoreFromCloud()) {
            is FirebaseSyncResult.Success -> {
                SyncResult.Success(
                    itemsSynced = result.expensesCount,
                    timestamp = result.timestamp,
                    isRestored = true
                )
            }
            is FirebaseSyncResult.Error -> {
                SyncResult.Error(result.message)
            }
        }
    }

    suspend fun getCloudBackupMeta() = firebaseCloudSyncRepository.getCloudBackupMeta()
}
