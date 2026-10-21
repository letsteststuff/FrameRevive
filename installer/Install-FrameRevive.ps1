[CmdletBinding()]
param(
    [string]$AdbPath,
    [string]$ApkPath = (Join-Path $PSScriptRoot 'FrameRevive.apk'),
    [string]$Serial,
    [switch]$ConfigureWifi,
    [switch]$NoBrowser
)
$ErrorActionPreference = 'Stop'
if (-not $AdbPath) {
    $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    $nearbyAdb = Join-Path $PSScriptRoot 'platform-tools\adb.exe'
    if (Test-Path -LiteralPath $nearbyAdb) { $AdbPath = $nearbyAdb }
    elseif (Test-Path -LiteralPath $sdkAdb) { $AdbPath = $sdkAdb }
    else { $command = Get-Command adb -ErrorAction SilentlyContinue; if ($command) { $AdbPath = $command.Source } }
}
if (-not $AdbPath -or -not (Test-Path -LiteralPath $AdbPath)) {
    throw 'ADB was not found. Extract Android SDK Platform-Tools beside this script or pass -AdbPath C:\path\adb.exe.'
}
if (-not (Test-Path -LiteralPath $ApkPath)) { throw 'FrameRevive.apk is missing. Keep the APK and installer together.' }
$devices = @(& $AdbPath devices)
if ($LASTEXITCODE -ne 0) { throw 'ADB could not start.' }
$ready = @($devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($Serial) { if ($Serial -notin $ready) { throw 'The requested frame is not connected and authorized.' } }
elseif ($ready.Count -eq 1) { $Serial = $ready[0] }
elseif ($ready.Count -gt 1) { throw 'More than one Android device is connected. Disconnect the others or specify -Serial.' }
else { throw 'Connect the frame by USB, enable USB debugging, and accept Always allow from this computer.' }
function Invoke-FrameAdb {
    $frameArguments = $args
    $result = & $AdbPath -s $Serial @frameArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($result -join [Environment]::NewLine)" }
    return $result
}
$model = (Invoke-FrameAdb shell getprop ro.product.model) -join ''
$android = (Invoke-FrameAdb shell getprop ro.build.version.release) -join ''
Write-Host "Connected: $model (Android $android)"
if ($android -ne '10') { throw 'This installer is validated for Android 10 only.' }
$hasLenovo = (Invoke-FrameAdb shell pm path com.walnut.bluetooth) -join ''
if (-not $hasLenovo.StartsWith('package:')) { throw 'Expected Lenovo Smart Frame software not found. No changes made.' }
if ($ConfigureWifi) {
    Invoke-FrameAdb shell am start -a android.settings.WIFI_SETTINGS | Out-Host
    Read-Host 'Connect the frame to home Wi-Fi in Android settings, then press Enter' | Out-Null
}
$backupFolder = Join-Path $PSScriptRoot 'local'
New-Item -ItemType Directory -Force -Path $backupFolder | Out-Null
$backupPath = Join-Path $backupFolder 'original-launcher.txt'
if (-not (Test-Path -LiteralPath $backupPath)) {
    (Invoke-FrameAdb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME) | Set-Content -LiteralPath $backupPath
}
Write-Host 'Installing FrameRevive. Existing FrameRevive settings are preserved.'
Invoke-FrameAdb install -r $ApkPath | Out-Host
Invoke-FrameAdb shell am start -n com.framerevive.app/.MainActivity | Out-Host
# Read only the random management key, never Google session data.
$key = $null
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    try {
        $xmlText = (Invoke-FrameAdb shell run-as com.framerevive.app cat shared_prefs/frame-settings.xml) -join "`n"
        [xml]$settings = $xmlText
        $node = $settings.map.string | Where-Object { $_.name -eq 'managementKey' }
        if ($node) { $key = $node.InnerText; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (-not $key -or $key -notmatch '^[a-f0-9]{32}$') { throw 'App installed but setup did not initialize. Lenovo software has not been disabled.' }
$policy = (Invoke-FrameAdb shell dumpsys device_policy) -join "`n"
if ($policy -notmatch 'com\.framerevive\.app.*FrameAdminReceiver') {
    Invoke-FrameAdb shell dpm set-active-admin com.framerevive.app/.FrameAdminReceiver | Out-Host
}
Invoke-FrameAdb shell cmd package set-home-activity com.framerevive.app/.MainActivity | Out-Host
Invoke-FrameAdb shell am force-stop com.walnut.bluetooth | Out-Null
Invoke-FrameAdb shell pm disable-user --user 0 com.walnut.bluetooth | Out-Host
Invoke-FrameAdb shell am start -n com.framerevive.app/.MainActivity | Out-Host
$ipText = (Invoke-FrameAdb shell ip -f inet addr show wlan0) -join ' '
$ipMatch = [regex]::Match($ipText, 'inet\s+(\d+\.\d+\.\d+\.\d+)/')
$networkUrl = if ($ipMatch.Success) { "http://$($ipMatch.Groups[1].Value):8765/?key=$key" } else { $null }
Invoke-FrameAdb forward tcp:18765 tcp:8765 | Out-Null
$usbUrl = "http://127.0.0.1:18765/?key=$key"
$urls = @('Keep these links private. They control this frame.', "USB setup (while connected): $usbUrl")
if ($networkUrl) { $urls += "Home Wi-Fi (bookmark this): $networkUrl" }
$urls | Set-Content -LiteralPath (Join-Path $backupFolder 'frame-controls.txt')
Write-Host ''
Write-Host 'Installed. Import YOUR Google Photos session, then choose YOUR album.'
$urls | ForEach-Object { Write-Host $_ }
if (-not $networkUrl) { Write-Warning 'Connect the frame to Wi-Fi before importing Google Photos.' }
if (-not $NoBrowser) { Start-Process $usbUrl }
