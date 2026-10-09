<div align="center">

<img src="docs/assets/expenso_logo.png" alt="Expenso Logo" width="128" height="128" />

# Expenso (SpendWise) — Production-Grade Android 15+ Expense Tracker

**Expenso** (also known as **SpendWise**) is an offline-first, highly secure personal finance and expense tracking application engineered in **Kotlin & Jetpack Compose**. Built specifically for **Android 15 (API 35+)**, Expenso features **complete data sovereignty**, **Zetetic SQLCipher hardware-backed AES-256 database encryption**, **WhatsApp-style encrypted Google Drive backups**, **Firebase cloud sync**, and a dedicated **10+ AI feature `:intelligence` subsystem** powered by on-device ML and Google Gemini.

[![Platform](https://img.shields.io/badge/Platform-Android%2015%2B%20%28API%2035%29-brightgreen.svg)](https://developer.android.com/about/versions/15)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-blue.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-purple.svg)](https://developer.android.com/jetpack/compose)
[![Encryption](https://img.shields.io/badge/Security-SQLCipher%20AES--256%20%2B%20Keystore-red.svg)](https://www.zetetic.net/sqlcipher/)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean%20%2B%20Multi--Module-orange.svg)](ARCHITECTURE.md)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## 🌟 Key Features & Capabilities

### 💳 Core Financial & Ledger Management
- **Smart Cash Flow & Available Balance Engine**:
  $$\text{Available Balance} = \text{Monthly Salary} - \text{Spent (Income)} - \text{Credit Card Due Carried}$$
- **Multi-Source Expense Logging**: Categorize expenses by **Income** (deducts immediately from current available balance) or **Credit Card** (accumulates in separate ledger).
- **Automated Month Rollover**: On the 1st of every month, previous credit card expenditure automatically carries forward as "Credit Card Due" and deducts from the new month's salary.
- **Custom Categories & Budget Limits**: Set monthly budgets per category with real-time progress indicators and warning alerts.

### 🧠 On-Device AI & Financial Intelligence (`:intelligence` Module)
- **NLP Expense Parser**: Parse natural language phrases like `"Paid ₹450 for groceries yesterday"` into amount, category, and date automatically.
- **Receipt OCR Scanner**: Capture receipts using device camera via ML Kit Text Recognition to auto-populate transaction details.
- **Voice Expense Capture**: Record voice notes to log expenses hands-free.
- **Anomaly Detection**: Flag unusual spending spikes based on rolling standard deviations.
- **End-of-Month Predictive Forecasting**: Estimate total monthly expenditure using spending velocity algorithms.
- **Financial Health Index & What-If Simulator**: Calculate a 0-100 financial health score and simulate expense reduction impacts.
- **Google Gemini Financial Assistant**: Interactive AI chatbot offering contextual spending advice and query resolution.

### 🛡 Enterprise Security & Privacy (OWASP MASVS Compliant)
- **SQLCipher 4.6.1 AES-256 Encryption**: Encrypts the entire SQLite database at rest with PBKDF2 HMAC-SHA512 derivation.
- **Hardware-Backed Android KeyStore**: Passphrases wrapped in TEE / StrongBox using AES-256-GCM.
- **Biometric App Lock**: BiometricPrompt API with device credential fallback and SHA-256 PIN backup.
- **Screenshot Protection**: `FLAG_SECURE` enforced to prevent screenshots, screen recording, and task switcher previews.
- **Privacy-First UPI Payment Detection**: Monitors PhonePe, Google Pay, Paytm, and BHIM launches via `UsageStatsManager` without reading transaction details or SMS messages.

### ☁️ Backup & Cloud Synchronization
- **WhatsApp-Style Google Drive Backup**: Encrypted binary envelope backups (`AES-256-GCM`) saved to hidden application folder (`appDataFolder`).
- **Firebase Firestore Sync**: Realtime multi-device cloud synchronization with offline queue fallback (`SyncOutbox`).
- **Data Import / Export**: Complete JSON & CSV data export and restore capabilities.

---

## 🏛 Project Architecture & Tech Stack

Expenso is structured as a **multi-module Gradle project** enforcing Clean Architecture principles:

```
Expenso/
├── app/                  # Main Android Application (Compose UI, ViewModels, DI, Storage)
├── intelligence/         # Standalone AI Subsystem (NLP, OCR, Anomaly, Forecast, Health Score)
├── scripts/              # Build scripts, Keystore generators & APK verifiers
└── docs/                 # Documentation assets and visual guides
```

| Layer / Subsystem | Technology / Library |
|---|---|
| **Language & Runtime** | Kotlin 2.0+, Java 17, Android 15 (minSdk 35, targetSdk 35) |
| **UI Framework** | Jetpack Compose, Material 3, Navigation Compose, Single Activity Architecture |
| **Dependency Injection** | Google Dagger Hilt 2.53 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`) |
| **Local Encrypted DB** | Zetetic SQLCipher 4.6.1, Room 2.6+, Android KeyStore AES-256-GCM |
| **Background Processing** | AndroidX WorkManager 2.10+ (`MonthRolloverWorker`, `DailyReminderWorker`) |
| **Cloud & Network** | Firebase Firestore, Google Drive REST API, Ktor / Retrofit for Gemini REST API |
| **On-Device ML** | Google ML Kit Text Recognition, Custom NLP & Time-Series Engine |
| **Memory Optimization** | 16 KB Page Alignment enabled for Android 15 native `.so` binaries |

For a complete architectural breakdown, sequence diagrams, and schema details, see [**ARCHITECTURE.md**](ARCHITECTURE.md).

---

## 📸 Screenshots & Visual Overview

```
+---------------------------+  +---------------------------+  +---------------------------+
|      Home Dashboard       |  |      Add Expense (NLP)    |  |     AI Forecast & Digest  |
|                           |  |                           |  |                           |
|  Available: ₹42,500       |  |  "Paid 450 for coffee"    |  |  Est. Month End: ₹38,200  |
|  CC Spend: ₹8,200         |  |  -> Amount: ₹450.00       |  |  Health Score: 88 / 100   |
|  [|||||||||......] 65%    |  |  -> Category: Food        |  |  Spending Velocity: Low   |
+---------------------------+  +---------------------------+  +---------------------------+
```

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- **Android Studio**: Ladybug (2024.2+) or Meerkat (2024.3+)
- **JDK**: JDK 17 or higher
- **Android SDK**: API 35 (Android 15) SDK installed
- **Gradle**: 8.7+ (wrapper included)

### 1. Clone the Repository
```bash
git clone https://github.com/VALLARASU64/Expenso-Expense-Tracking.git
cd Expenso-Expense-Tracking
```

### 2. Generate Release Keystore (Optional for Release Builds)
Run the automated keystore script:
```cmd
cd scripts
generate-keystore.bat
```
This generates `spendwise-release.jks` and writes `keystore.properties`.

### 3. Build via Command Line
```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK (Universal ARM64/x86_64 with 16KB Page Alignment)
./gradlew assembleRelease
```
The resulting release APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

---

## 🔒 Security & Privacy Model

Expenso complies strictly with **OWASP MASVS v2.0** standards:

- **MASVS-STORAGE-1**: Sensitive data encrypted at rest using SQLCipher 4.6.1.
- **MASVS-STORAGE-2**: OS backup flags disabled (`android:allowBackup="false"`).
- **MASVS-CRYPTO-1**: AES-256-GCM and PBKDF2 HMAC-SHA512 crypto primitives.
- **MASVS-AUTH-1**: AndroidX BiometricPrompt with strong credential fallbacks.
- **MASVS-PLATFORM-1**: `FLAG_SECURE` prevents screenshot leakage.
- **MASVS-PRIVACY-1**: UPI app monitoring operates via `UsageStatsManager` with zero transaction screen scraping.

For detailed security mapping, check [**SECURITY_CHECKLIST.md**](SECURITY_CHECKLIST.md).

---

## 📂 Project Documentation Index

- 🏛 [**ARCHITECTURE.md**](ARCHITECTURE.md) — Comprehensive System Design, Sequence Diagrams & DB Schemas
- 🔐 [**SECURITY_CHECKLIST.md**](SECURITY_CHECKLIST.md) — Security Audit & OWASP MASVS v2.0 Mapping Matrix
- ☁️ [**DRIVE_BACKUP_SETUP.md**](DRIVE_BACKUP_SETUP.md) — Google Drive Encrypted Backup & Restore Guide
- 🔥 [**SETUP.md**](SETUP.md) — Firebase Setup & Firestore Cloud Rules Guide
- 📱 [**ANDROID_15_DISTRIBUTION_GUIDE.md**](ANDROID_15_DISTRIBUTION_GUIDE.md) — Sideloading, 16KB Page Alignment & Distribution Guide

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

```
Copyright (c) 2026 Expenso / SpendWise Team
```
