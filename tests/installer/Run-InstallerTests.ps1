[CmdletBinding()]
param(
    [string]$InstallerPath = (Join-Path $PSScriptRoot '..\..\installer\Install-FrameRevive.ps1'),
    [switch]$KeepArtifacts
)
$ErrorActionPreference = 'Stop'
# Exercise PowerShell 7 hosts that promote nonzero native exits to errors.
$PSNativeCommandUseErrorActionPreference = $true
$InstallerPath = (Resolve-Path -LiteralPath $InstallerPath).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('FrameRevive-installer-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$oldState = $env:FRAMEREVIVE_TEST_STATE
$oldScenario = $env:FRAMEREVIVE_TEST_SCENARIO
$passed = 0
$completed = $false

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-TestInstaller([string]$Script, [hashtable]$Parameters) {
    # All choices stay within the fixture. Guard browser and trust-store calls.
    function Read-Host { param([string]$Prompt) return '' }
    function Import-Certificate { throw 'Test attempted to change certificate trust.' }
    function Start-Process { throw 'Test attempted to launch an external process.' }
    function Start-Sleep { param([int]$Seconds) }
    & $Script @Parameters
}

function Invoke-Scenario {
    param([string]$Name, [string]$ExpectedError, [switch]$Repeat, [switch]$ConfigureWifi,
          [switch]$ExplicitSerial, [switch]$MissingApk, [switch]$MissingAdb, [string]$RequestedSerial)
    $caseRoot = Join-Path $testRoot $Name
    New-Item -ItemType Directory -Path $caseRoot | Out-Null
    $script = Join-Path $caseRoot 'Install-FrameRevive.ps1'
    Copy-Item -LiteralPath $InstallerPath -Destination $script
    [IO.File]::WriteAllBytes((Join-Path $caseRoot 'certificate.cer'), $certificateBytes)
    $apk = Join-Path $caseRoot 'FrameRevive test.apk'
    if (-not $MissingApk) { [IO.File]::WriteAllBytes($apk, [byte[]](1, 2, 3)) }
    $env:FRAMEREVIVE_TEST_STATE = $caseRoot
    $env:FRAMEREVIVE_TEST_SCENARIO = $Name
    $parameters = @{ AdbPath = $fakeAdb; ApkPath = $apk; NoBrowser = $true }
    if ($MissingAdb) { $parameters.AdbPath = Join-Path $caseRoot 'missing-adb.exe' }
    if ($RequestedSerial) { $parameters.Serial = $RequestedSerial }
    if ($ExplicitSerial) { $parameters.Serial = 'TEST-FRAME' }
    if ($ConfigureWifi) { $parameters.ConfigureWifi = $true }
    $actualError = $null
    try { Invoke-TestInstaller $script $parameters *> (Join-Path $caseRoot 'installer-output.log') }
    catch { $actualError = $_.Exception.Message }
    $logPath = Join-Path $caseRoot 'calls.log'
    $calls = if (Test-Path -LiteralPath $logPath) { @(Get-Content -LiteralPath $logPath) } else { @() }
    if ($ExpectedError) {
        Assert-True ($null -ne $actualError -and $actualError -like "*$ExpectedError*") "${Name}: wrong failure: $actualError"
        if ($Name -ne 'final-launch-failure') {
            Assert-True (-not ($calls -match 'disable-user|set-home-activity|force-stop')) "${Name}: changed launcher after an earlier failure"
        }
        Assert-True (-not (Test-Path -LiteralPath (Join-Path $caseRoot 'local\frame-controls.txt'))) "${Name}: wrote success links after failure"
    } else {
        Assert-True ($null -eq $actualError) "${Name}: unexpected failure: $actualError"
        Assert-True (($calls -match 'shell\tam\tstart\t-n\tcom.framerevive.app/.MainActivity').Count -eq 2) "${Name}: both launches must run"
        Assert-True (($calls -match 'install\t-r\t').Count -eq 1) "${Name}: update must use install -r"
        Assert-True (-not ($calls -match 'uninstall|\tclear\t')) "${Name}: must preserve existing app data"
        Assert-True (($calls -match 'disable-user').Count -eq 1) "${Name}: missing Lenovo disable command"
        $callText = $calls -join "`n"
        $certificateRead = $callText.IndexOf("shell`trun-as`tcom.framerevive.app`tbase64")
        $launcherChange = $callText.IndexOf("shell`tcmd`tpackage`tset-home-activity")
        $lenovoDisable = $callText.IndexOf("shell`tpm`tdisable-user")
        Assert-True ($certificateRead -ge 0 -and $launcherChange -gt $certificateRead -and $lenovoDisable -gt $launcherChange) "${Name}: changed launcher before initialization or disabled Lenovo before selecting Home"
        $backup = Join-Path $caseRoot 'local\original-launcher.txt'
        Assert-True ((Get-Content -LiteralPath $backup -Raw).Trim() -eq 'com.walnut.bluetooth/.SplashActivity') "${Name}: incorrect launcher backup"
        $links = Get-Content -LiteralPath (Join-Path $caseRoot 'local\frame-controls.txt') -Raw
        Assert-True ($links.Contains('https://127.0.0.1:18765/?key=11111111111111111111111111111111')) "${Name}: missing USB controls link"
        Assert-True ($links.Contains('framerevive-11223344.local')) "${Name}: missing stable controls link"
        Assert-True ((Test-Path -LiteralPath (Join-Path $caseRoot 'local\frame-management.cer'))) "${Name}: missing public pairing certificate"
        if ($ConfigureWifi) {
            Assert-True (($calls -match 'android.settings.WIFI_SETTINGS').Count -eq 1) "${Name}: missing Wi-Fi settings launch"
        }
        if ($Name -eq 'delayed-initialization') {
            Assert-True ((Get-Content -LiteralPath (Join-Path $caseRoot 'settings-read-count.txt')) -eq '3') 'Initialization must tolerate two absent settings reads'
        }
        if ($Repeat) {
            $backupBytes = [Convert]::ToBase64String([IO.File]::ReadAllBytes($backup))
            Invoke-TestInstaller $script $parameters *>> (Join-Path $caseRoot 'installer-output.log')
            Assert-True ([Convert]::ToBase64String([IO.File]::ReadAllBytes($backup)) -eq $backupBytes) 'Repeat install replaced the original launcher backup'
            $repeatedCalls = @(Get-Content -LiteralPath $logPath)
            Assert-True (($repeatedCalls -match 'install\t-r\t').Count -eq 2) 'Repeat install must preserve app data'
            Assert-True (($repeatedCalls -match 'dpm\tset-active-admin').Count -eq 1) 'Repeat install re-added the existing administrator'
        }
    }
    Write-Host "PASS $Name"
    $script:passed++
}

try {
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (-not (Test-Path -LiteralPath $compiler)) { throw 'The .NET Framework C# compiler is required on Windows.' }
    $toolDirectory = Join-Path $testRoot 'ADB tools with spaces'
    New-Item -ItemType Directory -Path $toolDirectory | Out-Null
    $fakeAdb = Join-Path $toolDirectory 'fake-adb.exe'
    & $compiler /nologo /target:exe "/out:$fakeAdb" (Join-Path $PSScriptRoot 'FakeAdb.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Fake ADB compilation failed.' }
    $rsa = [Security.Cryptography.RSA]::Create()
    $rsa.KeySize = 2048
    try {
        $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=framerevive-11223344.local', $rsa,
            [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
        $certificate = $request.CreateSelfSigned([DateTimeOffset]::UtcNow.AddMinutes(-1), [DateTimeOffset]::UtcNow.AddDays(1))
        try { $certificateBytes = $certificate.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert) }
        finally { $certificate.Dispose() }
    } finally { $rsa.Dispose() }

    Invoke-Scenario 'fresh-install' -Repeat
    Invoke-Scenario 'launch-warnings'
    Invoke-Scenario 'delayed-initialization'
    Invoke-Scenario 'configure-wifi' -ConfigureWifi -ExplicitSerial
    Invoke-Scenario 'no-wifi'
    Invoke-Scenario 'multiple-devices-selected' -ExplicitSerial
    Invoke-Scenario 'wrong-serial' -RequestedSerial 'MISSING-FRAME' -ExpectedError 'requested frame is not connected and authorized'
    Invoke-Scenario 'missing-adb' -MissingAdb -ExpectedError 'ADB was not found'
    Invoke-Scenario 'missing-apk' -MissingApk -ExpectedError 'FrameRevive.apk is missing'
    Invoke-Scenario 'no-device' -ExpectedError 'Connect the frame by USB'
    Invoke-Scenario 'unauthorized' -ExpectedError 'Connect the frame by USB'
    Invoke-Scenario 'multiple-devices' -ExpectedError 'More than one Android device'
    Invoke-Scenario 'wrong-android' -ExpectedError 'validated for Android 10 only'
    Invoke-Scenario 'wrong-frame' -ExpectedError 'Expected Lenovo Smart Frame software not found'
    Invoke-Scenario 'install-failure' -ExpectedError 'INSTALL_FAILED_TEST_FIXTURE'
    Invoke-Scenario 'launch-failure' -ExpectedError 'Activity class does not exist'
    Invoke-Scenario 'initialization-failure' -ExpectedError 'App installed but setup did not initialize'
    Invoke-Scenario 'final-launch-failure' -ExpectedError 'Activity class does not exist'
    $completed = $true
    Write-Host "$passed installer scenarios passed on PowerShell $($PSVersionTable.PSVersion)."
} finally {
    $env:FRAMEREVIVE_TEST_STATE = $oldState
    $env:FRAMEREVIVE_TEST_SCENARIO = $oldScenario
    if ($completed -and -not $KeepArtifacts) {
        $resolvedRoot = [IO.Path]::GetFullPath($testRoot)
        $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\FrameRevive-installer-tests-'
        if (-not $resolvedRoot.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected test cleanup path.' }
        Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
    } else { Write-Host "Test artifacts retained at $testRoot" }
}
