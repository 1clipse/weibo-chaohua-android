param(
  [string]$DependencyRoot = "D:\codex-deps\weibo-chaohua-android",
  [string]$ApkPath = "D:\codex-outputs\weibo-chaohua-android\apk\weibo-chaohua-checkin-debug.apk",
  [string]$Serial = "",
  [switch]$GrantNotifications,
  [switch]$Launch,
  [switch]$OpenAccessibilitySettings,
  [switch]$OpenExactAlarmSettings,
  [switch]$OpenAppSettings,
  [switch]$OpenBatterySettings,
  [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
$PackageName = "com.codex.weibocheckin"
$LastAdbExit = 0

function Resolve-AdbPath {
  $candidates = @(
    (Join-Path $DependencyRoot "android-sdk\platform-tools\adb.exe")
  )

  if ($env:ANDROID_HOME) {
    $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
  }
  if ($env:ANDROID_SDK_ROOT) {
    $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
  }

  $pathAdb = Get-Command adb -ErrorAction SilentlyContinue
  if ($pathAdb) {
    $candidates += $pathAdb.Source
  }

  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path $candidate)) {
      return (Resolve-Path $candidate).Path
    }
  }

  throw "ADB not found. Checked D: dependency root, ANDROID_HOME, ANDROID_SDK_ROOT, and PATH."
}

$Adb = Resolve-AdbPath

function Invoke-Adb {
  param([string[]]$Arguments)
  $adbArgs = @()
  if ($Serial.Trim().Length -gt 0) {
    $adbArgs += @("-s", $Serial.Trim())
  }
  $adbArgs += $Arguments
  & $Adb @adbArgs
  $script:LastAdbExit = $LASTEXITCODE
}

function Invoke-AdbCapture {
  param([string[]]$Arguments)
  $adbArgs = @()
  if ($Serial.Trim().Length -gt 0) {
    $adbArgs += @("-s", $Serial.Trim())
  }
  $adbArgs += $Arguments
  $output = & $Adb @adbArgs 2>&1
  $script:LastAdbExit = $LASTEXITCODE
  return $output
}

function Get-ConnectedDeviceSerials {
  $devices = & $Adb devices
  $script:LastAdbExit = $LASTEXITCODE
  $devices | ForEach-Object { Write-Host $_ }
  if ($LastAdbExit -ne 0) {
    return @()
  }
  return @(
    $devices |
      Where-Object { $_ -match "\sdevice$" } |
      ForEach-Object { ($_ -split "\s+")[0] }
  )
}

function Test-DeviceReady {
  if ($Serial.Trim().Length -gt 0) {
    Invoke-Adb @("get-state")
    return $LastAdbExit -eq 0
  }

  $serials = @(Get-ConnectedDeviceSerials)
  if ($LastAdbExit -ne 0) {
    return $false
  }
  if ($serials.Count -gt 1) {
    throw "Multiple Android devices are connected: $($serials -join ', '). Re-run with -Serial <device-serial>."
  }
  return $serials.Count -eq 1
}

function Get-AdbText {
  param([string[]]$Arguments)
  $output = Invoke-AdbCapture $Arguments
  return ($output | Out-String).Trim()
}

function Write-InstalledPackageSummary {
  Write-Host ""
  Write-Host "Installed package:"
  $pmPath = Get-AdbText @("shell", "pm", "path", $PackageName)
  if ($LastAdbExit -eq 0 -and $pmPath.Length -gt 0) {
    Write-Host "  APK path on device: $pmPath"
  } else {
    Write-Host "  WARN package path was not reported by Android."
  }

  $packageDump = Get-AdbText @("shell", "dumpsys", "package", $PackageName)
  if ($LastAdbExit -eq 0 -and $packageDump.Length -gt 0) {
    $versionName = (($packageDump -split "`n") | Where-Object { $_ -match "versionName=" } | Select-Object -First 1).Trim()
    $versionCode = (($packageDump -split "`n") | Where-Object { $_ -match "versionCode=" } | Select-Object -First 1).Trim()
    $debuggable = if ($packageDump -match "DEBUGGABLE") { "yes" } else { "not reported" }
    if ($versionName) { Write-Host "  $versionName" }
    if ($versionCode) { Write-Host "  $versionCode" }
    Write-Host "  debuggable: $debuggable"
  } else {
    Write-Host "  WARN package details were not reported by Android."
  }
}

function Test-AccessibilityServiceEnabled {
  param([string]$EnabledServices)
  $shortName = "$PackageName/.WeiboAccessibilityService"
  $fullName = "$PackageName/$PackageName.WeiboAccessibilityService"
  return $EnabledServices -match [regex]::Escape($shortName) -or
    $EnabledServices -match [regex]::Escape($fullName)
}

function Get-ReadinessLines {
  $lines = New-Object System.Collections.Generic.List[string]
  $sdk = Get-AdbText @("shell", "getprop", "ro.build.version.sdk")
  $release = Get-AdbText @("shell", "getprop", "ro.build.version.release")
  $enabledA11y = Get-AdbText @("shell", "settings", "get", "secure", "enabled_accessibility_services")
  $appOps = Get-AdbText @("shell", "cmd", "appops", "get", $PackageName)
  $packageDump = Get-AdbText @("shell", "dumpsys", "package", $PackageName)

  $lines.Add("Readiness summary:")
  if ($sdk) {
    $lines.Add("INFO Android $release / SDK $sdk")
  }
  if (Test-AccessibilityServiceEnabled $enabledA11y) {
    $lines.Add("OK accessibility service is enabled")
  } else {
    $lines.Add("WARN accessibility service is not enabled. Run: .\install-debug-apk.ps1 -OpenAccessibilitySettings")
  }
  if ($appOps -match "POST_NOTIFICATION:\s+allow" -or $packageDump -match "POST_NOTIFICATIONS:\s+granted=true") {
    $lines.Add("OK notification runtime permission is allowed; the app also checks app-wide notification and channel status at runtime")
  } else {
    $lines.Add("WARN notification permission may be disabled, or app-wide/channel notifications may be blocked. Run with -GrantNotifications or open App notification settings.")
  }
  if ($appOps -match "SCHEDULE_EXACT_ALARM:\s+allow") {
    $lines.Add("OK exact alarm is allowed")
  } else {
    $lines.Add("WARN exact alarm is not confirmed. This blocks daily scheduling. Run: .\install-debug-apk.ps1 -OpenExactAlarmSettings")
  }
  if ($appOps -match "RUN_ANY_IN_BACKGROUND:\s+allow") {
    $lines.Add("OK background app-op is allowed")
  } else {
    $lines.Add("WARN background app-op is not allowed or not reported. On Xiaomi/HyperOS also enable Autostart and unrestricted battery/background behavior.")
  }
  if ($appOps -match "ACCESS_RESTRICTED_SETTINGS:\s+allow") {
    $lines.Add("OK restricted settings app-op is allowed")
  } elseif ($appOps -match "ACCESS_RESTRICTED_SETTINGS") {
    $lines.Add("WARN restricted settings is blocked. If Accessibility cannot be enabled, open App info menu and allow restricted settings.")
  } else {
    $lines.Add("UNKNOWN restricted settings app-op is not reported by this Android version.")
  }
  return $lines
}

if (-not (Test-Path $ApkPath)) {
  throw "APK not found at $ApkPath. Run .\build-debug-apk.ps1 first."
}

$hash = Get-FileHash $ApkPath -Algorithm SHA256
Write-Host "APK: $ApkPath"
Write-Host "SHA256: $($hash.Hash)"
Write-Host "ADB: $Adb"

$deviceReady = Test-DeviceReady
if ($LastAdbExit -ne 0) {
  throw "adb devices failed"
}

if ($CheckOnly) {
  if ($deviceReady) {
    Write-Host "CheckOnly passed. Device is connected."
  } else {
    Write-Host "CheckOnly passed. No Android device is connected yet."
  }
  exit 0
}

if (-not $deviceReady) {
  throw "No Android device found. Connect the phone, enable USB debugging, accept the authorization dialog, then run again."
}

Write-Host "Installing APK..."
$installOutput = Invoke-AdbCapture @("install", "-r", $ApkPath)
$installOutput | ForEach-Object { Write-Host $_ }
if ($LastAdbExit -ne 0) {
  $installText = ($installOutput | Out-String)
  if ($installText -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match|UPDATE_INCOMPATIBLE") {
    throw "adb install failed because an existing APK has a different signature. Uninstall the old app on the phone, then install this debug APK again. Package: $PackageName"
  }
  if ($installText -match "INSTALL_FAILED_VERSION_DOWNGRADE") {
    throw "adb install failed because Android rejected a version downgrade. Uninstall the existing app first, or build with a higher versionCode."
  }
  throw "adb install failed"
}

if ($GrantNotifications) {
  Write-Host "Trying to grant POST_NOTIFICATIONS..."
  Invoke-Adb @("shell", "pm", "grant", $PackageName, "android.permission.POST_NOTIFICATIONS")
  if ($LastAdbExit -ne 0) {
    Write-Host "Notification permission was not granted by adb. Open it manually in Android settings if needed."
  }
}

if ($Launch) {
  Write-Host "Launching app..."
  Invoke-Adb @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1")
  if ($LastAdbExit -ne 0) {
    throw "adb launch failed"
  }
}

if ($OpenAccessibilitySettings) {
  Write-Host "Opening Accessibility settings..."
  Invoke-Adb @("shell", "am", "start", "-a", "android.settings.ACCESSIBILITY_SETTINGS")
  if ($LastAdbExit -ne 0) {
    Write-Host "Could not open Accessibility settings automatically. Open it manually on the phone."
  }
}

if ($OpenExactAlarmSettings) {
  Write-Host "Opening exact alarm settings..."
  Invoke-Adb @("shell", "am", "start", "-a", "android.settings.REQUEST_SCHEDULE_EXACT_ALARM", "-d", "package:$($PackageName)")
  if ($LastAdbExit -ne 0) {
    Write-Host "Could not open exact alarm settings automatically. Open it manually on the phone."
  }
}

if ($OpenAppSettings) {
  Write-Host "Opening app settings..."
  Invoke-Adb @("shell", "am", "start", "-a", "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:$($PackageName)")
  if ($LastAdbExit -ne 0) {
    Write-Host "Could not open app settings automatically. Open it manually on the phone."
  }
}

if ($OpenBatterySettings) {
  Write-Host "Opening battery optimization settings..."
  Invoke-Adb @("shell", "am", "start", "-a", "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
  if ($LastAdbExit -ne 0) {
    Write-Host "Could not open battery optimization settings automatically. Open it manually on the phone."
  }
}

Write-InstalledPackageSummary
$readinessLines = Get-ReadinessLines
Write-Host ""
foreach ($line in $readinessLines) {
  Write-Host $line
}
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Open the app once and confirm the daily switch only stays on after exact alarm permission is allowed."
Write-Host "  2. Enable Accessibility for this app. If Android blocks it, open App info menu and allow restricted settings."
Write-Host "  3. On Xiaomi/HyperOS, also allow Autostart and unrestricted battery/background behavior."
Write-Host "  4. For a full export, run: .\collect-device-diagnostics.ps1 -Screenshot"

Write-Host "Install finished."
