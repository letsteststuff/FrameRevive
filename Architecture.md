# Current architecture

FrameRevive 0.5.1-alpha is a native Kotlin Android application for the Lenovo Smart Frame CD-3L501F running Android 10. The maintained source is on `main`.

- `MainActivity`: native slideshow, settings, launcher lifecycle, and daytime brightness.
- `PrivatePhotosClient`: read-only access to the selected private Google Photos album using the unofficial signed-in web interface.
- `GoogleSessionCookies`: imported session parsing; session material is encrypted with Android Keystore.
- `PhotoSelection`: recent and seasonal photo selection from the album index.
- `FrameRemote`: authenticated local network management page and guided session/album setup.
- `FramePower` and `FrameSchedule`: display-off policy and direct activity alarms for scheduled wake.

The frame connects to Google over Wi-Fi and stores its own index and image cache. A laptop is needed for initial ADB installation, not routine playback. Account data, photos, and management credentials do not belong in Git. Android backups are disabled.

The React/Vite frontend, WebView assets, and JavaScript bridge are retired. Historical source remains in Git history; see [the archive](docs/archive/README.md). The current controls page is served by the native app.

See [setup, validation, and recovery](docs/standalone-alpha.md). Full overnight confirmation of the 0.5.1 wake fix remains pending.
