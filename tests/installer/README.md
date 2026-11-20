# Installer tests

Run from the repository root on Windows in PowerShell 5.1 or PowerShell 7.

```powershell
.\tests\installer\Run-InstallerTests.ps1
```

The runner copies the real installer into a temporary directory and supplies an isolated fake ADB executable. It compiles `FakeAdb.cs` with the Windows .NET Framework compiler. No Pester or Android SDK installation is needed.

The native executable emits stdout, stderr warnings and failure exit codes so PowerShell exercises the same process boundary used by ADB. It records every command and rejects unexpected commands. The runner declines certificate trust and guards certificate-import and browser-launch functions. Generated certificates contain test data. Temporary files are removed after a passing run. Use `-KeepArtifacts` to inspect them, or `-InstallerPath` to test another installer file.

The scenarios cover a fresh installation, a repeat installation, both launch warnings, delayed app initialization, Wi-Fi setup, disconnected Wi-Fi, missing APK or ADB, missing or unauthorized devices, multiple devices with and without a selected serial, an incorrect serial, incompatible Android/device, failed installation, failed first/final launches, and initialization failure. Assertions check command ordering, `install -r`, preserved launcher backup, existing administrator handling and generated controls links.

These tests validate installer orchestration and error handling. APK installation and data migration need an Android emulator or real device. Lenovo launcher behavior, USB drivers and authorization prompts need a physical frame. The fake ADB executable has no device communication implementation.

GitHub Actions runs the suite in both Windows PowerShell 5.1 and PowerShell 7 when installer or test files change.
