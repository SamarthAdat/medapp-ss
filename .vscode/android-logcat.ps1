# Tails logcat filtered to this app's process, so you are not drowned in system noise.
param(
    [string]$Package = "com.ss.medrecord"
)

$sdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk = $env:ANDROID_SDK_ROOT }
if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb.exe not found at $adb" }

$pidText = (& $adb shell pidof -s $Package)
$pidText = "$pidText".Trim()

if ([string]::IsNullOrWhiteSpace($pidText)) {
    Write-Host "$Package is not running - showing unfiltered logcat (Ctrl+C to stop)." -ForegroundColor Yellow
    & $adb logcat
} else {
    Write-Host "Tailing logcat for $Package (pid $pidText). Ctrl+C to stop." -ForegroundColor Cyan
    & $adb logcat "--pid=$pidText"
}
