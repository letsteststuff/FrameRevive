# FrameRevive

A standalone Android photo-frame app for the discontinued Lenovo Smart Frame (CD-3L501F, Android 10).

## Current version: 0.4.0 alpha

FrameRevive reads an existing private Google Photos album over the frame's Wi-Fi connection. It replaces Lenovo's frame launcher and does not require an always-on laptop or a publicly shared album.

- Hourly album refresh with complete, atomic index updates.
- A shuffled mix of the newest 300 photos and photos captured within 14 calendar days of today in previous years.
- Newly discovered album additions join the recent pool even when their capture date is old.
- Display-sized image caching, capped around 512 MiB, for previously viewed photos.
- Configurable 10–300-second slideshow interval, fit/fill mode, and capture-date captions.
- Private LAN management page for a phone or computer on the same network.
- Configurable nightly display-off and automatic wake using Android alarms and a device-admin policy limited to screen locking.
- Default Home launcher support for startup after reboot.
- Google session encryption using Android Keystore; app backups disabled.

**This is an alpha.** Google Photos access uses an unofficial, reverse-engineered signed-in web interface. Google may change it or expire the session. Setup and session renewal currently require technical steps over ADB; there is no finished onboarding or reauthorization flow. Do not publish cookie exports, album indexes, cached photos, private management URLs, or app-data backups.

## Architecture

The active app is native Kotlin. `MainActivity` handles playback and settings; `PrivatePhotosClient` performs read-only album access; `PhotoSelection` builds the slideshow; `FramePower` and `FrameSchedule` manage screen power; `FrameRemote` serves the private LAN controls. The earlier React/WebView prototype remains in this repository for historical context and is not loaded by the native app.

## Build and validation

Use Android Studio's JDK 21, the Android SDK, and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The alpha was built and tested on a Lenovo Smart Frame running Android 10. Hardware checks covered private-album reading, image display, LAN management authentication, display-off/automatic wake, and startup after reboot. A newly added album photo still needs an end-to-end addition test, and long-term session durability is not established.

See [alpha setup and recovery notes](docs/standalone-alpha.md). The Python [read-only access probe](tools/probe_google_photos.py) uses only the standard library and requires a locally exported Netscape cookies file. Never paste cookies into chat, issues, or pull requests.

## Attribution

Read-only Google Photos web-protocol details are adapted from [xob0t/google_photos_web_client](https://github.com/xob0t/google_photos_web_client), under the MIT license. Its copyright and license are preserved in [tools/GOOGLE_PHOTOS_CLIENT_LICENSE.txt](tools/GOOGLE_PHOTOS_CLIENT_LICENSE.txt).
