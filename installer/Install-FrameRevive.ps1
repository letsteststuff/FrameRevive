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
$certificateBase64 = $null
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    try {
        $xmlText = (Invoke-FrameAdb shell run-as com.framerevive.app cat shared_prefs/frame-settings.xml) -join "`n"
        [xml]$settings = $xmlText
        $node = $settings.map.string | Where-Object { $_.name -eq 'managementKey' }
        $secure = $settings.map.boolean | Where-Object { $_.name -eq 'httpsManagement' -and $_.value -eq 'true' }
        if ($node -and $secure) {
            # An upgrade already has an HTTP key; wait for rotation AND the new device identity.
            $candidateCertificate = (Invoke-FrameAdb shell run-as com.framerevive.app base64 files/management-cert.der) -join ''
            if ([Convert]::FromBase64String($candidateCertificate).Length -gt 0) {
                $key = $node.InnerText; $certificateBase64 = $candidateCertificate; break
            }
        }
    } catch { }
    Start-Sleep -Seconds 1
}
if (-not $key -or $key -notmatch '^[a-f0-9]{32}$') { throw 'App installed but setup did not initialize. Lenovo software has not been disabled.' }
# Pair the device identity through the already-authorized USB channel, never a downloaded HTTP certificate.
$certificateBytes = [Convert]::FromBase64String($certificateBase64)
$certificatePath = Join-Path $backupFolder 'frame-management.cer'
[IO.File]::WriteAllBytes($certificatePath, $certificateBytes)
$certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2 -ArgumentList @(,$certificateBytes)
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try { $fingerprint = [BitConverter]::ToString($sha256.ComputeHash($certificateBytes)).Replace('-', ':') }
finally { $sha256.Dispose() }
Write-Host "Frame certificate SHA-256 (obtained over authorized USB): $fingerprint"
Write-Host 'This device uses its own HTTPS certificate. Compare this fingerprint with the one shown on the frame.'
$trustReply = Read-Host 'Type TRUST to add this device certificate to your Windows user trust store, or press Enter to pair manually'
if ($trustReply -ceq 'TRUST') {
    Import-Certificate -FilePath $certificatePath -CertStoreLocation Cert:\CurrentUser\Root | Out-Null
    Write-Host "Trusted device certificate. Remove later with: Remove-Item 'Cert:\CurrentUser\Root\$($certificate.Thumbprint)'"
}
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
$hostnameNode = $settings.map.string | Where-Object { $_.name -eq 'localHostname' }
$hostname = if ($hostnameNode) { $hostnameNode.InnerText } else { $null }
if ($hostname -and $hostname -notmatch '^framerevive-[a-f0-9]{8}\.local$') { throw 'Unexpected frame hostname.' }
$networkUrl = if ($hostname) { "https://${hostname}:8765/?key=$key" } elseif ($ipMatch.Success) { "https://$($ipMatch.Groups[1].Value):8765/?key=$key" } else { $null }
Invoke-FrameAdb forward tcp:18765 tcp:8765 | Out-Null
$usbUrl = "https://127.0.0.1:18765/?key=$key"
$urls = @('Keep these links private. They control this frame.', "USB setup (while connected): $usbUrl")
if ($networkUrl) { $urls += "Home Wi-Fi (bookmark this): $networkUrl" }
$urls += "Certificate SHA-256: $fingerprint"
$urls += 'Replace old HTTP bookmarks. On another browser/device, verify this fingerprint before trusting its first-time certificate.'
$urls | Set-Content -LiteralPath (Join-Path $backupFolder 'frame-controls.txt')
Write-Host ''
Write-Host 'Installed. Open Setup and connect YOUR shared Google Photos album link (no sign-in or cookies needed).'
$urls | ForEach-Object { Write-Host $_ }
if (-not $networkUrl) { Write-Warning 'Connect the frame to Wi-Fi before importing Google Photos.' }
if (-not $NoBrowser) { Start-Process $usbUrl }
