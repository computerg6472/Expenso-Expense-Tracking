package com.spendwise.app.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spendwise.app.BuildConfig
import com.spendwise.app.MainActivity
import com.spendwise.app.R
import com.spendwise.app.receiver.NotificationActionReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

@AndroidEntryPoint
class UpiMonitorService : Service() {

    companion object {
        // Dedicated channels: Idle background, Active UPI session (Normal/High), and Payment Done prompt (Normal/High)
        const val CHANNEL_ID_SERVICE_IDLE = "expenso_upi_service_idle_v2"
        const val CHANNEL_ID_UPI_ACTIVE = "expenso_upi_active_v2"
        const val CHANNEL_ID_UPI_PROMPT = "expenso_upi_prompt_v2"

        // Exactly TWO fixed notification IDs in total for UPI detection
        const val NOTIFICATION_ID_SERVICE = 1001       // Notification #1: Reused in place for Idle and "<app> app is opened"
        const val NOTIFICATION_ID_PAYMENT_PROMPT = 1002 // Notification #2: High importance prompt "Is payment done?"

        const val ACTION_STOP = "com.spendwise.app.STOP_MONITOR"

        // Watched UPI app package names mapped to clean display names
        val UPI_PACKAGES = mapOf(
            "com.phonepe.app" to "PhonePe",
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            "com.cred.club" to "CRED",
            "com.amazon.mShop.android.shopping" to "Amazon Pay",
            "com.naviapp" to "Navi",
            "money.super.app" to "Super.money",
            "com.mobikwik_new" to "MobiKwik",
            "com.samsung.android.spay" to "Samsung Wallet"
        )

        // Transient system overlays to ignore so they do not falsely break active sessions
        val IGNORED_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.gms",
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            "com.samsung.android.honeyboard",
            "com.sec.android.inputmethod",
            "npci.upi.security.pin",
            "org.npci.upi.security.pinactivity"
        )

        fun startService(context: Context) {
            val intent = Intent(context, UpiMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, UpiMonitorService::class.java))
        }

        fun cancelPaymentDoneNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID_PAYMENT_PROMPT)
            } catch (_: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null

    // Session State Machine: IDLE vs OPENED
    private enum class UpiSessionState {
        IDLE,
        OPENED
    }

    private var sessionState = UpiSessionState.IDLE
    private var activeUpiPackage: String? = null
    private var activeUpiAppName: String = "UPI App"
    private var consecutiveBackgroundTicks = 0
    private var lastSessionClosedTime = 0L

    // Grace delay: 5 consecutive checks (~6 seconds) of non-UPI app foreground to confirm app exit
    private val REQUIRED_BACKGROUND_TICKS_FOR_CLOSE = 5

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Notification #1: Foreground service starts with the Idle state
        startForeground(
            NOTIFICATION_ID_SERVICE,
            buildServiceNotification(
                title = "UPI detection is on",
                text = "Monitoring for UPI payments",
                isUpiOpen = false
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        startMonitoring()
        assertNotificationCount()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                checkUpiApps()
                delay(1200L) // Poll every 1.2 seconds
            }
        }
    }

    private fun checkUpiApps() {
        if (!hasUsageStatsPermission()) return

        val now = System.currentTimeMillis()
        val foregroundPkg = getForegroundPackage(now)

        // Ignore transient system packages (keyboards, biometrics, PIN exchange)
        if (foregroundPkg != null && foregroundPkg in IGNORED_SYSTEM_PACKAGES) {
            return
        }

        // If user directly opened SpendWise / Expenso, return service notification to idle immediately
        if (foregroundPkg != null && foregroundPkg == packageName) {
            if (sessionState == UpiSessionState.OPENED) {
                sessionState = UpiSessionState.IDLE
                activeUpiPackage = null
                consecutiveBackgroundTicks = 0
                cancelPaymentDoneNotification(this)
                updateServiceNotificationInPlace(
                    title = "UPI detection is on",
                    text = "Monitoring for UPI payments",
                    isUpiOpen = false
                )
            }
            return
        }

        val isUpiApp = foregroundPkg != null && foregroundPkg in UPI_PACKAGES.keys

        when (sessionState) {
            UpiSessionState.IDLE -> {
                if (isUpiApp && foregroundPkg != null) {
                    // Transition to OPENED:
                    sessionState = UpiSessionState.OPENED
                    activeUpiPackage = foregroundPkg
                    activeUpiAppName = UPI_PACKAGES[foregroundPkg] ?: "UPI App"
                    consecutiveBackgroundTicks = 0

                    // Cancel any existing Payment Done notification (e.g. from 90s reopen)
                    cancelPaymentDoneNotification(this)

                    // UPDATE Notification #1 in place: "<UPI app display name> app is opened" with normal alert priority
                    updateServiceNotificationInPlace(
                        title = "$activeUpiAppName app is opened",
                        text = "Expenso is active",
                        isUpiOpen = true
                    )
                }
            }

            UpiSessionState.OPENED -> {
                if (isUpiApp && foregroundPkg == activeUpiPackage) {
                    // Still inside the same UPI application
                    consecutiveBackgroundTicks = 0
                } else if (isUpiApp && foregroundPkg != activeUpiPackage) {
                    // Two UPI apps back-to-back: update active app name in place (never two opened notifications)
                    activeUpiPackage = foregroundPkg
                    activeUpiAppName = UPI_PACKAGES[foregroundPkg] ?: "UPI App"
                    consecutiveBackgroundTicks = 0

                    updateServiceNotificationInPlace(
                        title = "$activeUpiAppName app is opened",
                        text = "Expenso is active",
                        isUpiOpen = true
                    )
                } else {
                    // Outside the UPI application: tick background grace counter
                    consecutiveBackgroundTicks++
                    if (consecutiveBackgroundTicks >= REQUIRED_BACKGROUND_TICKS_FOR_CLOSE) {
                        // Session reached CLOSED:
                        val appName = activeUpiAppName
                        sessionState = UpiSessionState.IDLE
                        lastSessionClosedTime = now
                        activeUpiPackage = null
                        consecutiveBackgroundTicks = 0

                        // 1. UPDATE Notification #1 in place back to Idle text
                        updateServiceNotificationInPlace(
                            title = "UPI detection is on",
                            text = "Monitoring for UPI payments",
                            isUpiOpen = false
                        )

                        // 2. POST Notification #2: High-importance "Is payment done?" prompt
                        showPaymentDoneNotification(appName)
                    }
                }
            }
        }
    }

    private fun getForegroundPackage(now: Long): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null

        try {
            val events = usm.queryEvents(now - 15_000L, now)
            val event = UsageEvents.Event()
            var lastResumedPkg: String? = null
            var lastEventTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (event.timeStamp >= lastEventTime) {
                        val pkg = event.packageName
                        if (pkg !in IGNORED_SYSTEM_PACKAGES) {
                            lastEventTime = event.timeStamp
                            lastResumedPkg = pkg
                        }
                    }
                }
            }
            if (lastResumedPkg != null) return lastResumedPkg
        } catch (_: Exception) {}

        return try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 25_000L,
                now
            )
            stats
                ?.filter { it.packageName !in IGNORED_SYSTEM_PACKAGES && it.lastTimeUsed > (now - 25_000L) }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                System.currentTimeMillis() - 60_000,
                System.currentTimeMillis()
            )
            stats != null && stats.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // Notification #1: Build / Update in place (same ID 1001)
    private fun buildServiceNotification(
        title: String,
        text: String,
        isUpiOpen: Boolean = false
    ): Notification {
        val logo = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_expenso_wallet)
        } catch (_: Exception) { null }

        val channelId = if (isUpiOpen) CHANNEL_ID_UPI_ACTIVE else CHANNEL_ID_SERVICE_IDLE
        val priority = if (isUpiOpen) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
        val addExpensePendingIntent = getAddExpensePendingIntent(NOTIFICATION_ID_SERVICE)

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .apply { if (logo != null) setLargeIcon(logo) }
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFF2563EB.toInt())
            .setPriority(priority)
            .setOngoing(true)
            .setOnlyAlertOnce(!isUpiOpen) // Alert normally when UPI app opens
            .setShowWhen(isUpiOpen)
            .setContentIntent(addExpensePendingIntent)
            .apply {
                if (isUpiOpen) {
                    addAction(
                        R.drawable.ic_notification,
                        "+ Log Expense",
                        addExpensePendingIntent
                    )
                }
            }
            .build()
    }

    private fun updateServiceNotificationInPlace(
        title: String,
        text: String,
        isUpiOpen: Boolean = false
    ) {
        val notification = buildServiceNotification(title, text, isUpiOpen)
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_SERVICE, notification)
        } catch (_: SecurityException) {}
        assertNotificationCount()
    }

    // Notification #2: High importance prompt "Is payment done?" (ID 1002 - Normal alert)
    private fun showPaymentDoneNotification(appName: String) {
        val addExpensePendingIntent = getAddExpensePendingIntent(NOTIFICATION_ID_PAYMENT_PROMPT)
        val dismissPendingIntent = getDismissPendingIntent(NOTIFICATION_ID_PAYMENT_PROMPT)
        val logo = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_expenso_wallet)
        } catch (_: Exception) { null }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_UPI_PROMPT)
            .setSmallIcon(R.drawable.ic_notification)
            .apply { if (logo != null) setLargeIcon(logo) }
            .setContentTitle("Is payment done?")
            .setContentText("Log your expense in Expenso")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Finished paying on $appName? Tap below to record your expense.")
            )
            .setColor(0xFF2563EB.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60 * 1000L) // Auto-cancel after 10 minutes
            .setContentIntent(addExpensePendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Log Expense",
                addExpensePendingIntent
            )
            .addAction(
                0,
                "Dismiss",
                dismissPendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_PAYMENT_PROMPT, notification)
        } catch (_: SecurityException) {}
        assertNotificationCount()
    }

    private fun getAddExpensePendingIntent(requestCode: Int): PendingIntent {
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("spendwise://add_expense")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            setClass(this@UpiMonitorService, MainActivity::class.java)
        }

        return PendingIntent.getActivity(
            this,
            requestCode,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getDismissPendingIntent(notificationId: Int): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return

        // Remove legacy channels so settings don't persist silent state
        try {
            notificationManager.deleteNotificationChannel("spendwise_service")
            notificationManager.deleteNotificationChannel("spendwise_upi_prompt")
        } catch (_: Exception) {}

        // Channel #1: Idle service background indicator
        val idleChannel = NotificationChannel(
            CHANNEL_ID_SERVICE_IDLE,
            "Expenso UPI Detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background service indicator for UPI tracking"
            setShowBadge(false)
            enableVibration(false)
        }

        // Channel #2: UPI App is Opened (NORMAL / HIGH alert importance)
        val upiActiveChannel = NotificationChannel(
            CHANNEL_ID_UPI_ACTIVE,
            "UPI App Opened Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Normal alert when a UPI payment app is opened"
            enableVibration(true)
            setShowBadge(true)
        }

        // Channel #3: Is payment done? prompt (NORMAL / HIGH alert importance)
        val upiPromptChannel = NotificationChannel(
            CHANNEL_ID_UPI_PROMPT,
            "UPI Payment Prompt",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Normal prompt asking if payment is done after closing a UPI app"
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(idleChannel, upiActiveChannel, upiPromptChannel)
        )
    }

    // In-app assertion for debug builds: guarantees max 2 notifications, and prompt never on screen while UPI open
    private fun assertNotificationCount() {
        if (!BuildConfig.DEBUG) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val active = manager.activeNotifications.filter {
                it.id == NOTIFICATION_ID_SERVICE || it.id == NOTIFICATION_ID_PAYMENT_PROMPT
            }

            check(active.size <= 2) {
                "Assertion Error: Found ${active.size} active UPI notifications, maximum allowed is 2!"
            }

            if (sessionState == UpiSessionState.OPENED) {
                check(active.none { it.id == NOTIFICATION_ID_PAYMENT_PROMPT }) {
                    "Assertion Error: Payment prompt (ID $NOTIFICATION_ID_PAYMENT_PROMPT) must not exist while UPI app is opened!"
                }
            }
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cancelPaymentDoneNotification(this)
    }
}
