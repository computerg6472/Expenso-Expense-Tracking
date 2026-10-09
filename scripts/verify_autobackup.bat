@echo off
setlocal
echo ==========================================================
echo  Expenso Auto Backup (25MB) Automated Diagnostics Engine
echo ==========================================================

set ADB=C:\Users\EIMS\AppData\Local\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" (
    where adb >nul 2>&1
    if %errorlevel% equ 0 (
        set ADB=adb
    ) else (
        echo [ERROR] ADB not found. Please install Android platform-tools.
        exit /b 1
    )
)

echo.
echo [Step 1/6] Checking connected devices...
"%ADB%" devices

echo.
echo [Step 2/6] Enabling Backup Manager and setting Transport...
"%ADB%" shell bmgr enable true
"%ADB%" shell bmgr list transports
"%ADB%" shell bmgr transport com.google.android.gms/.backup.BackupTransportService

echo.
echo [Step 3/6] Setting verbose diagnostic tags on device...
"%ADB%" shell setprop log.tag.GmsBackupTransport VERBOSE
"%ADB%" shell setprop log.tag.BackupXmlParserLogging VERBOSE
"%ADB%" shell setprop log.tag.BackupManagerService VERBOSE
"%ADB%" shell setprop log.tag.SpendWiseBackupAgent VERBOSE
"%ADB%" shell setprop log.tag.SpendWiseDatabase VERBOSE

echo.
echo [Step 4/6] Quiescing app and flushing SQLite WAL...
"%ADB%" shell am force-stop com.spendwise.app

echo.
echo [Step 5/6] Triggering immediate full backup sweep...
"%ADB%" shell bmgr run
"%ADB%" shell bmgr backupnow com.spendwise.app

echo.
echo [Step 6/6] Inspecting dumpsys and capturing service logs...
"%ADB%" shell dumpsys backup | findstr /i "Current com.spendwise.app Transport"
"%ADB%" logcat -d -s BackupManagerService GmsBackupTransport BackupXmlParserLogging SpendWiseBackupAgent SpendWiseDatabase AndroidRuntime

echo.
echo ==========================================================
echo  Verification Routine Completed
echo ==========================================================
endlocal
