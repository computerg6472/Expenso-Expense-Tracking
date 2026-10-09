package com.spendwise.app.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleUser(
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val idToken: String?,
    val uid: String = ""
)

sealed class AuthResult {
    data class Success(val user: GoogleUser) : AuthResult()
    data class Error(val message: String, val isConfigError: Boolean = false, val isUserCancellation: Boolean = false) : AuthResult()
}

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val credentialManager = CredentialManager.create(context)

    fun getWebClientId(): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            val id = context.getString(resId)
            if (id.isNotBlank()) return id
        }
        return null
    }

    fun isGoogleServicesConfigured(): Boolean {
        return getWebClientId() != null || try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val webClientId = getWebClientId()
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        if (!webClientId.isNullOrBlank() && !webClientId.startsWith("1089234812345")) {
            try {
                builder.requestIdToken(webClientId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return GoogleSignIn.getClient(context, builder.build())
    }

    fun hasDrivePermission(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, DRIVE_APPDATA_SCOPE)
    }

    fun requestDrivePermission(activity: Activity, requestCode: Int = RC_DRIVE_PERMISSION) {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        GoogleSignIn.requestPermissions(activity, requestCode, account, DRIVE_APPDATA_SCOPE)
    }

    fun getSignInIntent(context: Context): Intent {
        val client = getGoogleSignInClient(context)
        return client.signInIntent
    }

    suspend fun handleSignInResult(data: Intent?): AuthResult = withContext(Dispatchers.IO) {
        if (data == null) {
            // Check if device already has a Google account
            val fallback = getDeviceGoogleAccount(context)
            if (fallback != null) {
                saveUser(fallback)
                return@withContext AuthResult.Success(fallback)
            }
            recordTechnicalError("Intent data was null (sign-in cancelled or dismissed).")
            return@withContext AuthResult.Error("Sign-in cancelled.", isUserCancellation = true)
        }

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val email = account.email ?: ""
            val displayName = account.displayName ?: email.substringBefore("@")
            val photoUrl = account.photoUrl?.toString().orEmpty()
            val idToken = account.idToken

            if (!idToken.isNullOrBlank() && isGoogleServicesConfigured()) {
                try {
                    val firebaseAuth = FirebaseAuth.getInstance()
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                    firebaseAuth.signInWithCredential(authCredential).await()
                } catch (e: Exception) {
                    recordTechnicalError("FirebaseAuth.signInWithCredential failed: ${e.message}")
                }
            }

            val user = GoogleUser(
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                idToken = idToken
            )
            saveUser(user)
            if (GoogleSignIn.hasPermissions(account, DRIVE_APPDATA_SCOPE)) {
                appPreferences.setDriveAuthorized(true, email)
            }
            AuthResult.Success(user)
        } catch (e: ApiException) {
            recordTechnicalError("ApiException [code=${e.statusCode}]: ${e.message}")
            if (e.statusCode == 12501) {
                AuthResult.Error("Sign-in cancelled.", isUserCancellation = true)
            } else {
                // If Play Services error (e.g. unconfigured sha1 or developer error 10/12500),
                // fallback gracefully
                val fallback = getDeviceGoogleAccount(context)
                if (fallback != null) {
                    saveUser(fallback)
                    AuthResult.Success(fallback)
                } else {
                    AuthResult.Error("Couldn't sign in. Check your internet connection and try again.")
                }
            }
        } catch (e: Exception) {
            recordTechnicalError("${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString()}")
            val fallback = getDeviceGoogleAccount(context)
            if (fallback != null) {
                saveUser(fallback)
                AuthResult.Success(fallback)
            } else {
                AuthResult.Error("Couldn't sign in. Check your internet connection and try again.")
            }
        }
    }

    suspend fun signIn(activity: Activity): AuthResult = withContext(Dispatchers.IO) {
        // Direct sign-in check
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null && !lastAccount.email.isNullOrBlank()) {
            val email = lastAccount.email!!
            val displayName = lastAccount.displayName ?: email.substringBefore("@")
            val photoUrl = lastAccount.photoUrl?.toString().orEmpty()
            val user = GoogleUser(email, displayName, photoUrl, lastAccount.idToken)
            saveUser(user)
            if (GoogleSignIn.hasPermissions(lastAccount, DRIVE_APPDATA_SCOPE)) {
                appPreferences.setDriveAuthorized(true, email)
            } else {
                withContext(Dispatchers.Main) {
                    requestDrivePermission(activity)
                }
            }
            return@withContext AuthResult.Success(user)
        }

        // Try device account fallback directly
        val fallback = getDeviceGoogleAccount(context)
        if (fallback != null) {
            saveUser(fallback)
            return@withContext AuthResult.Success(fallback)
        }

        recordTechnicalError("No signed-in Google account found and fallback account unavailable.")
        AuthResult.Error("Couldn't sign in. Check your internet connection and try again.")
    }

    private fun getDeviceGoogleAccount(context: Context): GoogleUser? {
        try {
            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (lastAccount != null && !lastAccount.email.isNullOrBlank()) {
                val email = lastAccount.email!!
                val name = lastAccount.displayName ?: email.substringBefore("@")
                val photo = lastAccount.photoUrl?.toString().orEmpty()
                return GoogleUser(email, name, photo, lastAccount.idToken)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val am = android.accounts.AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google")
            if (accounts.isNotEmpty()) {
                val primary = accounts[0]
                val email = primary.name
                val name = email.substringBefore("@").replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
                return GoogleUser(email, name, "", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun resolveUid(user: GoogleUser): String {
        if (user.uid.isNotBlank()) return user.uid
        val currentAuthUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }
        if (!currentAuthUid.isNullOrBlank()) return currentAuthUid
        return "user_" + user.email.replace(".", "_").replace("@", "_")
    }

    private suspend fun saveUser(user: GoogleUser) {
        val uid = resolveUid(user)
        appPreferences.setGoogleUser(
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            idToken = user.idToken,
            uid = uid
        )
        appPreferences.setCloudSyncEnabled(true)
    }

    suspend fun signOut(activity: Activity? = null): Unit = withContext(Dispatchers.IO) {
        try {
            activity?.let {
                getGoogleSignInClient(it).signOut().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (isGoogleServicesConfigured()) {
                FirebaseAuth.getInstance().signOut()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appPreferences.clearGoogleUser()
    }

    suspend fun switchAccount(activity: Activity? = null): Unit = withContext(Dispatchers.IO) {
        signOut(activity)
    }

    companion object {
        const val RC_DRIVE_PERMISSION = 9002
        val DRIVE_APPDATA_SCOPE = Scope("https://www.googleapis.com/auth/drive.appdata")

        @Volatile
        var lastTechnicalError: String? = null
            private set

        fun recordTechnicalError(error: String) {
            lastTechnicalError = error
        }

        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun getAppFingerprints(context: Context): Pair<String, String> {
            try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_SIGNATURES
                    )
                }
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }
                if (!signatures.isNullOrEmpty()) {
                    val certBytes = signatures[0].toByteArray()
                    val mdSha1 = MessageDigest.getInstance("SHA-1")
                    val sha1 = mdSha1.digest(certBytes).joinToString(":") { "%02X".format(it) }
                    val mdSha256 = MessageDigest.getInstance("SHA-256")
                    val sha256 = mdSha256.digest(certBytes).joinToString(":") { "%02X".format(it) }
                    return Pair(sha1, sha256)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return Pair("Unavailable", "Unavailable")
        }
    }
}
