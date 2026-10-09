package com.spendwise.app.data.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.SecureRandom

private val Context.secureDataStore by preferencesDataStore(name = "secure_key_store")

object DatabaseKeyProvider {
    private val DB_KEY = stringPreferencesKey("db_key_encrypted")
    private const val BACKUP_PREF_NAME = "spendwise_vault_key"
    private const val BACKUP_KEY_NAME = "backup_db_key_b64"
    private const val KEY_LENGTH_BYTES = 32 // 256-bit key

    suspend fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val backupPrefs = context.getSharedPreferences(BACKUP_PREF_NAME, Context.MODE_PRIVATE)
        val backupKeyB64 = backupPrefs.getString(BACKUP_KEY_NAME, null)

        val prefs = context.secureDataStore.data.first()
        val existingEncrypted = prefs[DB_KEY]

        if (existingEncrypted != null) {
            try {
                val key = KeystoreManager.decryptFromBase64(existingEncrypted)
                // Keep backup SharedPreferences synchronized for Auto Backup transfer (synchronous commit)
                if (backupKeyB64 == null) {
                    backupPrefs.edit().putString(
                        BACKUP_KEY_NAME,
                        android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
                    ).commit()
                }
                return key
            } catch (e: Exception) {
                android.util.Log.w("DatabaseKeyProvider", "Local keystore decrypt failed (device migrated/restored): ${e.message}")
            }
        }

        // Restored from Android 25MB Auto Backup onto a second device or fresh install
        if (backupKeyB64 != null) {
            try {
                val restoredKey = android.util.Base64.decode(backupKeyB64, android.util.Base64.NO_WRAP)
                if (restoredKey.size == KEY_LENGTH_BYTES) {
                    // Re-bind to this device's AndroidKeyStore for ongoing local security
                    try {
                        val newEncrypted = KeystoreManager.encryptToBase64(restoredKey)
                        context.secureDataStore.edit { it[DB_KEY] = newEncrypted }
                    } catch (_: Exception) {}
                    return restoredKey
                }
            } catch (e: Exception) {
                android.util.Log.w("DatabaseKeyProvider", "Failed to parse backupKeyB64: ${e.message}")
            }
        }

        // Fresh installation initialization
        val newKey = generateRandomKey()
        try {
            val encrypted = KeystoreManager.encryptToBase64(newKey)
            context.secureDataStore.edit { it[DB_KEY] = encrypted }
        } catch (_: Exception) {}
        backupPrefs.edit().putString(
            BACKUP_KEY_NAME,
            android.util.Base64.encodeToString(newKey, android.util.Base64.NO_WRAP)
        ).commit()
        return newKey
    }

    suspend fun syncBackupVaultKey(context: Context) {
        val backupPrefs = context.getSharedPreferences(BACKUP_PREF_NAME, Context.MODE_PRIVATE)
        val prefs = context.secureDataStore.data.first()
        val existingEncrypted = prefs[DB_KEY]
        if (existingEncrypted != null) {
            try {
                val key = KeystoreManager.decryptFromBase64(existingEncrypted)
                backupPrefs.edit().putString(
                    BACKUP_KEY_NAME,
                    android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
                ).commit()
                android.util.Log.i("DatabaseKeyProvider", "Backup vault key synchronized synchronously")
                return
            } catch (_: Exception) {}
        }
        val existingBackupKey = backupPrefs.getString(BACKUP_KEY_NAME, null)
        if (existingBackupKey == null) {
            val newKey = generateRandomKey()
            backupPrefs.edit().putString(
                BACKUP_KEY_NAME,
                android.util.Base64.encodeToString(newKey, android.util.Base64.NO_WRAP)
            ).commit()
            android.util.Log.i("DatabaseKeyProvider", "Fresh backup vault key committed for backup")
        }
    }

    private fun generateRandomKey(): ByteArray {
        val key = ByteArray(KEY_LENGTH_BYTES)
        SecureRandom().nextBytes(key)
        return key
    }
}
