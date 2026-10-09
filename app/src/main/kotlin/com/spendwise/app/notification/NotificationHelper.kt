package com.spendwise.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.spendwise.app.MainActivity
import com.spendwise.app.R

object NotificationHelper {

    const val CHANNEL_REMINDERS = "spendwise_reminders"
    const val CHANNEL_BUDGET = "spendwise_budget"

    const val NOTIFICATION_ID_DAILY = 2001
    const val NOTIFICATION_ID_BUDGET = 2002
    const val NOTIFICATION_ID_TEST = 2003

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Daily Expense Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily evening reminders to record your daily expenses"
                enableVibration(true)
                setShowBadge(true)
            }

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET,
                "Budget & Limit Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when spending reaches budget thresholds"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(reminderChannel, budgetChannel)
            )
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getAddExpensePendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spendwise://add_expense"), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getExpensoLogoBitmap(context: Context): android.graphics.Bitmap? {
        return try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_expenso_wallet)
        } catch (_: Exception) {
            null
        }
    }

    fun showDailyReminder(context: Context) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = getAddExpensePendingIntent(context, NOTIFICATION_ID_DAILY)
        val logo = getExpensoLogoBitmap(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .apply { if (logo != null) setLargeIcon(logo) }
            .setContentTitle("Expenso • Evening Check-in 💰")
            .setContentText("Don't forget to record today's expenses! Tap to log.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Keeping your expenses updated daily ensures accurate monthly budget tracking and zero missing transactions.")
            )
            .setColor(0xFF2563EB.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Record Expense",
                pendingIntent
            )
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_DAILY, notification)
    }

    fun showCreditLimitAlert(context: Context, percentUsed: Int) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = getAddExpensePendingIntent(context, NOTIFICATION_ID_BUDGET)
        val logo = getExpensoLogoBitmap(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .apply { if (logo != null) setLargeIcon(logo) }
            .setContentTitle("Credit Card Limit Alert ($percentUsed% used)")
            .setContentText("You have utilized $percentUsed% of your total credit card limit.")
            .setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_BUDGET, notification)
    }

    fun showTestNotification(context: Context) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = getAddExpensePendingIntent(context, NOTIFICATION_ID_TEST)
        val logo = getExpensoLogoBitmap(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .apply { if (logo != null) setLargeIcon(logo) }
            .setContentTitle("Expenso Notifications Active ✅")
            .setContentText("Daily reminders and balance alerts are working smoothly!")
            .setColor(0xFF2563EB.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "+ Add Expense",
                pendingIntent
            )
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_TEST, notification)
    }
}
