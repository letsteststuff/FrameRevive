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
