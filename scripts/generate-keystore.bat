@echo off
REM Generate SpendWise Release Keystore for Android 15+ APK Signing
REM This generates a 4096-bit RSA key valid for 25 years (10,000 days).

echo ========================================================
echo SpendWise Release Keystore Generator
echo ========================================================

set KEYSTORE_NAME=spendwise-release.jks
set KEY_ALIAS=spendwise_key
set VALIDITY_DAYS=10000

if exist "%KEYSTORE_NAME%" (
    echo [WARNING] %KEYSTORE_NAME% already exists!
    echo To regenerate, please back up and remove the existing file.
    pause
    exit /b 1
)

echo Generating 4096-bit RSA Keystore...
keytool -genkeypair -v ^
  -keystore %KEYSTORE_NAME% ^
  -alias %KEY_ALIAS% ^
  -keyalg RSA ^
  -keysize 4096 ^
  -validity %VALIDITY_DAYS% ^
  -storetype PKCS12 ^
  -dname "CN=SpendWise, OU=Mobile Security, O=SpendWise App, L=Bangalore, ST=Karnataka, C=IN" ^
  -storepass SpendWiseSecure2026! ^
  -keypass SpendWiseSecure2026!

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo SUCCESS: %KEYSTORE_NAME% generated!
    echo.
    echo Creating keystore.properties in project root...
    echo ========================================================
    
    (
        echo storePassword=SpendWiseSecure2026!
        echo keyPassword=SpendWiseSecure2026!
        echo keyAlias=%KEY_ALIAS%
        echo storeFile=scripts/%KEYSTORE_NAME%
    ) > ..\keystore.properties

    echo keystore.properties created successfully.
    echo Keep your keystore and passwords safe! You need them for all future updates.
) else (
    echo.
    echo [ERROR] Failed to generate keystore. Make sure 'keytool' (JDK) is in your PATH.
)

pause
