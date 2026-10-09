package com.spendwise.app.ui.lock

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

enum class LockState { INITIALIZING, LOCKED, UNLOCKED, LOCK_NOT_REQUIRED }

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _isAppLockEnabled = MutableStateFlow<Boolean?>(null)
    val isAppLockEnabled: StateFlow<Boolean?> = _isAppLockEnabled.asStateFlow()

    private val _lockState = MutableStateFlow(LockState.INITIALIZING)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _hasPinSet = MutableStateFlow(false)
    val hasPinSet: StateFlow<Boolean> = _hasPinSet.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(true)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _lockTimeoutSeconds = MutableStateFlow(30)
    val lockTimeoutSeconds: StateFlow<Int> = _lockTimeoutSeconds.asStateFlow()

    private var currentPinHash: String? = null
    private var backgroundTime: Long = 0L
    private var isAuthenticating: Boolean = false

    init {
        viewModelScope.launch {
            combine(
                appPreferences.appLockEnabled,
                appPreferences.lockTimeoutSeconds,
                appPreferences.pinHash,
                appPreferences.biometricEnabled
            ) { enabled, timeoutSec, pinHash, bioEnabled ->
                Triple(enabled, timeoutSec, pinHash) to bioEnabled
            }.collect { (triple, bioEnabled) ->
                val (enabled, timeoutSec, pinHash) = triple
                _isAppLockEnabled.value = enabled
                _lockTimeoutSeconds.value = timeoutSec
                currentPinHash = pinHash
                _hasPinSet.value = !pinHash.isNullOrBlank()
                _biometricEnabled.value = bioEnabled

                if (!enabled) {
                    _lockState.value = LockState.LOCK_NOT_REQUIRED
                } else if (_lockState.value == LockState.INITIALIZING || _lockState.value == LockState.LOCK_NOT_REQUIRED) {
                    _lockState.value = LockState.LOCKED
                }
            }
        }
    }

    fun setAuthenticating(authenticating: Boolean) {
        isAuthenticating = authenticating
    }

    fun onAppForeground() {
        if (_isAppLockEnabled.value != true) return
        if (_lockState.value == LockState.LOCK_NOT_REQUIRED) return
        if (isAuthenticating) return

        val timeoutMs = _lockTimeoutSeconds.value * 1000L
        val elapsed = System.currentTimeMillis() - backgroundTime
        if (backgroundTime > 0 && elapsed >= timeoutMs) {
            _lockState.value = LockState.LOCKED
        }
    }

    fun onAppBackground() {
        if (!isAuthenticating) {
            backgroundTime = System.currentTimeMillis()
        }
    }

    fun lockNow() {
        if (_isAppLockEnabled.value == true && _lockState.value != LockState.LOCK_NOT_REQUIRED) {
            _lockState.value = LockState.LOCKED
        }
    }

    fun onUnlocked() {
        backgroundTime = 0L
        isAuthenticating = false
        _lockState.value = LockState.UNLOCKED
    }

    fun handleDeepLink(intent: Intent) {
        // Deep link intent handled by navigation graph
    }

    fun verifyPin(pin: String): Boolean {
        val hash = hashPin(pin)
        return hash == currentPinHash
    }

    fun savePin(pin: String) {
        viewModelScope.launch {
            val hash = hashPin(pin)
            appPreferences.setPinHash(hash)
            currentPinHash = hash
            _hasPinSet.value = true
        }
    }

    fun removePin() {
        viewModelScope.launch {
            appPreferences.setPinHash(null)
            currentPinHash = null
            _hasPinSet.value = false
        }
    }

    fun setLockTimeout(seconds: Int) {
        viewModelScope.launch {
            appPreferences.setLockTimeout(seconds)
            _lockTimeoutSeconds.value = seconds
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBiometricEnabled(enabled)
            _biometricEnabled.value = enabled
        }
    }

    fun toggleAppLock(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAppLockEnabled(enabled)
            _isAppLockEnabled.value = enabled
            if (!enabled) {
                _lockState.value = LockState.LOCK_NOT_REQUIRED
            } else {
                _lockState.value = LockState.LOCKED
            }
        }
    }

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
