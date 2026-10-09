package com.spendwise.app.data.backup

import android.content.Context
import android.util.Log
import androidx.work.*
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface BackupTriggerManager {
    fun markDataChanged()
    fun scheduleDebouncedBackup()
    fun scheduleImmediateBackup()
    fun schedulePeriodicBackup()
    fun cancelPeriodicBackup()
    fun cancelAllDriveWork()
}

@Singleton
class BackupTriggerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) : BackupTriggerManager {

    companion object {
        private const val TAG = "BackupTrigger"
        const val WORK_NAME_DEBOUNCED = "expenso_backup_debounced"
        const val WORK_NAME_IMMEDIATE = "expenso_backup_immediate"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun markDataChanged() {
        scope.launch {
            try {
                appPreferences.setBackupDirty(true)
                scheduleDebouncedBackup()
            } catch (e: Exception) {
                Log.w(TAG, "markDataChanged error: ${e.message}")
            }
        }
    }

    override fun scheduleDebouncedBackup() {
        scope.launch {
            try {
                val email = appPreferences.userEmail.first()
                if (email.isBlank()) return@launch
                val enabled = appPreferences.cloudSyncEnabled.first() || appPreferences.driveBackupEnabled.first()
                if (!enabled) return@launch

                val wifiOnly = appPreferences.backupWifiOnly.first()
                val batteryAware = appPreferences.backupBatteryAware.first()

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .apply {
                        if (batteryAware) setRequiresBatteryNotLow(true)
                    }
                    .build()

                // Debounce ~2 minutes after last change
                val workRequest = OneTimeWorkRequestBuilder<DriveBackupWorker>() // will point to DriveBackupWorker in Step 4
                    .setConstraints(constraints)
                    .setInitialDelay(2, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag("drive_backup")
                    .build()

                // Enqueue unique work with REPLACE to reset the 2-minute debounce timer on every new edit
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_DEBOUNCED,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                Log.d(TAG, "Scheduled debounced backup in 2 minutes")
            } catch (e: Exception) {
                Log.w(TAG, "scheduleDebouncedBackup error: ${e.message}")
            }
        }
    }

    override fun scheduleImmediateBackup() {
        scope.launch {
            try {
                val wifiOnly = appPreferences.backupWifiOnly.first()
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .addTag("drive_backup_immediate")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_IMMEDIATE,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                Log.d(TAG, "Scheduled immediate backup")
            } catch (e: Exception) {
                Log.w(TAG, "scheduleImmediateBackup error: ${e.message}")
            }
        }
    }

    override fun schedulePeriodicBackup() {
        scope.launch {
            try {
                val mode = appPreferences.backupMode.first()
                if (mode != AppPreferences.BACKUP_MODE_DRIVE) {
                    Log.d(TAG, "schedulePeriodicBackup: mode is $mode, skipping Drive worker")
                    cancelAllDriveWork()
                    return@launch
                }

                val enabled = appPreferences.driveBackupEnabled.first()
                if (!enabled) return@launch

                val frequency = appPreferences.backupFrequency.first()
                if (frequency.equals("OFF", ignoreCase = true) || frequency.equals("MANUAL", ignoreCase = true)) {
                    cancelPeriodicBackup()
                    return@launch
                }

                val repeatDays = if (frequency.equals("WEEKLY", ignoreCase = true)) 7L else 1L
                val wifiOnly = appPreferences.backupWifiOnly.first()
                val batteryAware = appPreferences.backupBatteryAware.first()

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .apply {
                        if (batteryAware) setRequiresBatteryNotLow(true)
                    }
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(repeatDays, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .addTag("drive_backup_periodic")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "spendwise_drive_backup_periodic",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicRequest
                )
                Log.d(TAG, "Scheduled periodic backup every $repeatDays days")
            } catch (e: Exception) {
                Log.w(TAG, "schedulePeriodicBackup error: ${e.message}")
            }
        }
    }

    override fun cancelPeriodicBackup() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("spendwise_drive_backup_periodic")
            Log.d(TAG, "Cancelled periodic backup")
        } catch (e: Exception) {
            Log.w(TAG, "cancelPeriodicBackup error: ${e.message}")
        }
    }

    override fun cancelAllDriveWork() {
        try {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork("spendwise_drive_backup_periodic")
            wm.cancelUniqueWork(WORK_NAME_DEBOUNCED)
            wm.cancelUniqueWork(WORK_NAME_IMMEDIATE)
            Log.d(TAG, "Cancelled all Drive WorkManager jobs")
        } catch (e: Exception) {
            Log.w(TAG, "cancelAllDriveWork error: ${e.message}")
        }
    }
}
