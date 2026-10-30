# FrameRevive

A standalone Android photo-frame app for the discontinued Lenovo Smart Frame (CD-3L501F, Android 10).

**`main` is the maintained branch.** The older prototype branches are historical; start new work and builds from `main`. See the [documentation index](docs/README.md).

## Current version: 0.5.24 alpha

The tested build includes a shared Google Photos album source, automatic checks for additions, a neutral three-tab HTTPS controls page, blurred photo backgrounds, a frame clock/time-zone display, hand gestures, and front-button navigation with pause/resume.

- **Plus / Minus:** next / previous photo on initial press.
- **Star:** pause/resume with a persistent Paused label. Hold about 2.5 seconds for the controls overlay, private QR code and address; the menu closes after 30 seconds without input.
- **Web controls:** Everyday, Setup, and Debug & updates, including APK upload, safe diagnostics, and GitHub links.
- **Photo mix:** adjustable Recent/Memories slider, recent capture count, and seasonal date window. Defaults remain 300 recent photos, +/-14 days, and 50/50. Additions still get a first priority showing; capture date determines recent selection.
- **Preloading:** a bounded background queue prepares upcoming photos; Debug shows Ready, Loading, Queued and Failed states.
- **Gentle transitions:** optional 0.6-second crossfades between photos and collages.
- **Collages:** optional 2-, 3- and 4-photo collages after every 20 individual photos, with layouts suited to portrait and landscape images, with adjustable frequency and a Debug preview. See the [preloading, mix and collage guide](docs/preloading-mix-collages.md).
- **Navigation:** one active image operation and only the latest pending request, loading feedback, and history retained across same-album refreshes.

Supported setup uses a Google Photos shared album link that opens without signing in. The frame checks the album count every two minutes while awake, performs a full hourly refresh, and caches downloaded photos for offline playback. New additions get a first showing before the regular photo mix.

## Install on your own frame

Use the Windows installer bundle from [Releases](https://github.com/letsteststuff/FrameRevive/releases) and follow [README-FIRST](installer/README-FIRST.md). Initial installation needs a USB data cable, authorized ADB, and a Bluetooth mouse or equivalent input device. No factory reset or root is needed. The installer sets FrameRevive as Home and reversibly disables Lenovo's abandoned app. Future APK updates can be uploaded from Debug & updates, with Android confirmation on the frame.

Each frame creates its own private HTTPS controls link and certificate. Pair the certificate through the frame or authorized USB before trusting it. Keep management and album links private. See [HTTPS pairing](docs/security-pairing.md) and [recovery](docs/recovery.md).

### Saved-session compatibility

Signed-in session setup, cookie imports, album picking and session renewal are retired in 0.5.23. Existing encrypted sessions and selected signed-in albums are retained temporarily for playback and refresh. They are not the supported setup path, and an expired session cannot be renewed in FrameRevive. Setup shows affected owners how to switch the same album to a shared link; the previous catalog remains until that link finishes loading. No Google account session is needed for shared-link mode.

Network-folder sources are planned, not implemented. See [ROADMAP](ROADMAP.md).

If building from source, copy `app/build/outputs/apk/debug/app-debug.apk` to `installer/FrameRevive.apk` before running the installer. Binaries and installer-generated `local/` files are ignored by Git.

## Architecture

The active app is native Kotlin. `MainActivity` handles playback and settings; `PrivatePhotosClient` performs read-only album access; `PhotoSelection` builds the slideshow; `FramePower` and `FrameSchedule` manage screen power; `FrameRemote` serves the private LAN controls. The earlier React/WebView prototype has been retired from the active tree. Its history and old planning documents remain available in the [historical archive](docs/archive/README.md). See [Architecture.md](Architecture.md) for the current design.

## Build and validation

Use Android Studio's JDK 21, the Android SDK, and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The tested target is Lenovo Smart Frame CD-3L501F running Android 10. The owner has confirmed playback, additions, buttons, gestures, mixed collages, fades, sleep/wake and reboot recovery. Installation on another owner's untouched frame and long-term reliability still need validation. Google Photos access uses an unofficial web interface and may change.

## Attribution

Read-only Google Photos web-protocol details are adapted from [xob0t/google_photos_web_client](https://github.com/xob0t/google_photos_web_client), under the MIT license. Its copyright and license are preserved in [tools/GOOGLE_PHOTOS_CLIENT_LICENSE.txt](tools/GOOGLE_PHOTOS_CLIENT_LICENSE.txt).
