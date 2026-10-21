# Standalone alpha: setup and recovery

This document describes the developer-assisted alpha, not a completed one-click installer.

## Local setup data

The app imports two files from its private `files/` directory on first launch:

- `session-import.txt`: Netscape-format cookies from a dedicated signed-in Google Photos browser session. Only Google Photos-relevant cookies are retained, including `photos.fife.usercontent.google.com`. The import is encrypted with an Android Keystore key, then the plaintext import is deleted.
- `album-seed.json`: a complete album index with `albumId`, `title`, and a `photos` array. Each photo has `id`, `url`, `takenMs`, and optional `offsetMs` and `discoveredMs`. The seed becomes the local `album.json`; the app subsequently refreshes the selected album directly from Google.

Keep these files outside the repository. The access probe can verify private-album access, capture dates, and image retrieval, but does not yet generate the seed file. Initial provisioning and session renewal therefore remain developer tasks.

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

Settings include photo interval, fit/fill, date captions, and nightly sleep/wake times. Scheduling is off by default. With the screen-lock administrator enabled, scheduled sleep turns the display off; a wake alarm turns it on again. Without administrator activation, the app falls back to dimming to black. Times use the frame's timezone.

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

Unit tests cover seasonal selection, calendar-year boundaries, leap day, newly added older photos, and overnight/daytime schedule boundaries. Build, unit tests, and Android lint passed. Device checks confirmed image playback, all album pages and dates, authenticated LAN management, real display-off/automatic wake, and Home startup with settings retained after reboot.

Still outstanding: a real new-photo addition test, long-term session survival, friendly session renewal, and a complete installer. Offline playback covers downloaded photos only. The newest-photo pool size and seasonal window are currently code settings. This alpha is intended for the tested Android 10 frame; other Android versions have not been validated.
