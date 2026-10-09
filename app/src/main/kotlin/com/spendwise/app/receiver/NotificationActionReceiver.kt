package com.spendwise.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS = "com.spendwise.app.ACTION_DISMISS_NOTIFICATION"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notificationId != -1) {
                try {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                } catch (_: Exception) {}
            }
        }
    }
}
