# FrameRevive: start here

This alpha is for the Lenovo Smart Frame CD-3L501F running Android 10. It replaces the discontinued Lenovo photo app with a standalone Google Photos slideshow. It does not require root or a factory reset.

## 1. Prepare the frame

- Keep it powered and connected to your home Wi-Fi.
- Connect USB and enable USB debugging in Android developer options. Accept **Always allow from this computer** on the frame. A USB mouse/hub may be needed to operate Android settings; the frame is not assumed to have a touchscreen.
- Install [Google's Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools). Android Studio's ADB is found automatically. Otherwise extract `platform-tools` beside this installer.
- Keep `FrameRevive.apk`, `Install.cmd`, and `Install-FrameRevive.ps1` together.

## 2. Install

Double-click **Install.cmd**. The script preserves existing FrameRevive settings, enables its screen-lock-only administrator, selects it as the Home app, and disables Lenovo's frame app reversibly. It opens the private setup page through USB and saves your controls links in `local/frame-controls.txt`.

If PowerShell blocks the downloaded script, inspect it first and unblock the trusted file in its Windows Properties. Your computer's execution policy may still require manual installation; the installer does not change that policy.

To configure Wi-Fi first, run in PowerShell:

```powershell
.\Install-FrameRevive.ps1 -ConfigureWifi
```

If installation reports a signature conflict with an existing FrameRevive build, stop and decide whether to preserve its data before uninstalling it. This script does not uninstall or reset apps automatically.

## 3. Connect your own Google Photos account

Use **Connect or renew Google Photos** on the new local setup page:

1. Install **Get cookies.txt LOCALLY** from the Chrome Web Store link; allow it in Incognito.
2. In a new Incognito window, sign in to Google Photos and open your library.
3. Open a blank tab, close the Google Photos tab, and click **Export All Cookies** in the extension. Use Netscape text format.
4. Close Incognito. On the frame's setup page, choose that file and click **Import session**.
5. Wait for validation, select an album, and click **Use this album**. Large albums take time to read.

The session is sent directly to your frame and stored encrypted. No album-index file is needed. Never send your cookies to someone else or commit them to GitHub. Delete the export after successful setup. This uses an unofficial Google Photos web interface; Google may require a fresh export later.

## 4. Use without the computer

Bookmark the **Home Wi-Fi** link in `local/frame-controls.txt` on your phone. It changes the album, photo interval, fit/fill, date captions, and nightly off/on schedule. It also renews the session without ADB.

Once photos are displaying and the Wi-Fi link works, disconnect USB. Keep the frame powered. Scheduling is off by default; choose your own times. A router DHCP reservation keeps the address stable. Keep the management link private and do not expose the port to the internet.

## Restore Lenovo's app

Turn off FrameRevive's sleep schedule, reconnect USB, then run:

```powershell
adb shell pm enable --user 0 com.walnut.bluetooth
adb shell cmd package set-home-activity com.walnut.bluetooth/com.smart_frame.SplashActivity
adb shell am start -n com.walnut.bluetooth/com.smart_frame.SplashActivity
```

This restores Lenovo's launcher, not its discontinued Google Photos connection. Disable FrameRevive's device-administrator activation in Android settings before uninstalling it.

## Alpha limitations

Photo playback, real screen-off/wake, and reboot startup were tested on one Lenovo Smart Frame. Refresh runs hourly while the app is running. Offline playback covers previously downloaded photos. Setup still needs testing on another person's frame and account. Long-term Google session durability is not established.
