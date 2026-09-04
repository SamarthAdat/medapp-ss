# Starts an Android emulator and blocks until it has finished booting.
# Used by the "Emulator: Start" task so that "Run App" can depend on a ready device.
param(
    [string]$Avd = "",
    [switch]$ColdBoot
)

$ErrorActionPreference = "Stop"

$sdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk = $env:ANDROID_SDK_ROOT }
if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }

$emulator = Join-Path $sdk "emulator\emulator.exe"
$adb = Join-Path $sdk "platform-tools\adb.exe"

if (-not (Test-Path $emulator)) { throw "emulator.exe not found at $emulator" }
if (-not (Test-Path $adb)) { throw "adb.exe not found at $adb" }

# Already have a booted device? Nothing to do.
$booted = & $adb devices | Select-String -Pattern "^emulator-\d+\s+device$"
if ($booted) {
    Write-Host "An emulator is already running:" -ForegroundColor Green
    & $adb devices
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Avd)) {
    $avds = @(& $emulator -list-avds | Where-Object { $_.Trim() -ne "" })
    if ($avds.Count -eq 0) {
        throw "No AVDs found. Create one with Android Studio's Device Manager, or: avdmanager create avd -n Pixel -k `"system-images;android-35;google_apis;x86_64`""
    }
    $Avd = $avds[0].Trim()
}

Write-Host "Starting emulator: $Avd" -ForegroundColor Cyan
$args = @("-avd", $Avd)
if ($ColdBoot) { $args += "-no-snapshot-load" }
Start-Process -FilePath $emulator -ArgumentList $args -WindowStyle Normal

Write-Host "Waiting for device..." -ForegroundColor Cyan
& $adb wait-for-device

Write-Host "Waiting for boot to complete..." -ForegroundColor Cyan
for ($i = 0; $i -lt 180; $i++) {
    $state = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($state -eq "1") {
        & $adb shell input keyevent 82 2>$null | Out-Null   # dismiss lock screen
        Write-Host "Emulator '$Avd' is ready." -ForegroundColor Green
        & $adb devices
        exit 0
    }
    Start-Sleep -Seconds 2
}

throw "Emulator did not finish booting within 6 minutes."
