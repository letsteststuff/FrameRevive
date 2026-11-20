# FrameRevive 0.5.25 alpha

This release adds optional holiday decorations and includes the recent dependency and Windows installer improvements.

## Changes

- Eight subtle static holiday themes, including colored Christmas lights, with Off, Automatic and manual choices under Everyday. Decorations appear over photos and collages. Choose a theme, then press Save settings at the bottom of the page.
- The installer accepts successful ADB launch warnings and still stops on genuine command failures.
- Eighteen automated installer scenarios pass on Windows PowerShell 5.1 and PowerShell 7. These simulate ADB responses and verify installer orchestration.
- Updated Android libraries and build tooling, including SDK 37 compilation. The app supports the Lenovo CD-3L501F running Android 10.

## Downloads

For first installation on Windows, download **FrameRevive-0.5.25-Windows.zip**, extract it completely and follow **README-FIRST.md**. The bundle contains the APK, installer and guides. Android SDK Platform-Tools are obtained separately from Google.

For an existing FrameRevive installation, use **FrameRevive-0.5.25-alpha.apk** with Debug & updates on the private controls page. Android confirms installation on the frame. Keep the existing app installed to preserve settings.

**SHA256SUMS.txt** records the download checksums. GitHub's Source code archives contain the repository source.

## Testing and limitations

The holiday display and existing .25 app were checked on the developer's frame. The release rebuild runs Android unit tests and lint. Installer simulations cover warning/failure handling, fresh/repeat installation, device selection and initialization, while physical USB drivers and Lenovo launcher behavior still depend on the actual device.

Google Photos uses an unofficial shared-album connection. This is an alpha release. Share private controls links, album links and screenshots containing them only with people you intend to authorize.
