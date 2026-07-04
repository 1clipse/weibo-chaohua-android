param(
  [switch]$Build
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Push-Location $ProjectRoot
try {
  Write-Host "Checking PowerShell scripts..."
  $scripts = Get-ChildItem -Path $ProjectRoot -Filter *.ps1 -File
  foreach ($script in $scripts) {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -gt 0) {
      Write-Host "Script parse failed: $($script.Name)"
      $errors | ForEach-Object { Write-Host $_.Message }
      throw "PowerShell script syntax check failed"
    }
    Write-Host "OK $($script.Name)"
  }

  if ($Build) {
    Write-Host "Building debug APK..."
    powershell -ExecutionPolicy Bypass -File .\build-debug-apk.ps1
    if ($LASTEXITCODE -ne 0) {
      throw "build-debug-apk.ps1 failed"
    }
  }

  Write-Host "Checking install script..."
  powershell -ExecutionPolicy Bypass -File .\install-debug-apk.ps1 -CheckOnly
  if ($LASTEXITCODE -ne 0) {
    throw "install-debug-apk.ps1 -CheckOnly failed"
  }

  Write-Host "Checking diagnostics script..."
  powershell -ExecutionPolicy Bypass -File .\collect-device-diagnostics.ps1 -CheckOnly
  if ($LASTEXITCODE -ne 0) {
    throw "collect-device-diagnostics.ps1 -CheckOnly failed"
  }

  Write-Host "Checking APK delivery hashes..."
  powershell -ExecutionPolicy Bypass -File .\verify-local-delivery.ps1
  if ($LASTEXITCODE -ne 0) {
    throw "verify-local-delivery.ps1 failed"
  }

  Write-Host "Local delivery validation passed."
} finally {
  Pop-Location
}
