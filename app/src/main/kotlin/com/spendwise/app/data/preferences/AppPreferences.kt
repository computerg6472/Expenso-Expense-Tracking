package com.spendwise.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")
        val KEY_UPI_MONITOR_ENABLED = booleanPreferencesKey("upi_monitor_enabled")
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_FIRST_LAUNCH_SALARY_SET = booleanPreferencesKey("first_launch_salary_set")
        val KEY_THEME = stringPreferencesKey("theme") // SYSTEM, LIGHT, DARK
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_BATTERY_OPT_DISMISSED = booleanPreferencesKey("battery_opt_dismissed")
        val KEY_CREDIT_LIMIT = doublePreferencesKey("credit_limit")
        val KEY_DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val KEY_BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_USER_UID = stringPreferencesKey("user_uid")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val KEY_USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val KEY_GOOGLE_ID_TOKEN = stringPreferencesKey("google_id_token")
        val KEY_CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_GEMINI_AI_ENABLED = booleanPreferencesKey("gemini_ai_enabled")
        val KEY_AI_FLOATING_BUTTON_ENABLED = booleanPreferencesKey("ai_floating_button_enabled")
        val KEY_DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        // Google Drive AppData WhatsApp-style backup keys
        val KEY_DRIVE_BACKUP_ENABLED = booleanPreferencesKey("drive_backup_enabled")
        val KEY_DRIVE_AUTHORIZED = booleanPreferencesKey("drive_authorized")
        val KEY_DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
        val KEY_BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency") // AFTER_CHANGES, DAILY
        val KEY_BACKUP_WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
        val KEY_BACKUP_BATTERY_AWARE = booleanPreferencesKey("backup_battery_aware")
        val KEY_BACKUP_INCLUDE_AI_CHAT = booleanPreferencesKey("backup_include_ai_chat")
        val KEY_BACKUP_ENCRYPTION_MODE = stringPreferencesKey("backup_encryption_mode") // GOOGLE_ACCOUNT, PASSWORD
        val KEY_BACKUP_PASSWORD_HASH = stringPreferencesKey("backup_password_hash")
        val KEY_BACKUP_PASSWORD_SALT = stringPreferencesKey("backup_password_salt")
        val KEY_BACKUP_RECOVERY_KEY = stringPreferencesKey("backup_recovery_key")
        val KEY_LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val KEY_LAST_BACKUP_SIZE_BYTES = longPreferencesKey("last_backup_size_bytes")
        val KEY_LAST_BACKUP_EXPENSES_COUNT = intPreferencesKey("last_backup_expenses_count")
        val KEY_LAST_BACKUP_MONTHS_COUNT = intPreferencesKey("last_backup_months_count")
        val KEY_LAST_BACKUP_STATUS = stringPreferencesKey("last_backup_status")
        val KEY_LAST_BACKUP_ERROR = stringPreferencesKey("last_backup_error")
        val KEY_BACKUP_GENERATION = longPreferencesKey("backup_generation")
        val KEY_BACKUP_CONTENT_HASH = stringPreferencesKey("backup_content_hash")
        val KEY_BACKUP_IS_DIRTY = booleanPreferencesKey("backup_is_dirty")
        val KEY_BACKUP_MODE = stringPreferencesKey("backup_mode")
        const val BACKUP_MODE_AUTO = "AUTO_BACKUP"
        const val BACKUP_MODE_DRIVE = "CUSTOM_DRIVE"
    }


    val dailyReminderEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DAILY_REMINDER_ENABLED] ?: true }

    val budgetAlertsEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BUDGET_ALERTS_ENABLED] ?: true }

    val biometricEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BIOMETRIC_ENABLED] ?: true }

    val creditLimit: Flow<Double> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_CREDIT_LIMIT] ?: 50000.0 }

    val currencySymbol: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_CURRENCY_SYMBOL] ?: "\u20B9" }

    val appLockEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_APP_LOCK_ENABLED] ?: false }

    val lockTimeoutSeconds: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LOCK_TIMEOUT_SECONDS] ?: 30 }

    val upiMonitorEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_UPI_MONITOR_ENABLED] ?: false }

    val overlayEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_OVERLAY_ENABLED] ?: false }

    val onboardingDone: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ONBOARDING_DONE] ?: false }

    val firstLaunchSalarySet: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_FIRST_LAUNCH_SALARY_SET] ?: false }

    val theme: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_THEME] ?: "SYSTEM" }

    val pinHash: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PIN_HASH] }

    val batteryOptDismissed: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BATTERY_OPT_DISMISSED] ?: false }

    suspend fun setCurrencySymbol(symbol: String) {
        dataStore.edit { it[KEY_CURRENCY_SYMBOL] = symbol }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setLockTimeout(seconds: Int) {
        dataStore.edit { it[KEY_LOCK_TIMEOUT_SECONDS] = seconds }
    }

    suspend fun setUpiMonitorEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_UPI_MONITOR_ENABLED] = enabled }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setFirstLaunchSalarySet(set: Boolean) {
        dataStore.edit { it[KEY_FIRST_LAUNCH_SALARY_SET] = set }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setPinHash(hash: String?) {
        dataStore.edit {
            if (hash != null) it[KEY_PIN_HASH] = hash
            else it.remove(KEY_PIN_HASH)
        }
    }

    suspend fun setBatteryOptDismissed(dismissed: Boolean) {
        dataStore.edit { it[KEY_BATTERY_OPT_DISMISSED] = dismissed }
    }

    suspend fun setCreditLimit(limit: Double) {
        dataStore.edit { it[KEY_CREDIT_LIMIT] = limit }
    }

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DAILY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BUDGET_ALERTS_ENABLED] = enabled }
    }

    val userUid: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USER_UID] ?: "" }

    val userEmail: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USER_EMAIL] ?: "" }

    val userDisplayName: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USER_DISPLAY_NAME] ?: "" }

    val userPhotoUrl: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_USER_PHOTO_URL] ?: "" }

    val googleIdToken: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GOOGLE_ID_TOKEN] ?: "" }

    val cloudSyncEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_CLOUD_SYNC_ENABLED] ?: false }

    val lastSyncTime: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_SYNC_TIME] ?: 0L }

    val geminiApiKey: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GEMINI_API_KEY] ?: "" }

    val geminiAiEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GEMINI_AI_ENABLED] ?: true }

    val aiFloatingButtonEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AI_FLOATING_BUTTON_ENABLED] ?: true }

    val developerModeEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEVELOPER_MODE_ENABLED] ?: false }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DEVELOPER_MODE_ENABLED] = enabled }
    }

    suspend fun setUserEmail(email: String) {
        dataStore.edit { it[KEY_USER_EMAIL] = email }
    }

    suspend fun setGoogleUser(email: String, displayName: String, photoUrl: String, idToken: String?, uid: String = "") {
        dataStore.edit {
            it[KEY_USER_EMAIL] = email
            it[KEY_USER_DISPLAY_NAME] = displayName
            it[KEY_USER_PHOTO_URL] = photoUrl
            if (uid.isNotBlank()) it[KEY_USER_UID] = uid
            if (idToken != null) it[KEY_GOOGLE_ID_TOKEN] = idToken else it.remove(KEY_GOOGLE_ID_TOKEN)
        }
    }

    suspend fun clearGoogleUser() {
        dataStore.edit {
            it.remove(KEY_USER_UID)
            it.remove(KEY_USER_EMAIL)
            it.remove(KEY_USER_DISPLAY_NAME)
            it.remove(KEY_USER_PHOTO_URL)
            it.remove(KEY_GOOGLE_ID_TOKEN)
            it[KEY_CLOUD_SYNC_ENABLED] = false
        }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CLOUD_SYNC_ENABLED] = enabled }
    }

    suspend fun setLastSyncTime(epoch: Long) {
        dataStore.edit { it[KEY_LAST_SYNC_TIME] = epoch }
    }

    suspend fun setGeminiApiKey(key: String) {
        dataStore.edit { it[KEY_GEMINI_API_KEY] = key }
    }

    suspend fun setGeminiAiEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_GEMINI_AI_ENABLED] = enabled }
    }

    suspend fun setAiFloatingButtonEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AI_FLOATING_BUTTON_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    val deviceId: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEVICE_ID] ?: "" }

    suspend fun getOrCreateDeviceId(): String {
        val current = dataStore.data.first()[KEY_DEVICE_ID]
        if (!current.isNullOrBlank()) {
            return current
        }
        val generated = java.util.UUID.randomUUID().toString()
        dataStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }

    // Google Drive WhatsApp-style Backup Preferences
    val driveBackupEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DRIVE_BACKUP_ENABLED] ?: true }

    val driveAuthorized: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DRIVE_AUTHORIZED] ?: false }

    val driveAccountEmail: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DRIVE_ACCOUNT_EMAIL] ?: "" }

    val backupFrequency: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_FREQUENCY] ?: "AFTER_CHANGES" }

    val backupWifiOnly: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_WIFI_ONLY] ?: false }

    val backupBatteryAware: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_BATTERY_AWARE] ?: true }

    val backupIncludeAiChat: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_INCLUDE_AI_CHAT] ?: true }

    val backupEncryptionMode: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_ENCRYPTION_MODE] ?: "GOOGLE_ACCOUNT" }

    val backupPasswordHash: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_PASSWORD_HASH] }

    val backupPasswordSalt: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_PASSWORD_SALT] }

    val backupRecoveryKey: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_RECOVERY_KEY] }

    val lastBackupTimestamp: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_BACKUP_TIMESTAMP] ?: 0L }

    val lastBackupSizeBytes: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_BACKUP_SIZE_BYTES] ?: 0L }

    val lastBackupExpensesCount: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_BACKUP_EXPENSES_COUNT] ?: 0 }

    val lastBackupMonthsCount: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_BACKUP_MONTHS_COUNT] ?: 0 }

    val lastBackupStatus: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_BACKUP_STATUS] ?: "NEVER" }

    val lastBackupError: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_BACKUP_ERROR] }

    val backupGeneration: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_GENERATION] ?: 0L }

    val backupContentHash: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_CONTENT_HASH] ?: "" }

    val backupIsDirty: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_IS_DIRTY] ?: false }

    suspend fun setDriveAuthorized(authorized: Boolean, accountEmail: String? = null) {
        dataStore.edit {
            it[KEY_DRIVE_AUTHORIZED] = authorized
            if (accountEmail != null) it[KEY_DRIVE_ACCOUNT_EMAIL] = accountEmail
            if (!authorized) it.remove(KEY_DRIVE_ACCOUNT_EMAIL)
        }
    }

    suspend fun setBackupFrequency(freq: String) {
        dataStore.edit { it[KEY_BACKUP_FREQUENCY] = freq }
    }

    suspend fun setBackupWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[KEY_BACKUP_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setBackupBatteryAware(aware: Boolean) {
        dataStore.edit { it[KEY_BACKUP_BATTERY_AWARE] = aware }
    }

    suspend fun setBackupIncludeAiChat(include: Boolean) {
        dataStore.edit { it[KEY_BACKUP_INCLUDE_AI_CHAT] = include }
    }

    suspend fun setBackupEncryptionMode(mode: String, hash: String? = null, salt: String? = null, recoveryKey: String? = null) {
        dataStore.edit {
            it[KEY_BACKUP_ENCRYPTION_MODE] = mode
            if (hash != null) it[KEY_BACKUP_PASSWORD_HASH] = hash else it.remove(KEY_BACKUP_PASSWORD_HASH)
            if (salt != null) it[KEY_BACKUP_PASSWORD_SALT] = salt else it.remove(KEY_BACKUP_PASSWORD_SALT)
            if (recoveryKey != null) it[KEY_BACKUP_RECOVERY_KEY] = recoveryKey else it.remove(KEY_BACKUP_RECOVERY_KEY)
        }
    }

    suspend fun setBackupSuccess(timestamp: Long, sizeBytes: Long, expensesCount: Int, monthsCount: Int, generation: Long, contentHash: String) {
        dataStore.edit {
            it[KEY_LAST_BACKUP_TIMESTAMP] = timestamp
            it[KEY_LAST_BACKUP_SIZE_BYTES] = sizeBytes
            it[KEY_LAST_BACKUP_EXPENSES_COUNT] = expensesCount
            it[KEY_LAST_BACKUP_MONTHS_COUNT] = monthsCount
            it[KEY_LAST_BACKUP_STATUS] = "SUCCESS"
            it.remove(KEY_LAST_BACKUP_ERROR)
            it[KEY_BACKUP_GENERATION] = generation
            it[KEY_BACKUP_CONTENT_HASH] = contentHash
            it[KEY_BACKUP_IS_DIRTY] = false
        }
    }

    suspend fun setBackupStatus(status: String, error: String? = null) {
        dataStore.edit {
            it[KEY_LAST_BACKUP_STATUS] = status
            if (error != null) it[KEY_LAST_BACKUP_ERROR] = error else it.remove(KEY_LAST_BACKUP_ERROR)
        }
    }

    suspend fun setBackupDirty(isDirty: Boolean) {
        dataStore.edit { it[KEY_BACKUP_IS_DIRTY] = isDirty }
    }

    suspend fun incrementBackupGeneration(): Long {
        var nextGen = 1L
        dataStore.edit {
            val cur = it[KEY_BACKUP_GENERATION] ?: 0L
            nextGen = cur + 1
            it[KEY_BACKUP_GENERATION] = nextGen
        }
        return nextGen
    }

    val backupMode: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKUP_MODE] ?: BACKUP_MODE_AUTO }

    suspend fun setBackupMode(mode: String) {
        dataStore.edit { it[KEY_BACKUP_MODE] = mode }
    }
}

