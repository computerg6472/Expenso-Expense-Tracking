package com.spendwise.app.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendwise.app.ui.navigation.Screen

/**
 * Main Settings Hub (Master Screen)
 * Displays an organized list of navigation tiles grouped into logical sections.
 * Each tile features a leading icon, primary title, reactive live-state subtitle, and navigation chevron.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            com.spendwise.app.ui.components.TopBanner(
                bottomCornerRadius = 24.dp,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
            ) {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Preferences, backups & intelligence",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

        // Section 1: Account & Cloud
        SettingsSection(title = "Account & Cloud") {
            SettingsTile(
                icon = Icons.Default.CloudSync,
                iconTint = Color(0xFF2563EB),
                title = "Backup & Cloud Sync",
                subtitle = if (uiState.isSignedIn) {
                    "Firebase Cloud • Connected"
                } else {
                    "Firebase Cloud • Sign in to sync"
                },
                badge = if (uiState.isSignedIn) "Active" else null,
                badgeColor = Color(0xFF16A34A),
                showDivider = false,
                onClick = { navController.navigate(Screen.SettingsBackup.route) }
            )
        }

        // Section 2: Finances & Budget
        SettingsSection(title = "Finances & Budget") {
            SettingsTile(
                icon = Icons.Default.AccountBalanceWallet,
                iconTint = Color(0xFF059669),
                title = "Salary & Credit Limit",
                subtitle = "${uiState.currencySymbol}${uiState.salary.ifBlank { "0" }} / mo • Limit: ${uiState.currencySymbol}${uiState.creditLimit}",
                showDivider = true,
                onClick = { navController.navigate(Screen.SettingsIncome.route) }
            )
            SettingsTile(
                icon = Icons.Default.Category,
                iconTint = Color(0xFF7C3AED),
                title = "Expense Categories",
                subtitle = "${uiState.categories.size} categories configured",
                showDivider = false,
                onClick = { navController.navigate(Screen.SettingsCategories.route) }
            )
        }

        // Section 3: Preferences & Display
        SettingsSection(title = "Preferences & Display") {
            SettingsTile(
                icon = Icons.Default.Palette,
                iconTint = Color(0xFFD97706),
                title = "Appearance & Currency",
                subtitle = "${uiState.themeMode.lowercase().replaceFirstChar { it.uppercase() }} Theme • ${uiState.currencySymbol}",
                showDivider = true,
                onClick = { navController.navigate(Screen.SettingsAppearance.route) }
            )
            SettingsTile(
                icon = Icons.Default.Notifications,
                iconTint = Color(0xFFDB2777),
                title = "Notifications & Reminders",
                subtitle = "Daily: ${if (uiState.dailyReminderEnabled) "On" else "Off"} • Alerts: ${if (uiState.budgetAlertsEnabled) "On" else "Off"}",
                showDivider = false,
                onClick = { navController.navigate(Screen.SettingsNotifications.route) }
            )
        }

        // Section 4: Security & Automation
        SettingsSection(title = "Security & Automation") {
            SettingsTile(
                icon = Icons.Default.Security,
                iconTint = Color(0xFFDC2626),
                title = "App Lock & Security",
                subtitle = if (uiState.appLockEnabled) {
                    if (uiState.hasPinSet) "Protected (PIN + Biometrics)" else "Protected (Biometrics)"
                } else "Disabled",
                badge = if (uiState.appLockEnabled) "Protected" else null,
                badgeColor = Color(0xFF16A34A),
                showDivider = true,
                onClick = { navController.navigate(Screen.SettingsSecurity.route) }
            )
            SettingsTile(
                icon = Icons.Default.FlashOn,
                iconTint = Color(0xFFEAB308),
                title = "UPI Detection & Quick Entry",
                subtitle = if (uiState.upiMonitorEnabled) "Auto-detection Active" else "Manual Entry Only",
                showDivider = true,
                onClick = { navController.navigate(Screen.SettingsUpi.route) }
            )
            SettingsTile(
                icon = Icons.Default.AutoAwesome,
                iconTint = Color(0xFF6366F1),
                title = "Gemini AI Assistant",
                subtitle = if (uiState.geminiAiEnabled) {
                    "Active • Floating Button ${if (uiState.aiFloatingButtonEnabled) "On" else "Off"}"
                } else "Disabled",
                showDivider = false,
                onClick = { navController.navigate(Screen.SettingsAi.route) }
            )
        }

        // Section 5: Data & System
        SettingsSection(title = "Data & System") {
            SettingsTile(
                icon = Icons.Default.Storage,
                iconTint = Color(0xFF0891B2),
                title = "Data Export & Import",
                subtitle = "Lossless JSON • Spreadsheet CSV",
                showDivider = true,
                onClick = { navController.navigate(Screen.SettingsData.route) }
            )
            SettingsTile(
                icon = Icons.Default.Info,
                iconTint = Color(0xFF4B5563),
                title = "About & Diagnostics",
                subtitle = "Version 5.0 • Latency & Token Tests",
                showDivider = false,
                onClick = { navController.navigate(Screen.SettingsAbout.route) }
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
}
