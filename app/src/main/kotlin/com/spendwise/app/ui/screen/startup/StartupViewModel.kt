package com.spendwise.app.ui.screen.startup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.spendwise.app.data.auth.AuthResult
import com.spendwise.app.data.auth.GoogleAuthManager
import com.spendwise.app.data.backup.DriveBackupRepository
import com.spendwise.app.data.backup.RestoreResult
import com.spendwise.app.data.backup.RestoreStep
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "StartupVM"

// ─────────────────────────────────────────────────────────────────────────────
// Startup state (drives UI)
// ─────────────────────────────────────────────────────────────────────────────

sealed class StartupState {
    object CheckingAuth : StartupState()
    object MandatorySignIn : StartupState()

    /** Step-by-step cloud restore in progress */
    data class RestoreInProgress(
        val step: RestoreStep,
        val canSkip: Boolean = false   // true after 1.5 s so user is never trapped
    ) : StartupState()

    /** Home is now usable; historical load may still be happening in background */
    data class Ready(
        val isOffline: Boolean = false,
        val historyProgressPercent: Int = 100 // 0-100 for history banner
    ) : StartupState()

    /** Something went wrong; user can retry, use cache, or sign out */
    data class RestoreError(
        val title: String,
        val detail: String,
        val canUseCachedData: Boolean = false,
        val canRetry: Boolean = true,
        val errorTag: String = "" // for diagnostics copy
    ) : StartupState()

    object OnboardingRequired : StartupState()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class StartupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleAuthManager: GoogleAuthManager,
    private val googleDriveAuthManager: com.spendwise.app.data.drive.GoogleDriveAuthManager,
    private val driveBackupRepository: DriveBackupRepository,
    private val appPreferences: AppPreferences,
    private val categoryRepository: com.spendwise.app.domain.repository.CategoryRepository,
    private val expenseDao: com.spendwise.app.data.local.dao.ExpenseDao
) : ViewModel() {

    private val _startupState = MutableStateFlow<StartupState>(StartupState.CheckingAuth)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    // Expose restore step for the loading screen
    val restoreStep: StateFlow<RestoreStep> = driveBackupRepository.restoreStep

    init {
        checkStartupFlow()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // checkStartupFlow
    // ─────────────────────────────────────────────────────────────────────────

    fun checkStartupFlow() {
        viewModelScope.launch {
            _startupState.value = StartupState.CheckingAuth

            // 1. Android Auto Backup Restore check:
            // If the local database already has data (restored seamlessly by Android Auto Backup
            // or previously existing on device), load directly into Ready state without forcing OAuth login screen.
            val hasLocalExpenses = withContext(Dispatchers.IO) {
                try { expenseDao.getAllIncludingDeleted().any { !it.isDeleted } } catch (_: Exception) { false }
            }

            val userEmail = appPreferences.userEmail.first()
            val userUid   = appPreferences.userUid.first()

            if (hasLocalExpenses) {
                Log.i(TAG, "checkStartupFlow: Local database has records (restored by Auto Backup or existing). Launching directly to Ready.")
                withContext(Dispatchers.IO) { appPreferences.setOnboardingDone(true) }
                _startupState.value = StartupState.Ready(isOffline = false)

                // If user is already authenticated, start background sync
                val firebaseUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                val effectiveUid = when {
                    userUid.isNotBlank() -> userUid
                    firebaseUser?.uid != null -> firebaseUser.uid
                    userEmail.isNotBlank() -> "user_" + userEmail.replace(".", "_").replace("@", "_")
                    else -> ""
                }
                if (effectiveUid.isNotBlank()) {
                    startBackgroundSync(effectiveUid)
                }
                return@launch
            }

            // Poll Firebase Auth every 100ms for up to 2s — reinstall needs time to restore session
            val firebaseUser = withContext(Dispatchers.IO) {
                var user = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                if (user == null) {
                    val deadline = System.currentTimeMillis() + 2000L
                    while (user == null && System.currentTimeMillis() < deadline) {
                        kotlinx.coroutines.delay(100L)
                        user = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                    }
                }
                Log.i(TAG, "checkStartupFlow: firebaseUser=${user?.uid?.take(8)}, storedUid=${userUid.take(8.coerceAtMost(userUid.length))}")
                user
            }

            // Any sign-in signal = user exists (DataStore OR Firebase Auth)
            val hasAnySignIn = userEmail.isNotBlank() || userUid.isNotBlank() || firebaseUser != null
            if (!hasAnySignIn) {
                _startupState.value = StartupState.MandatorySignIn
                return@launch
            }

            // Resolve and persist UID immediately
            val effectiveUid = when {
                userUid.isNotBlank() -> userUid
                firebaseUser?.uid != null -> {
                    val uid = firebaseUser.uid
                    withContext(Dispatchers.IO) {
                        appPreferences.setGoogleUser(
                            email = firebaseUser.email ?: userEmail,
                            displayName = firebaseUser.displayName ?: "",
                            photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                            idToken = null,
                            uid = uid
                        )
                    }
                    uid
                }
                else -> "user_" + userEmail.replace(".", "_").replace("@", "_")
            }

            // Room has 0 expenses (fresh install or reinstall): run restore
            val resolvedEmail = when {
                userEmail.isNotBlank() -> userEmail
                firebaseUser?.email != null -> firebaseUser.email!!
                else -> ""
            }
            runRestore(effectiveUid, resolvedEmail)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Full restore (first sign-in path)
    // ─────────────────────────────────────────────────────────────────────────

    private fun runRestore(uid: String, email: String) {
        viewModelScope.launch {
            // Seed categories immediately so Home works even if restore is offline
            withContext(Dispatchers.IO) {
                try {
                    categoryRepository.insertDefaults()
                    categoryRepository.ensureOthersCategory()
                } catch (_: Exception) {}
            }

            _startupState.value = StartupState.RestoreInProgress(
                step = RestoreStep.SignedIn,
                canSkip = false
            )

            // Show skip button after 1.5 s to ensure user is NEVER trapped
            launch {
                kotlinx.coroutines.delay(1500L)
                val current = _startupState.value
                if (current is StartupState.RestoreInProgress) {
                    _startupState.value = current.copy(canSkip = true)
                }
            }

            // Forward live step updates from the repository to the UI
            launch {
                driveBackupRepository.restoreStep.collect { step ->
                    val current = _startupState.value
                    if (current is StartupState.RestoreInProgress) {
                        _startupState.value = current.copy(step = step)
                    }

                    // As soon as LoadingHistory(done=1, ...) fires, open Home
                    if (step is RestoreStep.LoadingHistory && step.done >= 1) {
                        val pct = (step.done * 100 / step.total.coerceAtLeast(1))
                        if (_startupState.value !is StartupState.Ready) {
                            appPreferences.setOnboardingDone(true)
                            _startupState.value = StartupState.Ready(
                                isOffline = false,
                                historyProgressPercent = pct
                            )
                        } else {
                            val r = _startupState.value as? StartupState.Ready ?: return@collect
                            _startupState.value = r.copy(historyProgressPercent = pct)
                        }
                    }
                    if (step is RestoreStep.Complete) {
                        val current2 = _startupState.value
                        if (current2 is StartupState.Ready) {
                            _startupState.value = current2.copy(historyProgressPercent = 100)
                        }
                    }
                }
            }

            val result = withContext(Dispatchers.IO) {
                driveBackupRepository.restore(uid, email)
            }

            handleRestoreResult(result, uid, email)
        }
    }

    private suspend fun handleRestoreResult(result: RestoreResult, uid: String, email: String) {
        when (result) {
            is RestoreResult.Success -> {
                // May already be Ready from the LoadingHistory trigger above
                if (_startupState.value !is StartupState.Ready) {
                    appPreferences.setOnboardingDone(true)
                    _startupState.value = StartupState.Ready(isOffline = false)
                }
                Log.i(TAG, "restore success: ${result.expensesRestored} expenses, ${result.monthsRestored} months")
            }

            is RestoreResult.NewAccount -> {
                appPreferences.setOnboardingDone(true)
                _startupState.value = StartupState.Ready(isOffline = false)
                Log.i(TAG, "new account: uid=${result.uid}")
            }

            is RestoreResult.Failure.Offline -> {
                Log.w(TAG, "restore offline, hasCachedData=${result.hasCachedData}")
                val isActuallyOffline = !isNetworkAvailable()
                if (result.hasCachedData) {
                    // Let user in from cache
                    appPreferences.setOnboardingDone(true)
                    // Only show offline banner if there's no network at all
                    _startupState.value = StartupState.Ready(isOffline = isActuallyOffline)
                } else {
                    if (isActuallyOffline) {
                        _startupState.value = StartupState.RestoreError(
                            title = "No Internet Connection",
                            detail = "Please connect to the internet to restore your backup.",
                            canUseCachedData = false,
                            canRetry = true,
                            errorTag = "OFFLINE_NO_CACHE"
                        )
                    } else {
                        // Has internet but Drive failed -> show retry
                        _startupState.value = StartupState.RestoreError(
                            title = "Connection Problem",
                            detail = "Could not reach Google Drive. Check your internet and tap Retry.",
                            canUseCachedData = false,
                            canRetry = true,
                            errorTag = "DRIVE_UNREACHABLE"
                        )
                    }
                }
            }

            is RestoreResult.Failure.Timeout -> {
                Log.e(TAG, "restore timeout at step: ${result.step}")
                _startupState.value = StartupState.RestoreError(
                    title = "Restore Timed Out",
                    detail = "Google Drive took too long at step: ${result.step}. This can happen on a slow connection.",
                    canUseCachedData = hasLocalData(),
                    canRetry = true,
                    errorTag = "TIMEOUT_${result.step.uppercase()}"
                )
            }

            is RestoreResult.Failure.PermissionDenied -> {
                Log.e(TAG, "restore permission denied: ${result.detail}")
                _startupState.value = StartupState.RestoreError(
                    title = "Google Drive Permission Required",
                    detail = "Google Drive authorization is needed to restore your backup. Please grant access to your Google Drive AppData folder.",
                    canUseCachedData = hasLocalData(),
                    canRetry = true,
                    errorTag = "PERMISSION_DENIED"
                )
            }

            is RestoreResult.Failure.NotFound -> {
                Log.w(TAG, "restore NotFound for uid=${result.uid} — treating as new account")
                appPreferences.setOnboardingDone(true)
                _startupState.value = StartupState.Ready(isOffline = false)
            }

            is RestoreResult.Failure.CorruptedMonth -> {
                Log.e(TAG, "restore corrupted month: ${result.monthKey} — ${result.error}")
                // Non-fatal: proceed with what we have
                appPreferences.setOnboardingDone(true)
                _startupState.value = StartupState.Ready(isOffline = false)
            }

            is RestoreResult.Failure.PasswordRequired -> {
                Log.w(TAG, "restore password required: mode=${result.mode}")
                _startupState.value = StartupState.RestoreError(
                    title = "Password Protected Backup",
                    detail = "Your Google Drive backup is encrypted with a password or recovery key.",
                    canUseCachedData = hasLocalData(),
                    canRetry = true,
                    errorTag = "PASSWORD_REQUIRED"
                )
            }

            is RestoreResult.Failure.Unknown -> {
                Log.e(TAG, "restore unknown failure: ${result.message}", result.cause)
                _startupState.value = StartupState.RestoreError(
                    title = "Cloud Restore Failed",
                    detail = result.message,
                    canUseCachedData = hasLocalData(),
                    canRetry = true,
                    errorTag = "UNKNOWN"
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Background sync for returning users
    // ─────────────────────────────────────────────────────────────────────────

    private fun startBackgroundSync(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                categoryRepository.insertDefaults()
                categoryRepository.ensureOthersCategory()
            } catch (_: Exception) {}

            // If Room is empty (reinstall), run a full restore in background
            val hasLocalData = try { categoryRepository.getAll().isNotEmpty() } catch (e: Exception) { false }
            if (!hasLocalData) {
                Log.i(TAG, "startBackgroundSync: Room is empty (reinstall?), running background restore")
                val current = _startupState.value
                if (current is StartupState.Ready) {
                    _startupState.value = current.copy(historyProgressPercent = 50)
                }
                try {
                    val email = appPreferences.userEmail.first()
                    val result = driveBackupRepository.restore(uid, email)
                    Log.i(TAG, "startBackgroundSync: restore result = $result")
                } catch (e: Exception) {
                    Log.e(TAG, "startBackgroundSync: background restore failed: ${e.message}")
                }
                val after = _startupState.value
                if (after is StartupState.Ready) {
                    _startupState.value = after.copy(historyProgressPercent = 100)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User actions from error screen
    // ─────────────────────────────────────────────────────────────────────────

    fun getDriveAuthIntent(): Intent {
        val recoverable = googleDriveAuthManager.getLastRecoverableIntent()
        if (recoverable != null) {
            return recoverable
        }
        return googleAuthManager.getSignInIntent(context)
    }

    fun onDriveAuthResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            Log.i(TAG, "onDriveAuthResult: resultCode=$resultCode")
            _startupState.value = StartupState.RestoreInProgress(
                step = RestoreStep.CheckingBackup,
                canSkip = true
            )
            var email = appPreferences.userEmail.first()
            var uid = appPreferences.userUid.first()

            if (data != null) {
                try {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    if (account != null && !account.email.isNullOrBlank()) {
                        val accEmail = account.email!!
                        val name = account.displayName ?: accEmail.substringBefore("@")
                        val photo = account.photoUrl?.toString().orEmpty()
                        val idToken = account.idToken
                        email = accEmail
                        uid = if (uid.isNotBlank()) uid else "user_" + accEmail.replace(".", "_").replace("@", "_")
                        appPreferences.setGoogleUser(accEmail, name, photo, idToken, uid)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "GoogleSignIn parse: ${e.message}")
                }
            }

            if (email.isBlank()) {
                val lastAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                if (lastAccount?.email != null) {
                    email = lastAccount.email!!
                    uid = if (uid.isNotBlank()) uid else "user_" + email.replace(".", "_").replace("@", "_")
                    appPreferences.setGoogleUser(email, lastAccount.displayName ?: "", lastAccount.photoUrl?.toString().orEmpty(), null, uid)
                }
            }

            if (email.isNotBlank() || uid.isNotBlank()) {
                runRestore(uid, email)
            } else {
                _startupState.value = StartupState.MandatorySignIn
            }
        }
    }

    fun retryRestore(activity: Activity? = null) {
        viewModelScope.launch {
            val uid = appPreferences.userUid.first()
            val email = appPreferences.userEmail.first()
            if (uid.isNotBlank()) runRestore(uid, email)
            else _startupState.value = StartupState.MandatorySignIn
        }
    }

    fun signOut(activity: Activity? = null) {
        viewModelScope.launch {
            googleAuthManager.signOut(activity)
            _startupState.value = StartupState.MandatorySignIn
        }
    }

    fun useCachedData() {
        viewModelScope.launch {
            val uid = appPreferences.userUid.first()
            val email = appPreferences.userEmail.first()
            val hasNetwork = isNetworkAvailable()

            appPreferences.setOnboardingDone(true)
            _startupState.value = StartupState.Ready(isOffline = !hasNetwork)

            if (hasNetwork && uid.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(2000L)
                    try {
                        val result = driveBackupRepository.restore(uid, email)
                        if (result is RestoreResult.Success || result is RestoreResult.NewAccount) {
                            val current = _startupState.value
                            if (current is StartupState.Ready) {
                                _startupState.value = current.copy(isOffline = false)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun skipToHome() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                categoryRepository.insertDefaults()
                categoryRepository.ensureOthersCategory()
            } catch (_: Exception) {}
            appPreferences.setOnboardingDone(true)
            _startupState.value = StartupState.Ready(isOffline = false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sign in
    // ─────────────────────────────────────────────────────────────────────────

    fun getSignInIntent(): Intent = googleAuthManager.getSignInIntent(context)

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = null
            val result = googleAuthManager.handleSignInResult(data)
            when (result) {
                is AuthResult.Success -> {
                    _isSigningIn.value = false
                    val uid = googleAuthManager.resolveUid(result.user)
                    // Save user info immediately
                    withContext(Dispatchers.IO) {
                        appPreferences.setGoogleUser(
                            email = result.user.email,
                            displayName = result.user.displayName,
                            photoUrl = result.user.photoUrl,
                            idToken = null,
                            uid = uid
                        )
                    }
                    val hasLocalExpenses = withContext(Dispatchers.IO) {
                        try { expenseDao.getAllIncludingDeleted().any { !it.isDeleted } } catch (_: Exception) { false }
                    }
                    if (hasLocalExpenses) {
                        withContext(Dispatchers.IO) { appPreferences.setOnboardingDone(true) }
                        _startupState.value = StartupState.Ready(isOffline = false)
                        startBackgroundSync(uid)
                    } else {
                        runRestore(uid, result.user.email)
                    }
                }
                is AuthResult.Error -> {
                    _isSigningIn.value = false
                    _signInError.value = result.message
                }
            }
        }
    }

    fun signInWithActivity(activity: Activity) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = null
            val result = googleAuthManager.signIn(activity)
            when (result) {
                is AuthResult.Success -> {
                    _isSigningIn.value = false
                    val uid = googleAuthManager.resolveUid(result.user)
                    withContext(Dispatchers.IO) {
                        appPreferences.setGoogleUser(
                            email = result.user.email,
                            displayName = result.user.displayName,
                            photoUrl = result.user.photoUrl,
                            idToken = null,
                            uid = uid
                        )
                    }
                    val hasLocalExpenses = withContext(Dispatchers.IO) {
                        try { expenseDao.getAllIncludingDeleted().any { !it.isDeleted } } catch (_: Exception) { false }
                    }
                    if (hasLocalExpenses) {
                        withContext(Dispatchers.IO) { appPreferences.setOnboardingDone(true) }
                        _startupState.value = StartupState.Ready(isOffline = false)
                        startBackgroundSync(uid)
                    } else {
                        runRestore(uid, result.user.email)
                    }
                }
                is AuthResult.Error -> {
                    _isSigningIn.value = false
                    _signInError.value = result.message
                }
            }
        }
    }

    fun clearSignInError() { _signInError.value = null }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun hasLocalData(): Boolean = withContext(Dispatchers.IO) {
        try {
            expenseDao.getAllIncludingDeleted().any { !it.isDeleted }
        } catch (e: Exception) { false }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return false
            val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            cap.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) { true }
    }
}
