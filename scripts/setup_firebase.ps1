param(
    [string]$ProjectId = "",
    [switch]$SkipDeploy = $false,
    [switch]$SkipVerify = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Expenso / SpendWise - Firebase Automated Setup Script   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_ROOT = Split-Path -Parent $SCRIPT_DIR
Set-Location $PROJECT_ROOT

# 1. Prerequisite Checks
Write-Host "[1/8] Checking prerequisites..." -ForegroundColor Yellow

# Find Java keytool
$KeytoolCmd = "keytool"
if (-not (Get-Command "keytool" -ErrorAction SilentlyContinue)) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\keytool.exe")) {
        $KeytoolCmd = "$env:JAVA_HOME\bin\keytool.exe"
    } elseif (Test-Path "C:\Program Files\Java\jdk-24\bin\keytool.exe") {
        $KeytoolCmd = "C:\Program Files\Java\jdk-24\bin\keytool.exe"
    } else {
        Write-Error "Keytool (Java) not found. Please install JDK or set JAVA_HOME."
        exit 1
    }
}
Write-Host "  -> Keytool found: $KeytoolCmd" -ForegroundColor Green

# Find Firebase CLI
$FirebaseCmd = "firebase"
if (-not (Get-Command "firebase" -ErrorAction SilentlyContinue)) {
    if (Get-Command "npx" -ErrorAction SilentlyContinue) {
        Write-Host "  -> 'firebase' CLI not in PATH, using 'npx firebase-tools'..." -ForegroundColor Yellow
        function Run-Firebase {
            & npx firebase-tools @args
        }
    } else {
        Write-Error "Firebase CLI not found. Please run 'npm install -g firebase-tools' or install Node.js."
        exit 1
    }
} else {
    function Run-Firebase {
        & firebase @args
    }
}
Write-Host "  -> Firebase CLI is ready." -ForegroundColor Green

# 2. Check Firebase Login Status
Write-Host ""
Write-Host "[2/8] Checking Firebase authentication..." -ForegroundColor Yellow
try {
    Run-Firebase login --interactive
} catch {
    Write-Warning "Please complete Firebase login in your browser..."
}

# 3. Resolve Project ID
Write-Host ""
Write-Host "[3/8] Resolving Firebase Project ID..." -ForegroundColor Yellow
if ([string]::IsNullOrWhiteSpace($ProjectId)) {
    Write-Host "Available Firebase projects:" -ForegroundColor Gray
    Run-Firebase projects:list

    $ProjectId = Read-Host "Enter your Firebase Project ID (or type 'NEW' to create a new one)"
    if ($ProjectId -eq "NEW" -or $ProjectId -eq "new") {
        $NewId = Read-Host "Enter a unique project ID for the new project (e.g. expenso-app-123)"
        Run-Firebase projects:create $NewId --display-name "Expenso App"
        $ProjectId = $NewId
    }
}

if ([string]::IsNullOrWhiteSpace($ProjectId)) {
    Write-Error "Project ID is required to proceed."
    exit 1
}
Write-Host "  -> Using Firebase Project: $ProjectId" -ForegroundColor Green

# 4. Register Android App
Write-Host ""
Write-Host "[4/8] Registering Android app (com.spendwise.app)..." -ForegroundColor Yellow
$AppPackage = "com.spendwise.app"
$AppId = ""

# List existing apps to check if already registered
$appsJson = ""
try {
    $appsJson = Run-Firebase apps:list android --project $ProjectId --json
    $appsObj = $appsJson | ConvertFrom-Json
    foreach ($app in $appsObj.result) {
        if ($app.packageName -eq $AppPackage) {
            $AppId = $app.appId
            Write-Host "  -> App already registered with App ID: $AppId" -ForegroundColor Green
            break
        }
    }
} catch {
    Write-Warning "Could not list existing apps. Proceeding to create/register..."
}

if ([string]::IsNullOrWhiteSpace($AppId)) {
    try {
        $createOutput = Run-Firebase apps:create android $AppPackage --project $ProjectId --nickname "Expenso" --json
        $createObj = $createOutput | ConvertFrom-Json
        $AppId = $createObj.result.appId
        Write-Host "  -> Successfully registered new Android app: $AppId" -ForegroundColor Green
    } catch {
        Write-Warning "App registration warning (may already exist). Continuing..."
    }
}

# 5. Extract SHA Fingerprints from Keystores
Write-Host ""
Write-Host "[5/8] Extracting SHA-1 and SHA-256 certificate fingerprints..." -ForegroundColor Yellow

# Release Keystore
$ReleaseKeystore = Join-Path $PROJECT_ROOT "keystore\release.keystore"
if (-not (Test-Path $ReleaseKeystore)) {
    Write-Host "  -> Creating fixed release keystore at $ReleaseKeystore..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ReleaseKeystore) | Out-Null
    & $KeytoolCmd -genkeypair -v -keystore $ReleaseKeystore -alias expenso -keyalg RSA -keysize 2048 -validity 10000 -storepass expenso123 -keypass expenso123 -dname "CN=Expenso, OU=SpendWise, O=SpendWise, L=Bengaluru, ST=KA, C=IN"
}

$releaseOutput = & $KeytoolCmd -list -v -keystore $ReleaseKeystore -alias expenso -storepass expenso123
$ReleaseSha1 = ($releaseOutput | Select-String "SHA1:\s+([A-F0-9:]+)").Matches.Groups[1].Value.Trim()
$ReleaseSha256 = ($releaseOutput | Select-String "SHA256:\s+([A-F0-9:]+)").Matches.Groups[1].Value.Trim()
Write-Host "  -> Release SHA-1:   $ReleaseSha1" -ForegroundColor Cyan
Write-Host "  -> Release SHA-256: $ReleaseSha256" -ForegroundColor Cyan

# Debug Keystore
$DebugKeystore = "$env:USERPROFILE\.android\debug.keystore"
$DebugSha1 = ""
$DebugSha256 = ""
if (Test-Path $DebugKeystore) {
    $debugOutput = & $KeytoolCmd -list -v -keystore $DebugKeystore -alias androiddebugkey -storepass android
    $DebugSha1 = ($debugOutput | Select-String "SHA1:\s+([A-F0-9:]+)").Matches.Groups[1].Value.Trim()
    $DebugSha256 = ($debugOutput | Select-String "SHA256:\s+([A-F0-9:]+)").Matches.Groups[1].Value.Trim()
    Write-Host "  -> Debug SHA-1:     $DebugSha1" -ForegroundColor Cyan
    Write-Host "  -> Debug SHA-256:   $DebugSha256" -ForegroundColor Cyan
}

# 6. Register SHA Fingerprints in Firebase
Write-Host ""
Write-Host "[6/8] Registering SHA fingerprints with Firebase Project..." -ForegroundColor Yellow
if ($AppId) {
    if ($ReleaseSha1) {
        try { Run-Firebase apps:android:sha:create $AppId $ReleaseSha1 --project $ProjectId } catch { Write-Warning "Release SHA-1 may already be registered." }
    }
    if ($ReleaseSha256) {
        try { Run-Firebase apps:android:sha:create $AppId $ReleaseSha256 --project $ProjectId } catch { Write-Warning "Release SHA-256 may already be registered." }
    }
    if ($DebugSha1) {
        try { Run-Firebase apps:android:sha:create $AppId $DebugSha1 --project $ProjectId } catch { Write-Warning "Debug SHA-1 may already be registered." }
    }
    if ($DebugSha256) {
        try { Run-Firebase apps:android:sha:create $AppId $DebugSha256 --project $ProjectId } catch { Write-Warning "Debug SHA-256 may already be registered." }
    }
    Write-Host "  -> Fingerprints registered." -ForegroundColor Green
} else {
    Write-Warning "AppId was not detected automatically. Please ensure fingerprints are registered in Firebase Console."
}

# 7. Download google-services.json & Deploy Firestore Rules
Write-Host ""
Write-Host "[7/8] Downloading google-services.json & deploying Firestore configuration..." -ForegroundColor Yellow
$TargetJsonPath = Join-Path $PROJECT_ROOT "app\google-services.json"

if ($AppId) {
    try {
        Run-Firebase apps:sdkconfig android $AppId --out $TargetJsonPath --project $ProjectId
        Write-Host "  -> Downloaded google-services.json to app/google-services.json" -ForegroundColor Green
    } catch {
        Write-Warning "Could not download sdkconfig automatically. Please download manually from Firebase Console."
    }
}

if (-not $SkipDeploy) {
    try {
        Write-Host "  -> Deploying Firestore rules and indexes..." -ForegroundColor Yellow
        Run-Firebase deploy --only firestore:rules,firestore:indexes --project $ProjectId
        Write-Host "  -> Firestore rules deployed successfully." -ForegroundColor Green
    } catch {
        Write-Warning "Firestore deploy encountered an issue. Ensure Firestore database is created in Native mode."
    }
}

# 8. Gradle Verification Task
Write-Host ""
Write-Host "[8/8] Running Gradle verification task 'verifyGoogleServices'..." -ForegroundColor Yellow
if (-not $SkipVerify) {
    $gradlew = Join-Path $PROJECT_ROOT "gradlew.bat"
    & $gradlew verifyGoogleServices
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  -> [PASSED] verifyGoogleServices succeeded!" -ForegroundColor Green
    } else {
        Write-Warning "verifyGoogleServices failed. Please inspect errors above."
    }
}

# 9. Clear Instructions for Console-Only Steps
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  AUTOMATED SETUP COMPLETED!                              " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
Write-Host "IMPORTANT: Complete these 3 console-only steps in Firebase Console:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Enable Google Sign-In Provider:" -ForegroundColor Cyan
Write-Host "   -> URL: https://console.firebase.google.com/project/$ProjectId/authentication/providers"
Write-Host "   - Click 'Get started' if first time."
Write-Host "   - Select 'Google', toggle 'Enable', select project support email, and click 'Save'."
Write-Host ""
Write-Host "2. Create Cloud Firestore Database (Native Mode):" -ForegroundColor Cyan
Write-Host "   -> URL: https://console.firebase.google.com/project/$ProjectId/firestore"
Write-Host "   - Click 'Create database'."
Write-Host "   - Choose 'Production mode' and pick your nearest location (e.g. asia-south1 / us-central)."
Write-Host "   - Click 'Create'."
Write-Host ""
Write-Host "3. (Optional) Enable Gemini Developer API / Firebase AI Logic:" -ForegroundColor Cyan
Write-Host "   -> URL: https://console.firebase.google.com/project/$ProjectId/extensions"
Write-Host "   - If using cloud AI features, enable Vertex AI in Firebase or Gemini Developer API."
Write-Host ""
Write-Host "After completing step 1, re-download google-services.json if OAuth client type 3 wasn't present:"
Write-Host "  -> Project Settings -> Your Android App -> google-services.json"
Write-Host "==========================================================" -ForegroundColor Green
