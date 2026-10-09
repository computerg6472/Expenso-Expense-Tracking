package com.spendwise.app.worker

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {
    private const val TAG_ROLLOVER = "month_rollover"

    private const val TAG_DAILY_REMINDER = "daily_reminder"

    fun scheduleDailyReminder(context: Context, hourOfDay: Int = 20, minute: Int = 0) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val initialDelay = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(TAG_DAILY_REMINDER)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                TAG_DAILY_REMINDER,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_DAILY_REMINDER)
    }

    fun scheduleMonthRollover(context: Context) {
        // Calculate milliseconds until the start of next month
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = cal.timeInMillis - System.currentTimeMillis()

        val request = OneTimeWorkRequestBuilder<MonthRolloverWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().build())
            .addTag(TAG_ROLLOVER)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(TAG_ROLLOVER, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelRollover(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_ROLLOVER)
    }
}
