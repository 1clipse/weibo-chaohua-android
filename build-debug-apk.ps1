param(
  [string]$DependencyRoot = "D:\codex-deps\weibo-chaohua-android",
  [string]$OutputRoot = "D:\codex-outputs\weibo-chaohua-android"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$JdkHome = Join-Path $DependencyRoot "jdk\jdk-17.0.19+10"
$GradleUserHome = Join-Path $DependencyRoot "gradle-home"
$AndroidUserHome = Join-Path $DependencyRoot "android-home"
$AndroidSdkRoot = Join-Path $DependencyRoot "android-sdk"
$ApkOutDir = Join-Path $OutputRoot "apk"

if (-not (Test-Path (Join-Path $JdkHome "bin\java.exe"))) {
  throw "JDK 17 not found at $JdkHome"
}

New-Item -ItemType Directory -Force -Path $GradleUserHome, $AndroidUserHome, $ApkOutDir | Out-Null

$env:JAVA_HOME = $JdkHome
$env:GRADLE_USER_HOME = $GradleUserHome
$env:ANDROID_USER_HOME = $AndroidUserHome
$env:ANDROID_HOME = $AndroidSdkRoot
$env:ANDROID_SDK_ROOT = $AndroidSdkRoot

Push-Location $ProjectRoot
try {
  "sdk.dir=$($AndroidSdkRoot.Replace('\', '\\'))" | Set-Content -Encoding ASCII (Join-Path $ProjectRoot "local.properties")
  & .\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
  if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE"
  }

  $apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
  if (-not (Test-Path $apk)) {
    throw "APK was not created at $apk"
  }
  Copy-Item -Force $apk (Join-Path $ApkOutDir "weibo-chaohua-checkin-debug.apk")
  Write-Host "APK copied to: $(Join-Path $ApkOutDir "weibo-chaohua-checkin-debug.apk")"
}
finally {
  Pop-Location
}
