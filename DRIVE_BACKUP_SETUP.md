# Expenso — Google Drive Encrypted Backup & Restore Architecture

## Overview
Expenso uses a **local-first, WhatsApp-style cloud backup architecture**.
- **Source of truth**: Local Room database encrypted with SQLCipher on the user's device.
- **Backup storage**: The user's private Google Drive hidden `appDataFolder` (`https://www.googleapis.com/auth/drive.appdata` scope).
- **Security**: Envelope encryption using **AES-256-GCM** (authenticated encryption with 128-bit tag, 96-bit random IV, SHA-256 payload integrity check, and GZIP compression).
- **Privacy**: The backup is stored in the hidden application data folder. It is invisible in standard Google Drive folders, cannot be accidentally altered by file managers, and only Expenso can read or write it.

---

## Acceptance Story: New Phone Restore
When moving to a new phone or reinstalling:
1. User installs `Expenso.apk` and signs in with their Google account.
2. The app detects a clean database (0 records) and reaches Google Drive `appDataFolder`.
3. It locates `expenso_backup_latest.enc`, inspects the header, and displays the backup details (Date, Size, Expenses count, Months count).
4. The backup is downloaded, decrypted (using the account-bound key or prompt if password-protected), decompressed, and validated against its SHA-256 hash.
5. All records are restored atomically in a single Room transaction:
   - Categories
   - Budgets
   - Monthly Salaries (`income_month`)
   - Expenses (all fields, notes, dates, payment sources)
   - Credit Card Ledgers (with automatic balance recalculation)
   - User Preferences (currency symbol, onboardingDone = true)
6. **Result**: The user lands directly on the Home dashboard with **100% of their data restored**, without ever being asked for salary or onboarding configuration.

---

## File Envelope Layout
Every backup file stored in Google Drive uses the following binary format:
```
[7 bytes: "EXPBK01"]
[1 byte:  Envelope Version = 0x01]
[1 byte:  Mode: 0x01 (Account-Bound) | 0x02 (Password) | 0x03 (Recovery-Key)]
[16 bytes: Random Salt]
[12 bytes: Random IV]
[N bytes:  Ciphertext + 16-byte AES-GCM Auth Tag]
```

### Encryption Modes
1. **Account-Bound Mode (Default)**:
   - Key derived via PBKDF2-HMAC-SHA256 from the user's Google email + application salt.
   - Restores automatically on any device as long as the user signs in with the same Google account.
2. **Password Mode (Optional)**:
   - Key derived via PBKDF2-HMAC-SHA256 (100,000 iterations) from a user-chosen password + random salt.
   - User can also export a 64-character hexadecimal Recovery Key.

---

## Safe Rotation Policy
In the user's hidden `appDataFolder`, Expenso maintains:
- `expenso_backup_latest.enc` — Most recent successful snapshot.
- `expenso_backup_previous.enc` — Fallback copy rotated before writing the latest snapshot.
- `expenso_backup_YYYY_MM.enc` — Up to 3 historical monthly archives (first backup of each month).

---

## Automated Backup Triggers (WorkManager)
1. **Debounced Trigger**: Scheduled 2 minutes after any local change (expense created, category modified, salary updated). Debounce timer resets if new edits occur within the 2-minute window.
2. **Periodic Trigger**: Daily or Weekly background backup respecting constraints:
   - Network: Wi-Fi only (configurable in Settings).
   - Battery: Requires battery not low.
3. **Manual Trigger**: "Back Up Now" button in **Settings → Google Drive Backup**.

---

## Testing & Diagnostics
To test Google Drive connectivity and cryptographic integrity:
1. Open **Settings → Sync Diagnostics**.
2. Tap **Test Drive OAuth & List Files**:
   - Acquires Google Drive Bearer token silently.
   - Queries `appDataFolder` and lists existing backup files, sizes, and timestamps.
3. Tap **Test Snapshot & AES-256 Crypto**:
   - Builds logical Room snapshot, calculates SHA-256 hash, encrypts with AES-256-GCM, decrypts, and verifies data integrity.
