package com.spendwise.app.data.security

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File

object SecurityDetector {
    /**
     * Returns a set of detected security concerns as human-readable strings.
     * Only used for informational warnings, never blocks app usage.
     */
    fun detectThreats(context: Context): Set<String> {
        val threats = mutableSetOf<String>()

        if (isRooted()) threats.add("Device appears to be rooted")
        if (isEmulator()) threats.add("Running on emulator")
        if (isDebuggerAttached()) threats.add("Debugger is attached")

        return threats
    }

    private fun isRooted(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return rootPaths.any { File(it).exists() }
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected()
    }
}
