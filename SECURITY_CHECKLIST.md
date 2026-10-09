# SpendWise Security Checklist & OWASP MASVS Mapping

This document provides a comprehensive security audit mapping each requirement of the SpendWise application directly to the codebase and OWASP Mobile Application Security Verification Standard (MASVS v2.0).

---

## 🛡 OWASP MASVS Audit Matrix

| MASVS Control | Requirement Description | Implementation in SpendWise | Code Location |
|---|---|---|---|
| **MASVS-STORAGE-1** | Sensitive data stored securely with encryption | SQLCipher 4.6.1 AES-256 database encryption with hardware-backed Android KeyStore key wrapping. | [`SpendWiseDatabase.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/data/local/SpendWiseDatabase.kt), [`DatabaseKeyProvider.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/data/security/DatabaseKeyProvider.kt) |
| **MASVS-STORAGE-2** | No sensitive data written to device backups | `android:allowBackup="false"`, `data_extraction_rules.xml`, and `backup_rules.xml` exclude all databases, preferences, and internal files. | [`AndroidManifest.xml`](file:///d:/Expense%20Track/SpendWise/app/src/main/AndroidManifest.xml), [`data_extraction_rules.xml`](file:///d:/Expense%20Track/SpendWise/app/src/main/res/xml/data_extraction_rules.xml) |
| **MASVS-CRYPTO-1** | Industry-standard crypto primitives | AES/GCM/NoPadding (256-bit key) with random 12-byte IV for key encryption; SQLCipher PBKDF2 with SHA-512 and AES-256-CBC. | [`KeystoreManager.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/data/security/KeystoreManager.kt) |
| **MASVS-CRYPTO-3** | Secure key generation & management | Keys generated in `AndroidKeyStore` provider (`KeyProperties.PURPOSE_ENCRYPT or PURPOSE_DECRYPT`), never stored in plaintext or hardcoded. | [`KeystoreManager.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/data/security/KeystoreManager.kt) |
| **MASVS-AUTH-1** | Biometric and device credential authentication | AndroidX `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` + SHA-256 fallback PIN. | [`LockScreen.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/ui/screen/lock/LockScreen.kt), [`AppLockViewModel.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/ui/lock/AppLockViewModel.kt) |
| **MASVS-NETWORK-1** | Zero network attack surface | No `android.permission.INTERNET` declared. `network_security_config.xml` sets `cleartextTrafficPermitted="false"`. Zero network outbound capability. | [`AndroidManifest.xml`](file:///d:/Expense%20Track/SpendWise/app/src/main/AndroidManifest.xml), [`network_security_config.xml`](file:///d:/Expense%20Track/SpendWise/app/src/main/res/xml/network_security_config.xml) |
| **MASVS-PLATFORM-1** | UI & screenshot protection | `WindowManager.LayoutParams.FLAG_SECURE` set on `MainActivity` window to block OS screenshots, screen recorders, and task switcher snapshots. | [`MainActivity.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/MainActivity.kt) |
| **MASVS-PLATFORM-2** | App auto-lock on background & timeout | Configurable inactivity timeout (default 30s) tracked across `onPause()` / `onResume()` lifecycle events. | [`AppLockViewModel.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/ui/lock/AppLockViewModel.kt), [`MainActivity.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/MainActivity.kt) |
| **MASVS-RESILIENCE-1** | Root, emulator, debugger detection | Heuristic security checks for known `su` binary paths, generic build fingerprints, and attached debugging tools. | [`SecurityDetector.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/data/security/SecurityDetector.kt) |
| **MASVS-CODE-1** | ProGuard / R8 code shrinking and log stripping | Release builds strip `Log.v`, `Log.d`, `Log.i`, `Log.w`, `Log.e` calls using `assumenosideeffects` and obfuscate class/member names. | [`proguard-rules.pro`](file:///d:/Expense%20Track/SpendWise/app/proguard-rules.pro), [`app/build.gradle.kts`](file:///d:/Expense%20Track/SpendWise/app/build.gradle.kts) |
| **MASVS-PRIVACY-1** | Privacy in UPI event monitoring | Only detects package foreground transitions via `UsageStatsManager`. No accessibility service used, no SMS read, no transaction screen scraping. | [`UpiMonitorService.kt`](file:///d:/Expense%20Track/SpendWise/app/src/main/kotlin/com/spendwise/app/service/UpiMonitorService.kt) |

---

## 🔑 Key Management Details

```
+-------------------------------------------------------------------------+
|                              Android Device                             |
|                                                                         |
|  +------------------------+             +----------------------------+  |
|  |   Hardware Keystore    |             | Encrypted DataStore        |  |
|  |  (TEE / StrongBox)     |             | (secure_key_store)         |  |
|  |                        |             |                            |  |
|  | AES-256 Master Key     | --encrypt-> | Encrypted Passphrase Blob  |  |
|  | "SpendWiseDatabaseKey" | <--decrypt- | (IV + Ciphertext)          |  |
|  +------------------------+             +----------------------------+  |
|              |                                        |                 |
|              v                                        v                 |
|  +-------------------------------------------------------------------+  |
|  |                      Database Initialization                      |  |
|  |   net.zetetic.database.sqlcipher.SupportFactory(passphrase)       |  |
|  |                                                                   |  |
|  |   Encrypted Database: spendwise.db (AES-256-CBC, PBKDF2 SHA-512)  |  |
|  +-------------------------------------------------------------------+  |
+-------------------------------------------------------------------------+
```

1. **Generation**: On first run, a 256-bit cryptographically secure pseudorandom key is generated via `SecureRandom`.
2. **Key Storage**: The Master Key is generated inside the hardware-backed `AndroidKeyStore` (`AES/GCM/NoPadding`).
3. **Protection**: The database passphrase is encrypted by the Keystore Master Key with a unique 12-byte IV and stored in app private storage.
4. **Memory Hygiene**: Passphrase is only loaded in memory during Room OpenHelper creation and is wiped from memory as soon as the handle is acquired.
