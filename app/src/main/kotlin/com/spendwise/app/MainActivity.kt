package com.spendwise.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.ui.SpendWiseApp
import com.spendwise.app.ui.lock.AppLockViewModel
import com.spendwise.app.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var database: com.spendwise.app.data.local.SpendWiseDatabase

    private val lockViewModel: AppLockViewModel by viewModels()
    private val startupViewModel: com.spendwise.app.ui.screen.startup.StartupViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the native launch screen active until auth is verified - zero blank/white flash
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (startupViewModel.startupState.value !is com.spendwise.app.ui.screen.startup.StartupState.CheckingAuth) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )

        // Allow screenshots inside the application
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Android 15 Edge-to-Edge enforcement
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS runtime permission for Android 13+
        requestNotificationPermission()

        setContent {
            val themeMode by appPreferences.theme.collectAsState(initial = "SYSTEM")
            val startupState by startupViewModel.startupState.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }

            val driveAuthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                startupViewModel.onDriveAuthResult(result.resultCode, result.data)
            }

            SpendWiseTheme(darkTheme = isDark) {
                AnimatedContent(
                    targetState = startupState,
                    transitionSpec = {
                        fadeIn(
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ) togetherWith fadeOut(
                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                        )
                    },
                    label = "startup_fade_transition"
                ) { state ->
                    when (state) {
                        is com.spendwise.app.ui.screen.startup.StartupState.CheckingAuth -> {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier.fillMaxSize()
                            )
                        }
                        is com.spendwise.app.ui.screen.startup.StartupState.MandatorySignIn -> {
                            com.spendwise.app.ui.screen.startup.MandatorySignInScreen(viewModel = startupViewModel)
                        }
                        is com.spendwise.app.ui.screen.startup.StartupState.RestoreInProgress -> {
                            val step by startupViewModel.restoreStep.collectAsState()
                            com.spendwise.app.ui.screen.startup.LoadingFinancesScreen(
                                restoreStep = step,
                                canSkip = state.canSkip,
                                onSkip = { startupViewModel.skipToHome() }
                            )
                        }
                        is com.spendwise.app.ui.screen.startup.StartupState.RestoreError -> {
                            com.spendwise.app.ui.screen.startup.RestoreErrorScreen(
                                state = state,
                                onRetry = {
                                    if (state.errorTag == "PERMISSION_DENIED") {
                                        try {
                                            val authIntent = startupViewModel.getDriveAuthIntent()
                                            driveAuthLauncher.launch(authIntent)
                                        } catch (e: Exception) {
                                            startupViewModel.retryRestore(this@MainActivity)
                                        }
                                    } else {
                                        startupViewModel.retryRestore(this@MainActivity)
                                    }
                                },
                                onUseCachedData = { startupViewModel.useCachedData() },
                                onSkipToHome = { startupViewModel.skipToHome() },
                                onSignOut = { startupViewModel.signOut(this@MainActivity) }
                            )
                        }
                        is com.spendwise.app.ui.screen.startup.StartupState.OnboardingRequired -> {
                            SpendWiseApp(
                                lockViewModel = lockViewModel,
                                appPreferences = appPreferences,
                                deepLinkIntent = intent,
                                startDestination = com.spendwise.app.ui.navigation.Screen.Onboarding.route
                            )
                        }
                        is com.spendwise.app.ui.screen.startup.StartupState.Ready -> {
                            SpendWiseApp(
                                lockViewModel = lockViewModel,
                                appPreferences = appPreferences,
                                deepLinkIntent = intent,
                                startDestination = com.spendwise.app.ui.navigation.Screen.Dashboard.route,
                                isOfflineNotice = state.isOffline,
                                historyProgressPercent = state.historyProgressPercent
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lockViewModel.handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        lockViewModel.onAppForeground()
    }

    override fun onPause() {
        super.onPause()
        lockViewModel.onAppBackground()
        try {
            database.checkpointWal()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "checkpointWal error: ${e.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            database.checkpointWal()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "onStop checkpointWal error: ${e.message}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.spendwise.app.data.auth.GoogleAuthManager.RC_DRIVE_PERMISSION) {
            startupViewModel.onDriveAuthResult(resultCode, data)
        }
    }
}
