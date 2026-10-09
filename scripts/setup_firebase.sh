#!/usr/bin/env bash
# ==========================================================
#  Expenso / SpendWise - Firebase Automated Setup Script
# ==========================================================
set -euo pipefail

PROJECT_ID="${1:-}"
SKIP_DEPLOY="${2:-false}"
SKIP_VERIFY="${3:-false}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=========================================================="
echo "  Expenso / SpendWise - Firebase Automated Setup Script   "
echo "=========================================================="
echo ""

# 1. Prerequisite Checks
echo "[1/8] Checking prerequisites..."
KEYTOOL_CMD="keytool"
if ! command -v keytool &> /dev/null; then
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
        KEYTOOL_CMD="$JAVA_HOME/bin/keytool"
    else
        echo "Error: keytool (Java) not found. Please install JDK or set JAVA_HOME."
        exit 1
    fi
fi
echo "  -> Keytool found: $KEYTOOL_CMD"

# Firebase CLI resolution
FIREBASE_RUN=()
if command -v firebase &> /dev/null; then
    FIREBASE_RUN=("firebase")
elif command -v npx &> /dev/null; then
    echo "  -> 'firebase' not in PATH, using 'npx firebase-tools'..."
    FIREBASE_RUN=("npx" "firebase-tools")
else
    echo "Error: Firebase CLI not found. Please install via: npm install -g firebase-tools"
    exit 1
fi
echo "  -> Firebase CLI is ready."

# 2. Check Firebase Login Status
echo ""
echo "[2/8] Checking Firebase authentication..."
"${FIREBASE_RUN[@]}" login --interactive || true

# 3. Resolve Project ID
echo ""
echo "[3/8] Resolving Firebase Project ID..."
if [ -z "$PROJECT_ID" ]; then
    echo "Available Firebase projects:"
    "${FIREBASE_RUN[@]}" projects:list || true
    read -rp "Enter your Firebase Project ID (or type 'NEW' to create a new one): " PROJECT_ID
    if [ "$PROJECT_ID" = "NEW" ] || [ "$PROJECT_ID" = "new" ]; then
        read -rp "Enter a unique project ID for the new project: " NEW_ID
        "${FIREBASE_RUN[@]}" projects:create "$NEW_ID" --display-name "Expenso App"
        PROJECT_ID="$NEW_ID"
    fi
fi

if [ -z "$PROJECT_ID" ]; then
    echo "Error: Project ID is required to proceed."
    exit 1
fi
echo "  -> Using Firebase Project: $PROJECT_ID"

# 4. Register Android App
echo ""
echo "[4/8] Registering Android app (com.spendwise.app)..."
APP_PACKAGE="com.spendwise.app"
APP_ID=""

APPS_JSON=$("${FIREBASE_RUN[@]}" apps:list android --project "$PROJECT_ID" --json 2>/dev/null || echo "{}")
APP_ID=$(echo "$APPS_JSON" | grep -B 2 "$APP_PACKAGE" | grep "appId" | head -n 1 | awk -F'"' '{print $4}' || echo "")

if [ -z "$APP_ID" ]; then
    echo "  -> Registering com.spendwise.app with project..."
    CREATE_JSON=$("${FIREBASE_RUN[@]}" apps:create android "$APP_PACKAGE" --project "$PROJECT_ID" --nickname "Expenso" --json 2>/dev/null || echo "{}")
    APP_ID=$(echo "$CREATE_JSON" | grep "appId" | head -n 1 | awk -F'"' '{print $4}' || echo "")
fi
echo "  -> Android App ID: ${APP_ID:-'(will inspect via console)'}"

# 5. Extract SHA Fingerprints
echo ""
echo "[5/8] Extracting SHA-1 and SHA-256 certificate fingerprints..."
RELEASE_KEYSTORE="$PROJECT_ROOT/keystore/release.keystore"
if [ ! -f "$RELEASE_KEYSTORE" ]; then
    echo "  -> Creating fixed release keystore at $RELEASE_KEYSTORE..."
    mkdir -p "$PROJECT_ROOT/keystore"
    "$KEYTOOL_CMD" -genkeypair -v -keystore "$RELEASE_KEYSTORE" -alias expenso -keyalg RSA -keysize 2048 -validity 10000 -storepass expenso123 -keypass expenso123 -dname "CN=Expenso, OU=SpendWise, O=SpendWise, L=Bengaluru, ST=KA, C=IN"
fi

RELEASE_OUT=$("$KEYTOOL_CMD" -list -v -keystore "$RELEASE_KEYSTORE" -alias expenso -storepass expenso123)
RELEASE_SHA1=$(echo "$RELEASE_OUT" | grep "SHA1:" | head -n 1 | awk '{print $2}')
RELEASE_SHA256=$(echo "$RELEASE_OUT" | grep "SHA256:" | head -n 1 | awk '{print $2}')
echo "  -> Release SHA-1:   $RELEASE_SHA1"
echo "  -> Release SHA-256: $RELEASE_SHA256"

DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
DEBUG_SHA1=""
DEBUG_SHA256=""
if [ -f "$DEBUG_KEYSTORE" ]; then
    DEBUG_OUT=$("$KEYTOOL_CMD" -list -v -keystore "$DEBUG_KEYSTORE" -alias androiddebugkey -storepass android 2>/dev/null || echo "")
    DEBUG_SHA1=$(echo "$DEBUG_OUT" | grep "SHA1:" | head -n 1 | awk '{print $2}')
    DEBUG_SHA256=$(echo "$DEBUG_OUT" | grep "SHA256:" | head -n 1 | awk '{print $2}')
    echo "  -> Debug SHA-1:     $DEBUG_SHA1"
    echo "  -> Debug SHA-256:   $DEBUG_SHA256"
fi

# 6. Register SHA Fingerprints
echo ""
echo "[6/8] Registering SHA fingerprints with Firebase Project..."
if [ -n "$APP_ID" ]; then
    [ -n "$RELEASE_SHA1" ] && "${FIREBASE_RUN[@]}" apps:android:sha:create "$APP_ID" "$RELEASE_SHA1" --project "$PROJECT_ID" 2>/dev/null || true
    [ -n "$RELEASE_SHA256" ] && "${FIREBASE_RUN[@]}" apps:android:sha:create "$APP_ID" "$RELEASE_SHA256" --project "$PROJECT_ID" 2>/dev/null || true
    [ -n "$DEBUG_SHA1" ] && "${FIREBASE_RUN[@]}" apps:android:sha:create "$APP_ID" "$DEBUG_SHA1" --project "$PROJECT_ID" 2>/dev/null || true
    [ -n "$DEBUG_SHA256" ] && "${FIREBASE_RUN[@]}" apps:android:sha:create "$APP_ID" "$DEBUG_SHA256" --project "$PROJECT_ID" 2>/dev/null || true
    echo "  -> Fingerprints registered."
fi

# 7. Download google-services.json & Deploy Firestore
echo ""
echo "[7/8] Downloading google-services.json & deploying Firestore configuration..."
if [ -n "$APP_ID" ]; then
    "${FIREBASE_RUN[@]}" apps:sdkconfig android "$APP_ID" --out "$PROJECT_ROOT/app/google-services.json" --project "$PROJECT_ID" 2>/dev/null || echo "Note: If download failed, download manually from Firebase Console."
fi

if [ "$SKIP_DEPLOY" != "true" ]; then
    echo "  -> Deploying Firestore rules and indexes..."
    "${FIREBASE_RUN[@]}" deploy --only firestore:rules,firestore:indexes --project "$PROJECT_ID" 2>/dev/null || echo "Note: Ensure Cloud Firestore is initialized in Native mode."
fi

# 8. Gradle Verification Task
echo ""
echo "[8/8] Running Gradle verification task 'verifyGoogleServices'..."
if [ "$SKIP_VERIFY" != "true" ]; then
    chmod +x "$PROJECT_ROOT/gradlew"
    "$PROJECT_ROOT/gradlew" verifyGoogleServices
fi

echo ""
echo "=========================================================="
echo "  AUTOMATED SETUP COMPLETED!                              "
echo "=========================================================="
echo ""
echo "IMPORTANT: Complete these 3 console-only steps in Firebase Console:"
echo ""
echo "1. Enable Google Sign-In Provider:"
echo "   -> URL: https://console.firebase.google.com/project/$PROJECT_ID/authentication/providers"
echo "   - Click 'Google', toggle 'Enable', select support email, and Save."
echo ""
echo "2. Create Cloud Firestore Database (Native Mode):"
echo "   -> URL: https://console.firebase.google.com/project/$PROJECT_ID/firestore"
echo "   - Click 'Create database', choose 'Production mode', select location, and click 'Create'."
echo ""
echo "3. (Optional) Enable Gemini Developer API / Firebase AI Logic:"
echo "   -> URL: https://console.firebase.google.com/project/$PROJECT_ID/extensions"
echo ""
echo "After step 1, re-download google-services.json from Firebase Console so OAuth client type 3 is populated."
echo "=========================================================="
