param(
  [string]$OutputApk = "D:\codex-outputs\weibo-chaohua-android\apk\weibo-chaohua-checkin-debug.apk",
  [string]$RepoApk = "releases\weibo-chaohua-checkin-debug.apk",
  [string]$ReadmePath = "README.md"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoApkPath = if ([System.IO.Path]::IsPathRooted($RepoApk)) {
  $RepoApk
} else {
  Join-Path $ProjectRoot $RepoApk
}
$ReadmeFullPath = if ([System.IO.Path]::IsPathRooted($ReadmePath)) {
  $ReadmePath
} else {
  Join-Path $ProjectRoot $ReadmePath
}

if (-not (Test-Path $OutputApk)) {
  throw "Output APK not found: $OutputApk"
}
if (-not (Test-Path $RepoApkPath)) {
  throw "Repository APK copy not found: $RepoApkPath"
}
if (-not (Test-Path $ReadmeFullPath)) {
  throw "README not found: $ReadmeFullPath"
}

$outputHash = (Get-FileHash $OutputApk -Algorithm SHA256).Hash
$repoHash = (Get-FileHash $RepoApkPath -Algorithm SHA256).Hash
$readme = Get-Content -Encoding UTF8 $ReadmeFullPath -Raw

Write-Host "Output APK SHA256: $outputHash"
Write-Host "Repo APK SHA256:   $repoHash"
Write-Host "Output APK:        $OutputApk"
Write-Host "Repo APK copy:     $RepoApkPath"
Write-Host "README:            $ReadmeFullPath"

if ($outputHash -ne $repoHash) {
  throw "APK hash mismatch. Copy $OutputApk to $RepoApkPath before delivery."
}

if ($readme -notmatch [regex]::Escape($outputHash)) {
  throw "README does not contain current APK SHA256: $outputHash"
}

Write-Host "Delivery verification passed."
Write-Host "Ready-to-hand-off summary:"
Write-Host "  APK path: $OutputApk"
Write-Host "  SHA256:   $outputHash"
Write-Host "  Repo copy matches output APK: yes"
Write-Host "  README contains current SHA256: yes"
Write-Host "  Device is not required for this hash/readme validation."
