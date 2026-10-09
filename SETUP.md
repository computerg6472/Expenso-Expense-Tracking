# Expenso — Firebase & Cloud Setup Guide

Follow these steps to set up Firebase for the Expenso (SpendWise) app.

---

## 1. Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add Project** → name it `Expenso` (or any name)
3. Disable Google Analytics (optional)
4. Click **Create Project**

---

## 2. Add Android App

1. Click **Add App** → Android icon
2. **Package name**: `com.spendwise.app`
3. **App nickname**: Expenso
4. **Debug SHA-1**: Run `gradlew signingReport` and paste the debug SHA-1
5. **Release SHA-1**: From your keystore — run:
   ```
   keytool -list -v -keystore your-keystore.jks -alias your-alias
   ```
6. Download `google-services.json` → place in `app/` folder

---

## 3. Enable Google Sign-In

1. Firebase Console → **Authentication** → **Sign-in method**
2. Enable **Google**
3. Set support email
4. Save

---

## 4. Create Firestore Database

1. Firebase Console → **Firestore Database** → **Create database**
2. Choose **Production mode** (rules applied separately)
3. Select location: `asia-south1` (Mumbai) for India users
4. Click **Done**

---

## 5. Deploy Firestore Security Rules

Option A — Firebase CLI (recommended):
```bash
npm install -g firebase-tools
firebase login
firebase init firestore   # select your project
firebase deploy --only firestore:rules
```

Option B — Firebase Console:
1. Firestore → **Rules** tab
2. Paste content from `firestore.rules`
3. Click **Publish**

---

## 6. Firestore Data Structure

```
users/{uid}/
  meta/profile        ← onboardingDone, schemaVersion, per-month checksums
  config/main         ← all categories + settings
  months/{yyyy-MM}    ← salary + all expenses as a map
    └ parts/{0,1,...}  ← overflow parts for months > 1800 expenses
```

---

## 7. Verify Setup

In the app: **Settings → Sync Diagnostics → Run Full Restore Test**

Expected result:
- Network: ✓ Online
- Firebase SDK: ✓ Ready
- Test result: PASS

---

## 8. Performance Expectations

| Operation | Target | How |
|-----------|--------|-----|
| Cold start (existing user) | < 500ms | Room cache, no Firestore |
| Restore current month | < 2s | 3 parallel reads |
| Restore 12 months history | < 8s | 6 parallel reads × 2 batches |
| Add expense write | < 200ms | Local Room instant, cloud debounced 500ms |
| Total reads per restore | ~14 | Fixed regardless of expense count |

---

## 9. Troubleshooting

| Problem | Solution |
|---------|----------|
| Shows "Working offline" on WiFi | Check SHA-1 fingerprint in Firebase console |
| Permission denied in Firestore | Re-deploy `firestore.rules` |
| Data not restoring on reinstall | User must be signed in to Google — Firebase Auth token must be valid |
| Migration stuck | Check Logcat for `ExpensoSync` tag — migration from v2 runs once |
