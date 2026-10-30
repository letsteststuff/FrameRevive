# FrameRevive: start here

This alpha is for the Lenovo Smart Frame CD-3L501F running Android 10. It replaces the discontinued Lenovo photo app with a standalone Google Photos slideshow. It does not require root or a factory reset.

Version 0.5.23 uses shared Google Photos album links for setup. It includes HTTPS controls, hand gestures, front-button navigation and pause, scheduled sleep/wake, preloading, adjustable photo mix, 2/3/4-photo collages and gentle transitions. Upgrade without uninstalling to preserve your settings.

## 1. Prepare the frame

- Keep it powered and connected to your home Wi-Fi.
- The frame is not assumed to have a touchscreen. On the tested frame, an Android phone running **Bluetooth Keyboard & Mouse** was paired with the frame and used as its mouse/keyboard. Pair it while Lenovo's software is still available, before running the installer. Keep the phone connected through the USB authorization step below. A compatible physical mouse/keyboard is another option.
- Use the phone's mouse controls to open the frame's Android Settings. In **About device** (wording may vary), select **Build number** repeatedly, usually seven times, until developer mode is enabled. Return to Settings, open **Developer options**, and enable **USB debugging**.
- Connect the frame to the Windows computer with a USB data cable. When **Allow USB debugging?** appears on the frame, use the phone's mouse controls to select **Always allow from this computer**, then **Allow/OK**. This on-frame prompt must be accepted; enabling developer mode alone is not enough. If the installer reports an unauthorized device, check the frame for this prompt and accept it before retrying.
- Install [Google's Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools). Android Studio's ADB is found automatically. Otherwise extract `platform-tools` beside this installer.
- Keep `FrameRevive.apk`, `Install.cmd`, and `Install-FrameRevive.ps1` together.

## 2. Install

**0.5.8 uses HTTPS:** the installer retrieves this frame's public certificate through authorized USB. Compare its SHA-256 fingerprint with the frame, then type `TRUST` if you want to trust it in your Windows user certificate store. Otherwise pair the browser manually using the verified fingerprint. Replace old HTTP bookmarks; the management key changes once. See [HTTPS pairing](../docs/security-pairing.md). Keep your Bluetooth Keyboard & Mouse app paired for on-frame verification and Android's installation confirmation.

Double-click **Install.cmd**. The script preserves existing FrameRevive settings, enables its screen-lock-only administrator, selects it as the Home app, and disables Lenovo's frame app reversibly. It opens the private setup page through USB and saves your controls links in `local/frame-controls.txt`.

If PowerShell blocks the downloaded script, inspect it first and unblock the trusted file in its Windows Properties. Your computer's execution policy may still require manual installation; the installer does not change that policy.

To configure Wi-Fi first, run in PowerShell:

```powershell
.\Install-FrameRevive.ps1 -ConfigureWifi
```

If installation reports a signature conflict with an existing FrameRevive build, stop and decide whether to preserve its data before uninstalling it. This script does not uninstall or reset apps automatically.

## 3. Connect your own Google Photos account

### Recommended: use a shared album link

1. In Google Photos, enable link sharing for the album you want to display.
2. Confirm that its link opens in a fresh Incognito window without signing in.
3. On the frame's setup page, paste the link into **Shared Google Photos album link** and click **Use shared album**.
4. Wait for the full scan to finish. The previous slideshow continues until the replacement index is complete.

This mode does not need a cookies export. Changes to that same album are checked every two minutes while awake, with a full hourly refresh, or with **Refresh album**. Anyone with the link can view the album, so keep it private. Disabling link sharing prevents future shared-mode refreshes. Google can still change this unofficial web interface.

### Existing saved-session installations

Session import, renewal and the signed-in album picker are retired. Existing saved sessions and selected albums are retained for compatibility, with a migration notice in Setup. If the session expires, switch to a shared album link using the steps above. Your previous complete catalog remains available until the replacement loads. New installations need no cookie export.

## 4. Use without the computer

Version 0.5.9 supports hand gestures on the Lenovo CD-3L501F: wave left for the next photo and right for a previously displayed photo. Pause about a second between waves. The **Hand gestures** option is under Display settings; gestures pause during sleep and while on-frame controls are open. See the [gesture guide](../docs/gestures.md).

Bookmark the **Home Wi-Fi** link in `local/frame-controls.txt` on your phone. It changes the album, photo interval, fit/fill, date captions, and nightly off/on schedule. It also changes the shared album without ADB.

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

Use version 0.5.1 or later for the corrected wake path and daytime brightness control. Version 0.5 missed a morning wake on the test frame. The new direct-activity wake passed a background-idle test; another full overnight check is still needed.

Photo playback, real screen-off/wake, and reboot startup were tested on one Lenovo Smart Frame. Refresh runs hourly while the app is running. Offline playback covers previously downloaded photos. Setup still needs testing on another person's frame and account. Long-term Google session durability is not established.


## 0.5.4 connection status and stable bookmark

The local controls page shows the current source, last successful refresh, last attempt, current refresh error, and the most recent photo-download error with a timestamp. Successful refreshes clear the current refresh error. Status updates every 15 seconds without overwriting unsaved display settings.

During playback the frame shows photos and optional captions. Tap with a connected input device to reveal the on-frame controls for 30 seconds. Photo changes no longer extend that timer.

Use **Bookmark your controls** on the page to save the full private hostname link. Each installation generates a hostname such as `framerevive-a1b2c3d4.local`. The mDNS responder returns the current Wi-Fi IPv4 address and never advertises the management key. Keep the key in the saved URL; the hostname does not replace authentication. It works only on the same local network while the app/frame is awake. Some networks block multicast or lack local-name support; keep the IP link as a fallback, or use a router DHCP reservation. A factory reset or clearing app data creates a new hostname and key.


## Webpage updates (0.5.5 and later)

The first upgrade from an older build still needs the Windows/ADB installer. Once 0.5.5 is installed, use **Update FrameRevive** on your private controls page: choose a `.apk`, upload and verify it, allow install requests on the frame if needed, then click **Install on frame** and confirm Android's prompt with your Bluetooth mouse. A ZIP is not an APK. The app verifies package identity, compatible Android version, matching signing certificate, and no downgrade; a same-version reinstall is allowed. No uninstall is needed, so settings and album data are preserved. The webpage may disconnect during replacement; reopen the saved link afterward. Cancel on the frame or discard a staged APK to stop. Upload limit: 40 MiB.

Read **RECOVERY.md** for black screens, photo/session failures, changed networks, and restoring Lenovo's launcher. The same guide is available on the controls page.


## Automatic additions (0.5.6 and later)

For a shared album, the awake slideshow checks the public initial-page item count every two minutes. A changed count requests a complete atomic refresh. It still scans the entire album hourly to renew photo URLs and catch changes that leave the count unchanged. This is polling, not a Google notification subscription; Google propagation and scan time add to the two-minute interval. Checks pause while the slideshow is asleep or another activity is in front, and run again when it resumes.

New IDs found in the same album are queued for one first display ahead of the normal mix, even for older capture dates. Pending priority IDs are retained through restart and removed after successful display. Selecting a different album does not treat its entire library as new additions. After the priority queue, the usual recent/seasonal mix resumes. No webpage interaction is required to detect or display additions.

The initial page is approximately 280 KB compressed on the tested album, not a zero-cost notification. Same-count replacements and edits may wait for the hourly scan. If count metadata is unavailable or a check fails, hourly refresh continues. See Connection status for the last count check, observed count, errors, and remaining priority items.


## Wake after changing sleep settings (0.5.7)

Disabling an active sleep schedule through the controls page explicitly wakes the display and restores daytime brightness. Editing the sleep/wake times so the current time is outside the sleep interval does the same. Ordinary setting saves outside a sleep interval do not request a display wake. The fix applies to both webpage and on-frame saves; it does not establish full overnight alarm reliability.
