# Android 15+ APK Build, Signing & Sideloading Distribution Guide

This guide provides end-to-end instructions for producing a production-quality, signed universal `.apk` of **SpendWise** that can be installed on any device running **Android 15 (API 35)** or higher.

---

## 📋 1. Android 15 Specific Requirements & How SpendWise Complies

| Android 15 Requirement | SpendWise Implementation |
|---|---|
| **16 KB Memory Page Size Support** | Android 15 devices (Pixel 8+, OnePlus 12+, Xiaomi 14+) support or enforce 16 KB page sizes. SpendWise uses Zetetic SQLCipher 4.6.1+ native `.so` libraries pre-aligned to 16 KB boundaries. Verified via `zipalign -c -P 16 -v 4`. |
| **Enforced Edge-to-Edge** | Android 15 permanently enforces edge-to-edge displays. SpendWise calls `enableEdgeToEdge()` in `MainActivity` and uses Jetpack Compose `Scaffold` with system `innerPadding` ensuring no UI is obscured by system navigation bars or camera punch-holes. |
| **Strict Foreground Service Types** | In Android 15, foreground services require a valid type and subtype declaration. SpendWise declares `foregroundServiceType="specialUse"` with `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>`. |
| **Restricted Settings for Sideloaded Apps** | On Android 13+, apps installed outside app stores have high-privilege permissions (Usage Access, Accessibility, Overlays) restricted by default. SpendWise includes guided instructions showing how to tap three-dot menu > "Allow restricted settings". |
| **POST_NOTIFICATIONS Enforcement** | Runtime permission prompt with fallback handling if denied. |

---

## 🔑 2. Keystore Generation & Configuration

For manual distribution, a single signing key must be used consistently across all updates. If the key changes, Android will block upgrades with a signature mismatch error.

### Step 2.1: Generate a 4096-bit RSA Release Keystore
Run `scripts/generate-keystore.bat` or execute in terminal:
```bash
keytool -genkeypair -v \
  -keystore spendwise-release.jks \
  -alias spendwise_key \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=SpendWise, OU=Security, O=SpendWise App, L=Bangalore, ST=Karnataka, C=IN" \
  -storepass YourSecurePassword123! \
  -keypass YourSecurePassword123!
```

### Step 2.2: Configure `keystore.properties`
Create `keystore.properties` in the project root:
```properties
storePassword=YourSecurePassword123!
keyPassword=YourSecurePassword123!
keyAlias=spendwise_key
storeFile=scripts/spendwise-release.jks
```

> [!CAUTION]
> Never commit `keystore.properties` or `.jks` files to public git repositories. Back up this file in a secure password manager.

---

## 🏗 3. Building the Universal Release APK

SpendWise builds a **single universal APK** containing all three primary ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) so the same file installs seamlessly on physical ARM devices and x86_64 emulators.

### Option A: Via Command Line (Gradle)
```bash
# In the SpendWise root folder:
./gradlew assembleRelease
```
Output location:
`app/build/outputs/apk/release/app-release.apk`

### Option B: Via Android Studio
1. Open Android Studio (Ladybug / Meerkat or later).
2. Open `d:\Expense Track\SpendWise`.
3. Select menu: **Build > Generate Signed Bundle / APK...**
4. Choose **APK** > Next.
5. Select `spendwise-release.jks`, enter your passwords, and choose alias `spendwise_key`.
6. Select build variant `release`.
7. Ensure both **V1 (Jar Signature)** and **V2 / V3 (Full APK Signature)** are checked.
8. Click **Finish**.

---

## 🔍 4. Verification with `apksigner` and `zipalign`

Run `scripts/verify-apk.bat` or run manually:

### 1. Verify Signature Scheme v2 & v3:
```bash
apksigner verify --verbose --print-certs app-release.apk
```
*Expected output:*
```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

### 2. Verify 16 KB Page Alignment:
```bash
zipalign -c -P 16 -v 4 app-release.apk
```
*Expected output: Verification successful.*

---

## 📲 5. End-User Installation & Sideloading Guide

Distribute `app-release.apk` via USB, WhatsApp, Telegram, or local file transfer.

### Step 1: Copy APK to the Phone
Transfer `app-release.apk` to the device's **Downloads** folder.

### Step 2: Allow Unknown Apps
1. Open your device's **Files** app or browser.
2. Tap `app-release.apk`.
3. If prompted: *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings** and toggle **Allow from this source**.

### Step 3: Google Play Protect Warning ("Unknown Developer")
Because this APK is signed with a private release key and not published on the Play Store, Google Play Protect may show:
- *"Blocked by Play Protect: Unrecognized app"*
- Tap **More details** > **Install anyway**.

### Step 4: Sideload "Restricted Settings" Bypass (Android 13, 14, 15)
To allow SpendWise to use **Usage Access** (to detect UPI apps):
1. Open Android **Settings** > **Apps** > **SpendWise**.
2. Tap the **three vertical dots** in the top-right corner.
3. Tap **Allow restricted settings**.
4. Confirm with fingerprint or PIN.
5. Return to SpendWise **Settings** > tap **Grant Usage Access** > select SpendWise > toggle **Permit usage access**.

### Step 5: OEM Battery & Autostart Optimization
- **Xiaomi / MIUI / HyperOS**: Settings > Apps > Manage Apps > SpendWise > enable **Autostart**; Battery Saver > set to **No restrictions**.
- **Samsung One UI**: Settings > Battery > Background usage limits > add SpendWise to **Never sleeping apps**.
- **OnePlus / Realme / Oppo (ColorOS)**: App info > Battery usage > enable **Allow background activity** & **Allow auto-launch**.
- **Google Pixel**: Settings > Apps > SpendWise > App battery usage > select **Unrestricted**.

---

## 🧪 6. Testing Checklist for Android 15+

### Emulator (Android 15 API 35 with 16KB Page Size)
- [ ] Create AVD with **Vanilla Ice Cream (API 35)** system image with 16KB support enabled.
- [ ] Install via ADB: `adb install app-release.apk`.
- [ ] Verify app launches with splash screen into `MainActivity`.
- [ ] Verify `FLAG_SECURE`: run `adb shell screencap -p /sdcard/sc.png` and verify screenshot is completely black.
- [ ] Edge-to-edge check: verify no bottom buttons or top bars are overlapped by navigation bar or status bar.

### Physical Device (Android 15)
- [ ] First launch: Prompt to enter Monthly Salary appears.
- [ ] Enter ₹50,000 salary -> Available Balance shows ₹50,000.
- [ ] Add ₹1,500 Grocery expense using "Income" -> Available balance drops to ₹48,500.
- [ ] Add ₹5,000 Travel expense using "Credit Card" -> Available balance remains ₹48,500; CC Spend shows ₹5,000.
- [ ] Biometric lock: Lock phone or put app in background for >30s -> App shows LockScreen; unlocks via fingerprint.
- [ ] Reports tab: Donut chart renders category percentages correctly; CSV export triggers system share sheet.
- [ ] UPI Monitor: Toggle UPI trigger on in Settings -> Foreground service notification appears. Open and close a UPI test app -> reminder notification fires.
