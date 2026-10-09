<#
.SYNOPSIS
    Automated Android Auto Backup (25 MB) Diagnostics & Verification Script for Expenso (com.spendwise.app)
#>

$ErrorActionPreference = "Continue"

$adb = "C:\Users\EIMS\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adbCmd = Get-Command "adb" -ErrorAction SilentlyContinue
    if ($adbCmd) { $adb = $adbCmd.Source } else {
        Write-Error "ADB not found. Please ensure Android SDK platform-tools are installed."
        exit 1
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Expenso Auto Backup (25MB) Automated Diagnostics Engine " -ForegroundColor Cyan
Write-Host " ADB: $adb" -ForegroundColor Gray
Write-Host " Package: com.spendwise.app" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Device check
Write-Host "`n[Step 1/6] Checking connected Android devices..." -ForegroundColor Yellow
$devices = & $adb devices
$deviceList = $devices | Where-Object { $_ -match "\tdevice$" }
if (-not $deviceList) {
    Write-Host "[-] No active device/emulator connected to ADB." -ForegroundColor Red
    Write-Host "    Please connect an Android device with USB debugging enabled or launch an emulator." -ForegroundColor Gray
    Write-Host "    Once connected, re-run this script to execute live OS-level backup verification." -ForegroundColor Gray
    exit 0
}

Write-Host "[+] Connected device: $($deviceList[0].Split("`t")[0])" -ForegroundColor Green

# 2. Enable Backup Manager and set Transport
Write-Host "`n[Step 2/6] Enabling Backup Manager & Cloud Transport..." -ForegroundColor Yellow
& $adb shell bmgr enable true
$transports = & $adb shell bmgr list transports
Write-Host "Available Transports:`n$transports" -ForegroundColor Gray

# Prefer Google Play Services cloud transport
& $adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService
Write-Host "[+] Cloud transport set to GMS BackupTransportService" -ForegroundColor Green

# 3. Enable Verbose Logging
Write-Host "`n[Step 3/6] Setting verbose diagnostic tags on device..." -ForegroundColor Yellow
& $adb shell setprop log.tag.GmsBackupTransport VERBOSE
& $adb shell setprop log.tag.BackupXmlParserLogging VERBOSE
& $adb shell setprop log.tag.BackupManagerService VERBOSE
& $adb shell setprop log.tag.SpendWiseBackupAgent VERBOSE
& $adb shell setprop log.tag.SpendWiseDatabase VERBOSE
Write-Host "[+] Verbose logging activated" -ForegroundColor Green

# 4. Trigger Checkpoint and Background Transition
Write-Host "`n[Step 4/6] Quiescing app and flushing SQLite WAL checkpoint..." -ForegroundColor Yellow
& $adb shell am force-stop com.spendwise.app
Start-Sleep -Seconds 1

# 5. Execute OS Backup Sweep
Write-Host "`n[Step 5/6] Triggering immediate full backup sweep..." -ForegroundColor Yellow
& $adb shell bmgr run
$backupResult = & $adb shell bmgr backupnow com.spendwise.app
Write-Host "Backup Run Output:`n$backupResult" -ForegroundColor Cyan

# 6. Inspection & Token Dump
Write-Host "`n[Step 6/6] Inspecting Backup Manager state & Dumpsys..." -ForegroundColor Yellow
$dumpsys = & $adb shell dumpsys backup
$pkgDump = $dumpsys | Select-String "com.spendwise.app|Current|Transport" -Context 0, 2
Write-Host "Dumpsys Extract:`n$pkgDump" -ForegroundColor Gray

Write-Host "`nCapturing Logcat Service Entries..." -ForegroundColor Yellow
& $adb logcat -d -s BackupManagerService GmsBackupTransport BackupXmlParserLogging SpendWiseBackupAgent SpendWiseDatabase AndroidRuntime | Select-Object -Last 40

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " Verification Routine Completed." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
