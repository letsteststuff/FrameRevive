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

## Updating from 0.5.24 to 0.5.25

You can update through the frame's controls webpage over your home Wi-Fi. USB and the Windows installer are not needed for this method. Keep your Bluetooth mouse or phone's Bluetooth Keyboard & Mouse app available to confirm the update on the frame.

1. Download **FrameRevive-0.5.25-alpha.apk** from the 0.5.25 release.
2. Open your saved private FrameRevive controls webpage and select **Debug & updates**.
3. Under **App update**, choose the downloaded APK and click **Upload & verify APK**.
4. If Android install permission is not yet allowed, click **Allow installs on frame**. On the frame, enable **Allow from this source** for FrameRevive, then go Back to the slideshow.
5. On the webpage, click **Install on frame**. Use your Bluetooth mouse to click **Install** in Android's confirmation on the frame.
6. When installation finishes, choose **Open** or return Home. Reopen or refresh your saved controls webpage if it disconnected during the update, and check that the installed version is **0.5.25-alpha**.

**Keep FrameRevive installed and do not clear its app data.** Updating this way preserves your album link and settings. If verification reports a signing-key mismatch, stop and ask for help rather than uninstalling.

## Testing and limitations

The holiday display and existing .25 app were checked on the developer's frame. The release rebuild runs Android unit tests and lint. Installer simulations cover warning/failure handling, fresh/repeat installation, device selection and initialization, while physical USB drivers and Lenovo launcher behavior still depend on the actual device.

Google Photos uses an unofficial shared-album connection. This is an alpha release. Share private controls links, album links and screenshots containing them only with people you intend to authorize.
