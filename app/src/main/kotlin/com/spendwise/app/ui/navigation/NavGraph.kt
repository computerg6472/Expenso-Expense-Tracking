package com.spendwise.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.spendwise.app.ui.screen.addexpense.AddExpenseScreen
import com.spendwise.app.ui.screen.dashboard.DashboardScreen
import com.spendwise.app.ui.screen.history.HistoryScreen
import com.spendwise.app.ui.screen.reports.ReportsScreen
import com.spendwise.app.ui.screen.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object AddExpense : Screen("add_expense?expenseId={expenseId}") {
        fun route(expenseId: String? = null) = if (expenseId != null) "add_expense?expenseId=$expenseId" else "add_expense"
    }
    object History : Screen("history")
    object Forecast : Screen("forecast")
    object Reports : Screen("reports")
    object Settings : Screen("settings")
    object SettingsBackup : Screen("settings_backup")
    object SettingsIncome : Screen("settings_income")
    object SettingsCategories : Screen("settings_categories")
    object SettingsAppearance : Screen("settings_appearance")
    object SettingsNotifications : Screen("settings_notifications")
    object SettingsSecurity : Screen("settings_security")
    object SettingsUpi : Screen("settings_upi")
    object SettingsAi : Screen("settings_ai")
    object SettingsData : Screen("settings_data")
    object SettingsAbout : Screen("settings_about")
    object Onboarding : Screen("onboarding")
    object SignInDiagnostics : Screen("sign_in_diagnostics")
    object SyncDiagnostics : Screen("sync_diagnostics")
    object SetSalary : Screen("set_salary")
}

@Composable
fun SpendWiseNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route,
    onOpenAiChat: (isVoice: Boolean, query: String?) -> Unit = { _, _ -> }
) {
    val bottomTabIndexes = mapOf(
        Screen.Dashboard.route to 0,
        Screen.History.route to 1,
        Screen.Forecast.route to 2,
        Screen.Reports.route to 3,
        Screen.Settings.route to 4
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            val targetRoute = targetState.destination.route
            val initialRoute = initialState.destination.route
            val targetIdx = bottomTabIndexes[targetRoute]
            val initialIdx = bottomTabIndexes[initialRoute]

            if (targetIdx != null && initialIdx != null) {
                // Short fade + slight slide (190ms, ease-out) animating ONLY transform & opacity (GPU-friendly)
                val slideDir = if (targetIdx >= initialIdx)
                    AnimatedContentTransitionScope.SlideDirection.Start
                else
                    AnimatedContentTransitionScope.SlideDirection.End

                slideIntoContainer(
                    towards = slideDir,
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    initialOffset = { (it * 0.08f).toInt() }
                ) + fadeIn(animationSpec = tween(170, easing = FastOutSlowInEasing))
            } else if (targetRoute == Screen.AddExpense.route) {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200))
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
            }
        },
        exitTransition = {
            val targetRoute = targetState.destination.route
            val initialRoute = initialState.destination.route
            val targetIdx = bottomTabIndexes[targetRoute]
            val initialIdx = bottomTabIndexes[initialRoute]

            if (targetIdx != null && initialIdx != null) {
                val slideDir = if (targetIdx >= initialIdx)
                    AnimatedContentTransitionScope.SlideDirection.Start
                else
                    AnimatedContentTransitionScope.SlideDirection.End

                slideOutOfContainer(
                    towards = slideDir,
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                    targetOffset = { (-it * 0.08f).toInt() }
                ) + fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
            } else {
                fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
            }
        },
        popEnterTransition = {
            val targetRoute = targetState.destination.route
            val initialRoute = initialState.destination.route
            val targetIdx = bottomTabIndexes[targetRoute]
            val initialIdx = bottomTabIndexes[initialRoute]

            if (targetIdx != null && initialIdx != null) {
                val slideDir = if (targetIdx >= initialIdx)
                    AnimatedContentTransitionScope.SlideDirection.Start
                else
                    AnimatedContentTransitionScope.SlideDirection.End

                slideIntoContainer(
                    towards = slideDir,
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    initialOffset = { (it * 0.08f).toInt() }
                ) + fadeIn(animationSpec = tween(170, easing = FastOutSlowInEasing))
            } else {
                fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
            }
        },
        popExitTransition = {
            val initialRoute = initialState.destination.route
            if (initialRoute == Screen.AddExpense.route) {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(180))
            } else {
                fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
            }
        }
    ) {

        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController, onOpenAiChat = onOpenAiChat)
        }
        composable(
            route = Screen.AddExpense.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "expenso://add_expense" },
                navDeepLink { uriPattern = "spendwise://add_expense" }
            )
        ) {
            val expenseId = it.arguments?.getString("expenseId")
            AddExpenseScreen(navController = navController, expenseId = expenseId)
        }
        composable(
            route = Screen.History.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "expenso://history" },
                navDeepLink { uriPattern = "spendwise://history" }
            )
        ) {
            HistoryScreen(navController = navController)
        }
        composable(Screen.Forecast.route) {
            com.spendwise.app.ui.screen.forecast.AiForecastScreen(navController = navController)
        }
        composable(Screen.Reports.route) {
            ReportsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.SettingsBackup.route) {
            com.spendwise.app.ui.screen.settings.BackupSettingsScreen(navController = navController)
        }
        composable(Screen.SettingsIncome.route) {
            com.spendwise.app.ui.screen.settings.IncomeSettingsScreen(navController = navController)
        }
        composable(Screen.SettingsCategories.route) {
            com.spendwise.app.ui.screen.settings.CategoriesSettingsScreen(navController = navController)
        }
        composable(Screen.SettingsAppearance.route) {
            com.spendwise.app.ui.screen.settings.AppearanceSettingsScreen(navController = navController)
        }
        composable(Screen.SettingsNotifications.route) {
            com.spendwise.app.ui.screen.settings.NotificationSettingsScreen(navController = navController)
        }
        composable(Screen.SettingsSecurity.route) {
            com.spendwise.app.ui.screen.settings.SecuritySettingsScreen(navController = navController)
        }
        composable(Screen.SettingsUpi.route) {
            com.spendwise.app.ui.screen.settings.UpiSettingsScreen(navController = navController)
        }
        composable(Screen.SettingsAi.route) {
            com.spendwise.app.ui.screen.settings.AiSettingsScreen(navController = navController)
        }
        composable(Screen.SettingsData.route) {
            com.spendwise.app.ui.screen.settings.DataManagementScreen(navController = navController)
        }
        composable(Screen.SettingsAbout.route) {
            com.spendwise.app.ui.screen.settings.AboutSettingsScreen(navController = navController)
        }
        composable(Screen.Onboarding.route) {
            com.spendwise.app.ui.screen.onboarding.OnboardingScreen(navController = navController)
        }
        composable(Screen.SignInDiagnostics.route) {
            com.spendwise.app.ui.screen.settings.SignInDiagnosticsScreen(navController = navController)
        }
        composable(Screen.SyncDiagnostics.route) {
            com.spendwise.app.ui.screen.settings.SyncDiagnosticsScreen(navController = navController)
        }
    }
}
