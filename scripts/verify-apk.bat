@echo off
REM Verification script for SpendWise Android 15 Release APK
REM Checks:
REM 1. APK Signature Scheme v2, v3, v4 verification
REM 2. 16 KB page-alignment check (critical for Android 15 devices)

echo ========================================================
echo SpendWise APK Verification (Android 15+ 16KB & Signatures)
echo ========================================================

set APK_PATH=..\app\build\outputs\apk\release\app-release.apk

if not "%~1"=="" set APK_PATH=%~1

if not exist "%APK_PATH%" (
    echo [ERROR] APK not found at %APK_PATH%
    echo Please provide the path to your APK: verify-apk.bat path\to\app-release.apk
    pause
    exit /b 1
)

echo [1/2] Verifying APK Signatures using apksigner...
call apksigner verify --verbose --print-certs "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] apksigner encountered an issue or is not in PATH.
)

echo.
echo [2/2] Checking 16 KB Page Alignment using zipalign (Android 15 requirement)...
call zipalign -c -P 16 -v 4 "%APK_PATH%"
if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] APK is properly 16KB aligned!
) else (
    echo [INFO] Standard 4-byte check:
    call zipalign -c -v 4 "%APK_PATH%"
)

echo.
echo Verification completed.
pause
