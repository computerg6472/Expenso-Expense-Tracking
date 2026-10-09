package com.spendwise.app.ui.screen.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.IncomeRepository
import com.spendwise.app.notification.NotificationHelper
import com.spendwise.app.worker.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Calendar
import javax.inject.Inject

import android.app.Activity
import com.spendwise.app.data.auth.AuthResult
import com.spendwise.app.data.auth.GoogleAuthManager
import com.spendwise.app.data.cloud.CloudSyncManager
import com.spendwise.app.data.cloud.GeminiClient
import com.spendwise.app.data.cloud.SyncResult

data class SettingsUiState(
    val salary: String = "",
    val creditLimit: String = "50000",
    val currencySymbol: String = "\u20B9",
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = true,
    val hasPinSet: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    val dailyReminderEnabled: Boolean = true,
    val budgetAlertsEnabled: Boolean = true,
    val upiMonitorEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val themeMode: String = "SYSTEM",
    val isDarkMode: Boolean = false,
    val categories: List<Category> = emptyList(),
    val isSaved: Boolean = false,
    val userEmail: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isSignedIn: Boolean = false,
    val isAuthenticating: Boolean = false,
    val authErrorMessage: String? = null,
    val cloudSyncEnabled: Boolean = false,
    val lastSyncTime: Long = 0L,
    val geminiAiEnabled: Boolean = false,
    val aiFloatingButtonEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    val syncStatusMessage: String? = null,
    val syncStatusLabel: String = "",
    val isDeveloperModeEnabled: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportImportMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val cloudSyncManager: CloudSyncManager,
    private val geminiClient: GeminiClient,
    private val googleAuthManager: GoogleAuthManager,
    private val dataExportImportManager: com.spendwise.app.data.export.DataExportImportManager,
    private val backupTriggerManager: com.spendwise.app.data.backup.BackupTriggerManager,
    private val database: com.spendwise.app.data.local.SpendWiseDatabase
) : ViewModel() {


    private val cal = Calendar.getInstance()
    private val currentYear = cal.get(Calendar.YEAR)
    private val currentMonth = cal.get(Calendar.MONTH) + 1

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var currentPinHash: String? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val income = incomeRepository.getByYearMonth(currentYear, currentMonth)
            val initialLimit = appPreferences.creditLimit.first()
            _uiState.update { state ->
                state.copy(
                    salary = if (income != null && income.salary > 0.0) {
                        if (income.salary % 1.0 == 0.0) income.salary.toLong().toString() else income.salary.toString()
                    } else "",
                    creditLimit = if (initialLimit % 1.0 == 0.0) initialLimit.toLong().toString() else initialLimit.toString()
                )
            }

            val part1Flow = combine(
                appPreferences.currencySymbol,
                appPreferences.appLockEnabled,
                appPreferences.biometricEnabled,
                appPreferences.pinHash,
                appPreferences.theme
            ) { curr, lock, bio, pin, theme ->
                Part1(curr, lock, bio, pin, theme)
            }

            val part2Flow = combine(
                appPreferences.lockTimeoutSeconds,
                appPreferences.dailyReminderEnabled,
                appPreferences.budgetAlertsEnabled
            ) { timeout, daily, budget ->
                Part2(timeout, daily, budget)
            }

            val part3Flow = combine(
                appPreferences.upiMonitorEnabled,
                appPreferences.overlayEnabled,
                appPreferences.creditLimit,
                categoryRepository.observeAll()
            ) { upi, overlay, limit, cats ->
                Part3(upi, overlay, limit, cats)
            }

            val userFlow = combine(
                appPreferences.userEmail,
                appPreferences.userDisplayName,
                appPreferences.userPhotoUrl
            ) { email, name, photo ->
                Triple(email, name, photo)
            }

            val syncAndAiFlow = combine(
                appPreferences.cloudSyncEnabled,
                appPreferences.lastSyncTime,
                appPreferences.geminiAiEnabled,
                appPreferences.aiFloatingButtonEnabled,
                appPreferences.developerModeEnabled
            ) { cloud, syncTime, gemEnabled, floatingEnabled, devMode ->
                SyncAndAi(cloud, syncTime, gemEnabled, floatingEnabled, devMode)
            }

            val part4Flow = combine(userFlow, syncAndAiFlow) { (email, name, photo), sa ->
                Part4(email, name, photo, sa.cloud, sa.syncTime, sa.gemEnabled, sa.floatingEnabled, sa.devMode)
            }

            combine(part1Flow, part2Flow, part3Flow, part4Flow) { p1, p2, p3, p4 ->
                currentPinHash = p1.pin
                _uiState.value.copy(
                    creditLimit = if (_uiState.value.creditLimit.isBlank()) {
                        if (p3.limit % 1.0 == 0.0) p3.limit.toLong().toString() else p3.limit.toString()
                    } else _uiState.value.creditLimit,
                    currencySymbol = p1.curr,
                    appLockEnabled = p1.lock,
                    biometricEnabled = p1.bio,
                    hasPinSet = !p1.pin.isNullOrBlank(),
                    themeMode = p1.theme,
                    isDarkMode = p1.theme == "DARK",
                    lockTimeoutSeconds = p2.timeout,
                    dailyReminderEnabled = p2.daily,
                    budgetAlertsEnabled = p2.budget,
                    upiMonitorEnabled = p3.upi,
                    overlayEnabled = p3.overlay,
                    categories = p3.cats,
                    userEmail = p4.email,
                    displayName = p4.displayName,
                    photoUrl = p4.photoUrl,
                    isSignedIn = p4.email.isNotBlank(),
                    cloudSyncEnabled = p4.cloudSync,
                    lastSyncTime = p4.lastSync,
                    geminiAiEnabled = p4.geminiEnabled,
                    aiFloatingButtonEnabled = p4.aiFloatingEnabled,
                    isDeveloperModeEnabled = p4.developerMode
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }

        // Observe Firebase Cloud sync status label
        viewModelScope.launch {
            combine(
                appPreferences.lastSyncTime,
                appPreferences.lastBackupExpensesCount,
                appPreferences.userEmail
            ) { timestamp, count, email ->
                if (email.isBlank()) {
                    "Not signed in"
                } else if (timestamp > 0) {
                    val dateStr = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                    "✓ Cloud backup: $dateStr" + (if (count > 0) " ($count records)" else "")
                } else {
                    "Cloud connected • Ready to back up"
                }
            }.collect { label ->
                _uiState.update { it.copy(syncStatusLabel = label) }
            }
        }
    }

    private data class Part1(val curr: String, val lock: Boolean, val bio: Boolean, val pin: String?, val theme: String)
    private data class Part2(val timeout: Int, val daily: Boolean, val budget: Boolean)
    private data class Part3(val upi: Boolean, val overlay: Boolean, val limit: Double, val cats: List<Category>)
    private data class Part4(val email: String, val displayName: String, val photoUrl: String, val cloudSync: Boolean, val lastSync: Long, val geminiEnabled: Boolean, val aiFloatingEnabled: Boolean, val developerMode: Boolean)
    private data class SyncAndAi(val cloud: Boolean, val syncTime: Long, val gemEnabled: Boolean, val floatingEnabled: Boolean, val devMode: Boolean)

    fun toggleAiFloatingButton(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAiFloatingButtonEnabled(enabled)
            if (_uiState.value.isSignedIn && _uiState.value.cloudSyncEnabled) {
                cloudSyncManager.performSync()
            }
        }
    }


    fun onSalaryChange(newSalary: String) {
        _uiState.update { it.copy(salary = newSalary.filter { ch -> ch.isDigit() || ch == '.' }) }
    }

    fun onCreditLimitChange(newLimit: String) {
        _uiState.update { it.copy(creditLimit = newLimit.filter { ch -> ch.isDigit() || ch == '.' }) }
    }

    fun saveCreditLimit() {
        val limit = _uiState.value.creditLimit.toDoubleOrNull() ?: 50000.0
        viewModelScope.launch {
            appPreferences.setCreditLimit(limit)
            _uiState.update {
                it.copy(
                    isSaved = true,
                    creditLimit = if (limit % 1.0 == 0.0) limit.toLong().toString() else limit.toString()
                )
            }
        }
    }

    fun saveSalary() {
        val amount = _uiState.value.salary.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            incomeRepository.setSalary(currentYear, currentMonth, amount)
            appPreferences.setFirstLaunchSalarySet(true)
            _uiState.update {
                it.copy(
                    isSaved = true,
                    salary = if (amount > 0.0) {
                        if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
                    } else ""
                )
            }
        }
    }

    fun clearSavedNotification() {
        _uiState.update { it.copy(isSaved = false) }
    }

    fun toggleAppLock(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAppLockEnabled(enabled)
            _uiState.update { it.copy(appLockEnabled = enabled) }
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBiometricEnabled(enabled)
            _uiState.update { it.copy(biometricEnabled = enabled) }
        }
    }

    fun setLockTimeout(seconds: Int) {
        viewModelScope.launch {
            appPreferences.setLockTimeout(seconds)
            _uiState.update { it.copy(lockTimeoutSeconds = seconds) }
        }
    }

    fun verifyPin(pin: String): Boolean {
        if (currentPinHash.isNullOrBlank()) return true
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return hash == currentPinHash
    }

    fun savePin(pin: String) {
        viewModelScope.launch {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(pin.toByteArray())
                .joinToString("") { "%02x".format(it) }
            currentPinHash = hash
            appPreferences.setPinHash(hash)
            _uiState.update { it.copy(hasPinSet = true) }
        }
    }

    fun removePin() {
        viewModelScope.launch {
            currentPinHash = null
            appPreferences.setPinHash(null)
            _uiState.update { it.copy(hasPinSet = false) }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            val mode = if (enabled) "DARK" else "LIGHT"
            appPreferences.setTheme(mode)
            _uiState.update { it.copy(themeMode = mode, isDarkMode = enabled) }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            appPreferences.setTheme(mode)
            _uiState.update { it.copy(themeMode = mode, isDarkMode = (mode == "DARK")) }
        }
    }

    fun setTheme(mode: String) = setThemeMode(mode)

    fun setCurrency(symbol: String) {
        viewModelScope.launch {
            appPreferences.setCurrencySymbol(symbol)
            _uiState.update { it.copy(currencySymbol = symbol) }
        }
    }

    fun toggleDailyReminder(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setDailyReminderEnabled(enabled)
            _uiState.update { it.copy(dailyReminderEnabled = enabled) }
            if (enabled) {
                WorkManagerScheduler.scheduleDailyReminder(context)
            } else {
                WorkManagerScheduler.cancelDailyReminder(context)
            }
        }
    }

    fun toggleBudgetAlerts(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBudgetAlertsEnabled(enabled)
            _uiState.update { it.copy(budgetAlertsEnabled = enabled) }
        }
    }

    fun sendTestNotification(context: Context) {
        NotificationHelper.showTestNotification(context)
    }

    fun toggleUpiMonitor(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setUpiMonitorEnabled(enabled)
            _uiState.update { it.copy(upiMonitorEnabled = enabled) }
        }
    }

    fun toggleOverlay(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setOverlayEnabled(enabled)
            _uiState.update { it.copy(overlayEnabled = enabled) }
        }
    }

    fun toggleCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setCloudSyncEnabled(enabled)
            _uiState.update { it.copy(cloudSyncEnabled = enabled) }
        }
    }

    fun syncNow(activity: Activity? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Backing up to Firebase Cloud...") }
            val result = cloudSyncManager.performSync(activity)
            when (result) {
                is SyncResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncStatusMessage = "✓ Backed up ${result.itemsSynced} records to Firebase Cloud!"
                        )
                    }
                }
                is SyncResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncStatusMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Restoring data and settings from Firebase Cloud...") }
            when (val result = cloudSyncManager.rebuildCacheFromCloud()) {
                is SyncResult.Success -> {
                    loadSettings()
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncStatusMessage = "✓ Successfully restored ${result.itemsSynced} records and all settings from Firebase Cloud!"
                        )
                    }
                }
                is SyncResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncStatusMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun restoreFromDrive() = restoreFromCloud()

    fun toggleGeminiAi(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setGeminiAiEnabled(enabled)
            _uiState.update { it.copy(geminiAiEnabled = enabled) }
        }
    }

    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            categoryRepository.addCategory(Category(name = name, colorHex = colorHex, isDefault = false))
        }
    }

    fun deleteCategory(category: Category) {
        if (category.isDefault) return
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }

    fun getSignInIntent(context: Context): Intent = googleAuthManager.getSignInIntent(context)

    fun handleSignInResult(data: Intent?, activity: Activity? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null) }
            when (val result = googleAuthManager.handleSignInResult(data)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isAuthenticating = false, authErrorMessage = null) }
                    syncNow()
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            authErrorMessage = if (result.isUserCancellation) null else "Couldn't sign in. Check your internet connection and try again."
                        )
                    }
                }
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authErrorMessage = null) }
            when (val result = googleAuthManager.signIn(activity)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isAuthenticating = false, authErrorMessage = null) }
                    syncNow()
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            authErrorMessage = if (result.isUserCancellation) null else "Couldn't sign in. Check your internet connection and try again."
                        )
                    }
                }
            }
        }
    }

    private var versionTapCount = 0
    private var lastTapTimestamp = 0L

    fun onVersionClicked(onMessage: (String) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastTapTimestamp > 2500L) {
            versionTapCount = 0
        }
        lastTapTimestamp = now
        versionTapCount++

        if (_uiState.value.isDeveloperModeEnabled) {
            onMessage("Developer mode is already enabled.")
            return
        }

        when {
            versionTapCount >= 7 -> {
                versionTapCount = 0
                viewModelScope.launch {
                    appPreferences.setDeveloperModeEnabled(true)
                    _uiState.update { it.copy(isDeveloperModeEnabled = true) }
                    onMessage("Developer mode enabled!")
                }
            }
            versionTapCount in 3..6 -> {
                val remaining = 7 - versionTapCount
                onMessage("You are $remaining ${if (remaining == 1) "step" else "steps"} away from developer options.")
            }
        }
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setDeveloperModeEnabled(enabled)
            _uiState.update { it.copy(isDeveloperModeEnabled = enabled) }
        }
    }

    fun toggleDeveloperMode(enabled: Boolean) = setDeveloperModeEnabled(enabled)


    fun signOut(activity: Activity? = null) {
        viewModelScope.launch {
            googleAuthManager.signOut(activity)
            _uiState.update {
                it.copy(
                    isSignedIn = false,
                    userEmail = "",
                    displayName = "",
                    photoUrl = "",
                    cloudSyncEnabled = false,
                    syncStatusMessage = null
                )
            }
        }
    }

    fun switchAccount(activity: Activity? = null) {
        viewModelScope.launch {
            googleAuthManager.switchAccount(activity)
            _uiState.update {
                it.copy(
                    isSignedIn = false,
                    userEmail = "",
                    displayName = "",
                    photoUrl = "",
                    cloudSyncEnabled = false,
                    syncStatusMessage = null
                )
            }
        }
    }

    fun rebuildCacheFromCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "Rebuilding local cache from Google Cloud...") }
            when (val result = cloudSyncManager.rebuildCacheFromCloud()) {
                is SyncResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncStatusMessage = "Cache rebuilt successfully (${result.itemsSynced} records restored)"
                        )
                    }
                }
                is SyncResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncStatusMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
    }

    fun exportAllDataJson(onReadyToShare: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportImportMessage = "Generating complete backup...") }
            when (val result = dataExportImportManager.exportAllDataJson()) {
                is com.spendwise.app.data.export.ExportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportImportMessage = "Export complete: ${result.totalExpenses} expenses across ${result.totalMonths} months"
                        )
                    }
                    onReadyToShare(result.uri)
                }
                is com.spendwise.app.data.export.ExportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportImportMessage = "Export failed: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun exportAllDataCsv(onReadyToShare: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportImportMessage = "Generating CSV spreadsheet...") }
            when (val result = dataExportImportManager.exportAllDataCsv()) {
                is com.spendwise.app.data.export.ExportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportImportMessage = "CSV export complete (${result.totalExpenses} expenses)"
                        )
                    }
                    onReadyToShare(result.uri)
                }
                is com.spendwise.app.data.export.ExportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportImportMessage = "CSV export failed: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun importData(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, exportImportMessage = "Importing backup data...") }
            when (val result = dataExportImportManager.importDataFromUri(uri)) {
                is com.spendwise.app.data.export.ImportResult.Success -> {
                    loadSettings()
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            exportImportMessage = "Successfully imported ${result.expensesCount} expenses across ${result.monthsCount} months!"
                        )
                    }
                }
                is com.spendwise.app.data.export.ImportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            exportImportMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearExportImportMessage() {
        _uiState.update { it.copy(exportImportMessage = null) }
    }
}

