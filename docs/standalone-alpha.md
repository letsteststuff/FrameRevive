# Standalone alpha: setup and recovery

This document describes the alpha's internals and recovery. For installation and guided account setup, use [the start-here guide](../installer/README-FIRST.md).

## Guided setup and local data

The ordinary setup flow no longer needs a developer-created seed. After installation, open the private management page, import a Netscape-format Google Photos session export, then choose an album. The frame validates the session before replacing any working session and reads every album page to build its own complete index. A failed album load retains the previous selected album.

Session import is sent directly to the frame, filters unrelated/expired cookies, and saves only encrypted session data. The installer opens a USB-forwarded localhost page for initial setup; subsequent account renewal and album changes can use the private Wi-Fi management page. There is no proxy account or shared login.

For compatibility with the earlier developer-assisted alpha, these two private imports remain supported:

The app imports two files from its private `files/` directory on first launch:

- `session-import.txt`: Netscape-format cookies from a dedicated signed-in Google Photos browser session. Only Google Photos-relevant cookies are retained, including `photos.fife.usercontent.google.com`. The import is encrypted with an Android Keystore key, then the plaintext import is deleted.
- `album-seed.json`: a complete album index with `albumId`, `title`, and a `photos` array. Each photo has `id`, `url`, `takenMs`, and optional `offsetMs` and `discoveredMs`. The seed becomes the local `album.json`; the app subsequently refreshes the selected album directly from Google.

Keep these files outside the repository. The access probe remains available for diagnostics. It is not needed for ordinary guided setup.

Install the debug APK with ADB. Private files can be provisioned using `adb shell run-as com.framerevive.app` for this debuggable alpha. Delete any temporary shared copies immediately after import. Do not put credentials in command arguments, logs, Git, or an APK.

## Launcher and screen power

After confirming photo playback, save a backup of the existing app and device configuration. These commands activate FrameRevive's screen-lock-only administrator policy and make it the Home app:

```powershell
adb shell dpm set-active-admin com.framerevive.app/.FrameAdminReceiver
adb shell cmd package set-home-activity com.framerevive.app/.MainActivity
```

On the tested Lenovo model, the original frame app is `com.walnut.bluetooth`. After verifying FrameRevive, it can be stopped and disabled reversibly:

```powershell
adb shell am force-stop com.walnut.bluetooth
adb shell pm disable-user --user 0 com.walnut.bluetooth
```

Verify the package names on other models first. Do not disable Android system services. No factory reset, root, or device-owner provisioning is required.

## Management and scheduling

The controls overlay displays a private management URL on TCP port 8765. Open it from a phone or computer on the same trusted home network. Every request requires a randomly generated per-install key. The management server uses HTTP, has no cloud relay, and must not be exposed by router port forwarding.

Settings include Google session renewal, private-album selection, photo interval, fit/fill, date captions, and nightly sleep/wake times. Scheduling is off by default. With the screen-lock administrator enabled, scheduled sleep turns the display off; a wake alarm turns it on again. Without administrator activation, the app falls back to dimming to black. Times use the frame's timezone.

A DHCP reservation is recommended for a stable management address. The private key persists if the IP address changes. The current address is computed at app startup, so restart the app to refresh the displayed address after a network change. Controls may not be reachable while the frame is asleep.

## Restore Lenovo's launcher

Turn off FrameRevive's sleep schedule first, then run:

```powershell
adb shell pm enable --user 0 com.walnut.bluetooth
adb shell cmd package set-home-activity com.walnut.bluetooth/com.smart_frame.SplashActivity
adb shell am start -n com.walnut.bluetooth/com.smart_frame.SplashActivity
```

Remove FrameRevive's device-administrator activation in Android settings before uninstalling. Restoring Lenovo's app does not restore its discontinued Google Photos integration.

## Validation and known limits

### Wake recovery in 0.5.1

The 0.5 build missed a morning wake: Android fired the alarm but skipped its manifest broadcast receiver. The frame's old automatic-brightness setting was also at its minimum. Version 0.5.1 schedules the Home activity directly, uses an alarm-clock wake event, rearms future alarms on activity entry, and restores an explicit per-app daytime brightness (55% by default). The controls page exposes brightness, the last power event, and the next wake time. Legacy broadcast alarms are canceled during migration.

Build, 21 unit tests, and lint passed. The updated frame woke without a key event after being put into background idle, restored its daytime brightness, and rearmed the following day's wake. Full overnight confirmation remains pending; the earlier short test did not establish overnight reliability.

Unit tests cover seasonal selection, calendar-year boundaries, leap day, newly added older photos, overnight/daytime schedule boundaries, cookie filtering/expiry/format limits, management authentication, cross-origin write rejection, and session-upload size handling. Build, unit tests, and Android lint passed. Device checks confirmed image playback, all album pages and dates, authenticated LAN management, real display-off/automatic wake, Home startup after reboot, rejection of invalid imports without replacing a working session, and guided setup with no saved session or album seed.

Still outstanding: a real new-photo addition test, long-term session survival, and testing the installer on another owner's frame/account. Offline playback covers downloaded photos only. The newest-photo pool size and seasonal window are currently code settings. This alpha is intended for the tested Android 10 frame; other Android versions have not been validated.


## 0.5.2 controls and button lights

Management buttons stay on the controls page, show a spinner while sending, and report results inline. Setup and album status update in the background without replacing unsaved display settings. A connection failure may mean the command arrived but its response was lost; check the frame before retrying.

On the tested Lenovo firmware, the native app controls the separate `led` system service. Front button lights are off when idle and illuminate at full button brightness for eight seconds after a proximity or input event. The proximity listener is removed and LEDs switched off when the activity pauses. No screen brightness or sleep/wake policy changes are part of this feature. Other firmware may not expose the service; photo playback continues without it.


## 0.5.3 shared-album mode

Paste a Google Photos shared-album URL that opens without sign-in into the local controls page. The app resolves only HTTPS Google Photos short links or /share/ pages, reads the initial data and every continuation page, and replaces the saved index only when the entire scan succeeds. The shared URL is stored in private app data for future refreshes. It is omitted from installer bundles and source files.

Shared requests use a dedicated anonymous client: no account cookies are sent or accepted, and an anonymous request does not require an authenticated request token. Each sync obtains fresh web metadata and photo URLs from the shared page. Hourly checks and manual refresh both use this source after restart. Signed-in album selection switches back to the encrypted-session client.

Image HTTP 403 errors trigger a rate-limited refresh (maximum once per five minutes); successful cached playback stays on screen without repeatedly reopening the failure controls. Google can revoke or change shared links, so cookie-free access does not imply guaranteed permanent availability.


## 0.5.4 connection status and stable bookmark

The local controls page shows the current source, last successful refresh, last attempt, current refresh error, and the most recent photo-download error with a timestamp. Successful refreshes clear the current refresh error. Status updates every 15 seconds without overwriting unsaved display settings.

During playback the frame shows photos and optional captions. Tap with a connected input device to reveal the on-frame controls for 15 seconds. Photo changes no longer extend that timer.

Use **Bookmark your controls** on the page to save the full private hostname link. Each installation generates a hostname such as `framerevive-a1b2c3d4.local`. The mDNS responder returns the current Wi-Fi IPv4 address and never advertises the management key. Keep the key in the saved URL; the hostname does not replace authentication. It works only on the same local network while the app/frame is awake. Some networks block multicast or lack local-name support; keep the IP link as a fallback, or use a router DHCP reservation. A factory reset or clearing app data creates a new hostname and key.


## Webpage updates (0.5.5 and later)

The first upgrade from an older build still needs the Windows/ADB installer. Once 0.5.5 is installed, use **Update FrameRevive** on your private controls page: choose a `.apk`, upload and verify it, allow install requests on the frame if needed, then click **Install on frame** and confirm Android's prompt with your Bluetooth mouse. A ZIP is not an APK. The app verifies package identity, compatible Android version, matching signing certificate, and no downgrade; a same-version reinstall is allowed. No uninstall is needed, so settings and album data are preserved. The webpage may disconnect during replacement; reopen the saved link afterward. Cancel on the frame or discard a staged APK to stop. Upload limit: 40 MiB.

Read [the recovery guide](recovery.md) for black screens, photo/session failures, changed networks, and restoring Lenovo's launcher. The same guide is available on the controls page.


## Automatic additions (0.5.6 and later)

For a shared album, the awake slideshow checks the public initial-page item count every two minutes. A changed count requests a complete atomic refresh. It still scans the entire album hourly to renew photo URLs and catch changes that leave the count unchanged. This is polling, not a Google notification subscription; Google propagation and scan time add to the two-minute interval. Checks pause while the slideshow is asleep or another activity is in front, and run again when it resumes.

New IDs found in the same album are queued for one first display ahead of the normal mix, even for older capture dates. Pending priority IDs are retained through restart and removed after successful display. Selecting a different album does not treat its entire library as new additions. After the priority queue, the usual recent/seasonal mix resumes. No webpage interaction is required to detect or display additions.

The initial page is approximately 280 KB compressed on the tested album, not a zero-cost notification. Same-count replacements and edits may wait for the hourly scan. If count metadata is unavailable or a check fails, hourly refresh continues. See Connection status for the last count check, observed count, errors, and remaining priority items.


## Wake after changing sleep settings (0.5.7)

Disabling an active sleep schedule through the controls page explicitly wakes the display and restores daytime brightness. Editing the sleep/wake times so the current time is outside the sleep interval does the same. Ordinary setting saves outside a sleep interval do not request a display wake. The fix applies to both webpage and on-frame saves; it does not establish full overnight alarm reliability.
# Management security update (0.5.8)

HTTPS replaces historical HTTP examples below. Replace old bookmarks and use the new per-frame key; there is no HTTP listener or redirect. Pair the certificate using the fingerprint on the physical frame or authorized USB. See [HTTPS pairing](security-pairing.md).

