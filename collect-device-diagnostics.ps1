param(
  [string]$DependencyRoot = "D:\codex-deps\weibo-chaohua-android",
  [string]$OutputRoot = "D:\codex-outputs\weibo-chaohua-android\diagnostics",
  [string]$Serial = "",
  [switch]$Screenshot,
  [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"
$PackageName = "com.codex.weibocheckin"
$PrefsFile = "/data/user/0/$PackageName/shared_prefs/weibo_checkin_prefs.xml"
$LastAdbExit = 0
$Warnings = New-Object System.Collections.Generic.List[string]

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

function Test-AccessibilityServiceEnabled {
  param([string]$EnabledServices)
  $shortName = "$PackageName/.WeiboAccessibilityService"
  $fullName = "$PackageName/$PackageName.WeiboAccessibilityService"
  return $EnabledServices -match [regex]::Escape($shortName) -or
    $EnabledServices -match [regex]::Escape($fullName)
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

function Invoke-AdbBinaryToFile {
  param(
    [string[]]$Arguments,
    [string]$OutFile
  )

  $adbArgs = @()
  if ($Serial.Trim().Length -gt 0) {
    $adbArgs += @("-s", $Serial.Trim())
  }
  $adbArgs += $Arguments

  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $Adb
  $escapedArgs = $adbArgs | ForEach-Object {
    if ($_ -match '[\s"]') {
      '"' + ($_ -replace '"', '\"') + '"'
    } else {
      $_
    }
  }
  $psi.Arguments = ($escapedArgs -join " ")
  $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true

  $process = [System.Diagnostics.Process]::Start($psi)
  $fileStream = [System.IO.File]::Open($OutFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
  try {
    $process.StandardOutput.BaseStream.CopyTo($fileStream)
  } finally {
    $fileStream.Dispose()
  }
  $stderr = $process.StandardError.ReadToEnd()
  $process.WaitForExit()
  $script:LastAdbExit = $process.ExitCode
  if ($script:LastAdbExit -ne 0 -and $stderr.Trim().Length -gt 0) {
    Write-Host $stderr.Trim()
  }
}

$deviceReady = Test-DeviceReady
if ($LastAdbExit -ne 0) {
  throw "adb devices failed"
}

if ($CheckOnly) {
  Write-Host "ADB: $Adb"
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

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$prefsOut = Join-Path $OutputRoot "weibo-checkin-prefs-$stamp.xml"
$screenOut = Join-Path $OutputRoot "weibo-checkin-screen-$stamp.png"
$deviceOut = Join-Path $OutputRoot "weibo-checkin-device-$stamp.txt"

Write-Host "Collecting device and package status..."
$statusLines = New-Object System.Collections.Generic.List[string]
$statusLines.Add("Collected at: $(Get-Date -Format o)")
$statusLines.Add("Package: $PackageName")
$statusLines.Add("")
$statusLines.Add("adb get-state:")
$stateArgs = @()
if ($Serial.Trim().Length -gt 0) {
  $stateArgs += @("-s", $Serial.Trim())
}
$stateArgs += @("get-state")
$state = & $Adb @stateArgs
$statusLines.Add(($state | Out-String).Trim())
$statusLines.Add("")
$statusLines.Add("package path:")
$pathArgs = @()
if ($Serial.Trim().Length -gt 0) {
  $pathArgs += @("-s", $Serial.Trim())
}
$pathArgs += @("shell", "pm", "path", $PackageName)
$packagePath = & $Adb @pathArgs
$statusLines.Add(($packagePath | Out-String).Trim())
$statusLines.Add("")
$statusLines.Add("enabled accessibility services:")
$a11yArgs = @()
if ($Serial.Trim().Length -gt 0) {
  $a11yArgs += @("-s", $Serial.Trim())
}
$a11yArgs += @("shell", "settings", "get", "secure", "enabled_accessibility_services")
$enabledA11y = & $Adb @a11yArgs
$statusLines.Add(($enabledA11y | Out-String).Trim())
$statusLines.Add("")
$statusLines.Add("app ops:")
$appOpsArgs = @()
if ($Serial.Trim().Length -gt 0) {
  $appOpsArgs += @("-s", $Serial.Trim())
}
$appOpsArgs += @("shell", "cmd", "appops", "get", $PackageName)
$appOps = & $Adb @appOpsArgs
$statusLines.Add(($appOps | Out-String).Trim())
$statusLines.Add("")
$statusLines.Add("package permissions:")
$permArgs = @()
if ($Serial.Trim().Length -gt 0) {
  $permArgs += @("-s", $Serial.Trim())
}
$permArgs += @("shell", "dumpsys", "package", $PackageName)
$packageDump = & $Adb @permArgs
$statusLines.Add(
  (($packageDump | Select-String -Pattern "versionName|versionCode|POST_NOTIFICATIONS|SCHEDULE_EXACT_ALARM|RECEIVE_BOOT_COMPLETED|WAKE_LOCK|granted=true|granted=false") | Out-String).Trim()
)
$a11yText = ($enabledA11y | Out-String).Trim()
$appOpsText = ($appOps | Out-String)
$permissionText = ($packageDump | Out-String)
$readinessLines = New-Object System.Collections.Generic.List[string]
$readinessLines.Add("Readiness summary:")
if (Test-AccessibilityServiceEnabled $a11yText) {
  $readinessLines.Add("OK accessibility service is enabled")
} else {
  $readinessLines.Add("WARN accessibility service is not enabled")
}
if ($appOpsText -match "POST_NOTIFICATION:\s+allow" -or $permissionText -match "POST_NOTIFICATIONS:\s+granted=true") {
  $readinessLines.Add("OK notification runtime permission is allowed; the app also checks app-wide notification and channel status at runtime")
} else {
  $readinessLines.Add("WARN notification permission may be disabled, or app-wide/channel notifications may be blocked")
}
if ($appOpsText -match "SCHEDULE_EXACT_ALARM:\s+allow") {
  $readinessLines.Add("OK exact alarm is allowed")
} else {
  $readinessLines.Add("WARN exact alarm is not confirmed as allowed; daily scheduling is blocked until enabled. Run: .\install-debug-apk.ps1 -OpenExactAlarmSettings")
}
if ($appOpsText -match "RUN_ANY_IN_BACKGROUND:\s+allow") {
  $readinessLines.Add("OK background app-op is allowed; OEM autostart/battery settings may still need manual confirmation")
} else {
  $readinessLines.Add("WARN background app-op is not allowed or not reported; on Xiaomi/HyperOS enable Autostart and unrestricted battery/background behavior")
}
if ($appOpsText -match "ACCESS_RESTRICTED_SETTINGS:\s+allow") {
  $readinessLines.Add("OK restricted settings app-op is allowed")
} elseif ($appOpsText -match "ACCESS_RESTRICTED_SETTINGS") {
  $readinessLines.Add("WARN restricted settings app-op is not allowed; if Accessibility cannot be enabled, open App info menu and allow restricted settings")
} else {
  $readinessLines.Add("UNKNOWN restricted settings app-op is not reported by this Android version")
}
$statusLines.InsertRange(3, [string[]]$readinessLines)
$statusLines.Insert(3 + $readinessLines.Count, "")
$statusLines | Set-Content -Encoding UTF8 $deviceOut
Write-Host "Device status exported to: $deviceOut"
foreach ($line in $readinessLines) {
  Write-Host $line
}

Write-Host "Exporting app preferences..."
$prefs = Invoke-AdbCapture @("shell", "run-as", $PackageName, "cat", $PrefsFile)
if ($LastAdbExit -ne 0 -or -not $prefs) {
  $prefsText = ($prefs | Out-String).Trim()
  $reason = "Could not read app preferences. Make sure the debug APK is installed, opened at least once, and still signed as debug."
  if ($prefsText -match "Package .* is unknown|unknown package") {
    $reason = "Could not read app preferences because the package is not installed."
  } elseif ($prefsText -match "run-as: package not debuggable|not debuggable") {
    $reason = "Could not read app preferences because the installed APK is not debuggable. Install the local debug APK."
  } elseif ($prefsText -match "No such file|not found") {
    $reason = "Could not read app preferences because the app has not created prefs yet. Open the app once, then retry."
  }
  if ($prefsText.Length -gt 0) {
    $reason = "$reason Raw output: $prefsText"
  }
  $Warnings.Add($reason)
  Add-Content -Encoding UTF8 $deviceOut ""
  Add-Content -Encoding UTF8 $deviceOut "warnings:"
  Add-Content -Encoding UTF8 $deviceOut $reason
  Write-Host "WARN $reason"
} else {
  $prefs | Set-Content -Encoding UTF8 $prefsOut
  Write-Host "Preferences exported to: $prefsOut"
}

if ($Screenshot) {
  Write-Host "Capturing screenshot..."
  Invoke-AdbBinaryToFile -Arguments @("exec-out", "screencap", "-p") -OutFile $screenOut
  if ($LastAdbExit -ne 0 -or -not (Test-Path $screenOut) -or (Get-Item $screenOut).Length -eq 0) {
    throw "Could not capture screenshot"
  }
  Write-Host "Screenshot exported to: $screenOut"
}

if ($Warnings.Count -gt 0) {
  Write-Host "Diagnostics export finished with warnings."
  foreach ($warning in $Warnings) {
    Write-Host "WARN $warning"
  }
  exit 2
}

Write-Host "Diagnostics export finished."
