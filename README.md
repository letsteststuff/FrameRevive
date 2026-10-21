# FrameRevive

A standalone Android photo-frame app for the discontinued Lenovo Smart Frame (CD-3L501F, Android 10).

**`main` is the maintained branch.** The older prototype branches are historical; start new work and builds from `main`. See the [documentation index](docs/README.md).

## Current version: 0.5.2 alpha

Version 0.5.2 keeps local controls open during button actions, adds loading indicators and inline results, and restores proximity-triggered front button lights with an eight-second timeout on the tested Lenovo firmware.

Version 0.5.1 replaces skipped background wake broadcasts with direct activity alarms, restores the frame's own daytime brightness, and shows the next scheduled wake in the controls page. Version 0.5 missed an overnight wake on the tested frame. The replacement passed a background-idle wake test; full overnight confirmation is still pending.

FrameRevive reads an existing private Google Photos album over the frame's Wi-Fi connection. It replaces Lenovo's frame launcher and does not require an always-on laptop or a publicly shared album.

- Hourly album refresh with complete, atomic index updates.
- A shuffled mix of the newest 300 photos and photos captured within 14 calendar days of today in previous years.
- Newly discovered album additions join the recent pool even when their capture date is old.
- Display-sized image caching, capped around 512 MiB, for previously viewed photos.
- Configurable 10–300-second slideshow interval, fit/fill mode, and capture-date captions.
- Daytime brightness control independent of Lenovo's abandoned automatic-light behavior.
- Private LAN management page for a phone or computer on the same network.
- Guided Google session import and renewal through that page; no ADB needed for account setup after installation.
- Private-album picker that builds its own photo index. No seed file or someone else's account data is needed.
- Configurable nightly display-off and automatic wake using Android alarms and a device-admin policy limited to screen locking.
- Default Home launcher support for startup after reboot.
- Google session encryption using Android Keystore; app backups disabled.

**This is an alpha.** Google Photos access uses an unofficial, reverse-engineered signed-in web interface. Google may change it or expire the session. Initial installation requires ADB; account connection and renewal use a guided local session-file import, rather than Google's official Sign in button. Do not publish cookie exports, album indexes, cached photos, private management URLs, or app-data backups.

## Install on your own frame

See [installer/README-FIRST.md](installer/README-FIRST.md). The Windows installer locates ADB, installs the APK, enables the launcher and screen-power controls, and opens the setup page. Each owner imports their own session and selects their own album. Runtime account data and private management links are never part of the shared installer.

If building from source, copy `app/build/outputs/apk/debug/app-debug.apk` to `installer/FrameRevive.apk` before running the installer. That binary and installer-generated `local/` files are intentionally ignored by Git. The project has not been tested across multiple owners' frames yet.

## Architecture

The active app is native Kotlin. `MainActivity` handles playback and settings; `PrivatePhotosClient` performs read-only album access; `PhotoSelection` builds the slideshow; `FramePower` and `FrameSchedule` manage screen power; `FrameRemote` serves the private LAN controls. The earlier React/WebView prototype has been retired from the active tree. Its history and old planning documents remain available in the [historical archive](docs/archive/README.md). See [Architecture.md](Architecture.md) for the current design.

## Build and validation

Use Android Studio's JDK 21, the Android SDK, and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The alpha was built and tested on a Lenovo Smart Frame running Android 10. Hardware checks covered private-album reading, image display, LAN management authentication, display-off/automatic wake, startup after reboot, and guided setup without a saved album index or session. A newly added album photo still needs an end-to-end addition test, and long-term session durability is not established.

See [alpha setup and recovery notes](docs/standalone-alpha.md). The Python [read-only access probe](tools/probe_google_photos.py) uses only the standard library and requires a locally exported Netscape cookies file. Never paste cookies into chat, issues, or pull requests.

## Attribution

Read-only Google Photos web-protocol details are adapted from [xob0t/google_photos_web_client](https://github.com/xob0t/google_photos_web_client), under the MIT license. Its copyright and license are preserved in [tools/GOOGLE_PHOTOS_CLIENT_LICENSE.txt](tools/GOOGLE_PHOTOS_CLIENT_LICENSE.txt).
