package com.spendwise.app.data.drive

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class DriveAuthResult {
    data class Authorized(val accessToken: String, val accountEmail: String) : DriveAuthResult()
    data class NeedsConsent(val pendingIntent: PendingIntent?, val intent: Intent? = null) : DriveAuthResult()
    data class AccountMismatch(val signedInEmail: String, val driveAccountEmail: String) : DriveAuthResult()
    object NotSignedIn : DriveAuthResult()
    data class UnregisteredOnApiConsole(val message: String = "App is not registered on Google Cloud Console.") : DriveAuthResult()
    data class Error(val message: String, val isRecoverable: Boolean = false, val cause: Throwable? = null) : DriveAuthResult()
}

@Singleton
class GoogleDriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "DriveAuthManager"
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        val DRIVE_SCOPE_OBJECT = Scope(DRIVE_APPDATA_SCOPE)
        private const val OAUTH_SCOPE_STRING = "oauth2:$DRIVE_APPDATA_SCOPE"
        const val RC_DRIVE_PERMISSION = 9002
    }

    @Volatile
    private var lastRecoverableIntent: Intent? = null

    fun getLastRecoverableIntent(): Intent? = lastRecoverableIntent

    fun clearLastRecoverableIntent() {
        lastRecoverableIntent = null
    }

    private val authClient: AuthorizationClient by lazy {
        Identity.getAuthorizationClient(context)
    }

    /**
     * Checks if the currently signed-in Google account has authorized Google Drive appDataFolder access.
     */
    suspend fun isAuthorized(): Boolean = withContext(Dispatchers.IO) {
        val email = appPreferences.userEmail.first()
        if (email.isBlank()) return@withContext false
        try {
            val token = getValidAccessToken(forceRefresh = false)
            !token.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Request authorization for the Google Drive appData scope.
     * If consent has not been granted yet, returns [DriveAuthResult.NeedsConsent] containing the PendingIntent.
     */
    suspend fun requestAuthorization(activity: Activity): DriveAuthResult = withContext(Dispatchers.IO) {
        val userEmail = appPreferences.userEmail.first()
        if (userEmail.isBlank()) {
            return@withContext DriveAuthResult.NotSignedIn
        }

        val account = Account(userEmail, "com.google")

        // 1. If we have a pending recoverable intent from Play Services, launch it directly
        val cachedIntent = lastRecoverableIntent
        if (cachedIntent != null) {
            withContext(Dispatchers.Main) {
                try {
                    activity.startActivityForResult(cachedIntent, RC_DRIVE_PERMISSION)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed launching cached intent: ${e.message}")
                }
            }
            return@withContext DriveAuthResult.NeedsConsent(null, cachedIntent)
        }

        // 2. Check GoogleSignIn permissions
        val lastAccount = GoogleSignIn.getLastSignedInAccount(activity)
        if (lastAccount != null && !GoogleSignIn.hasPermissions(lastAccount, DRIVE_SCOPE_OBJECT)) {
            withContext(Dispatchers.Main) {
                GoogleSignIn.requestPermissions(activity, RC_DRIVE_PERMISSION, lastAccount, DRIVE_SCOPE_OBJECT)
            }
            return@withContext DriveAuthResult.NeedsConsent(null)
        }

        // 3. Fallback to GoogleAuthUtil (handles device Google accounts and UserRecoverableAuthException)
        try {
            val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)
            if (!token.isNullOrBlank()) {
                appPreferences.setDriveAuthorized(true, userEmail)
                lastRecoverableIntent = null
                Log.i(TAG, "GoogleAuthUtil successfully acquired bearer token for $userEmail")
                return@withContext DriveAuthResult.Authorized(token, userEmail)
            }
        } catch (e: UserRecoverableAuthException) {
            Log.i(TAG, "GoogleAuthUtil requires user consent: ${e.message}")
            lastRecoverableIntent = e.intent
            withContext(Dispatchers.Main) {
                try {
                    activity.startActivityForResult(e.intent, RC_DRIVE_PERMISSION)
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed launching recoverable intent: ${ex.message}")
                }
            }
            return@withContext DriveAuthResult.NeedsConsent(pendingIntent = null, intent = e.intent)
        } catch (e: GoogleAuthException) {
            Log.e(TAG, "GoogleAuthException during authorization: ${e.message}", e)
            val isUnregistered = e.message?.contains("UnregisteredOnApiConsole", ignoreCase = true) == true
            return@withContext if (isUnregistered) {
                DriveAuthResult.UnregisteredOnApiConsole("UnregisteredOnApiConsole: App SHA-1 is not registered on Google Cloud Console.")
            } else {
                DriveAuthResult.Error(e.message ?: "Authentication failed", cause = e)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during authorization: ${e.message}", e)
            return@withContext DriveAuthResult.Error("Network error. Please check your internet connection.", cause = e)
        }

        DriveAuthResult.Error("Could not obtain Google Drive access token.")
    }

    /**
     * Obtains a valid short-lived bearer access token for Google Drive REST API calls.
     * Silent token retrieval suitable for WorkManager and background coroutines.
     * Verifies that the Drive account email matches the signed-in app email.
     */
    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val userEmail = appPreferences.userEmail.first()
        if (userEmail.isBlank()) {
            Log.w(TAG, "getValidAccessToken: No user signed in")
            return@withContext null
        }

        val driveSavedEmail = appPreferences.driveAccountEmail.first()
        if (driveSavedEmail.isNotBlank() && !driveSavedEmail.equals(userEmail, ignoreCase = true)) {
            Log.e(TAG, "Security check failed: Signed-in email ($userEmail) != Drive email ($driveSavedEmail)")
            appPreferences.setDriveAuthorized(false)
            return@withContext null
        }

        val account = Account(userEmail, "com.google")

        // 1. If forceRefresh, clear token cache in GoogleAuthUtil first
        if (forceRefresh) {
            try {
                val oldToken = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)
                if (!oldToken.isNullOrBlank()) {
                    GoogleAuthUtil.clearToken(context, oldToken)
                }
            } catch (_: Exception) {}
        }

        // 2. Try Identity AuthorizationClient
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(DRIVE_SCOPE_OBJECT))
                .setAccount(account)
                .build()
            val result = authClient.authorize(request).await()
            if (!result.hasResolution() && !result.accessToken.isNullOrBlank()) {
                val token = result.accessToken!!
                appPreferences.setDriveAuthorized(true, userEmail)
                lastRecoverableIntent = null
                return@withContext token
            }
        } catch (e: Exception) {
            Log.d(TAG, "Identity client silent refresh fallback: ${e.message}")
        }

        // 3. Fallback to GoogleAuthUtil getToken
        try {
            val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)
            if (!token.isNullOrBlank()) {
                appPreferences.setDriveAuthorized(true, userEmail)
                lastRecoverableIntent = null
                return@withContext token
            }
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "Token expired and requires user interaction: ${e.message}")
            lastRecoverableIntent = e.intent
            appPreferences.setDriveAuthorized(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed retrieving access token: ${e.message}")
        }

        null
    }

    /**
     * Revokes or disconnects Google Drive authorization.
     */
    suspend fun disconnectDrive() = withContext(Dispatchers.IO) {
        val userEmail = appPreferences.userEmail.first()
        if (userEmail.isNotBlank()) {
            try {
                val account = Account(userEmail, "com.google")
                val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)
                if (!token.isNullOrBlank()) {
                    GoogleAuthUtil.clearToken(context, token)
                }
            } catch (_: Exception) {}
        }
        appPreferences.setDriveAuthorized(false, null)
        Log.i(TAG, "Google Drive disconnected for $userEmail")
    }
}
