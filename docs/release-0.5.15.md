# FrameRevive 0.5.15-alpha

Standalone Google Photos slideshow for the Lenovo Smart Frame CD-3L501F running Android 10.

## Downloads

- **Windows first installation:** download `FrameRevive-0.5.15-Windows.zip`, extract it, and follow `README-FIRST.md`. It includes the APK, installer, and guides.
- **Update an existing frame:** upload `FrameRevive-0.5.15-alpha.apk` through **Debug & updates**. Confirm installation on the frame using your Bluetooth mouse. Do not uninstall first.
- The automatic **Source code** ZIP/tar.gz downloads are for developers.

## Included changes

- Neutral Everyday / Setup / Debug & updates controls with connection details, diagnostics, and GitHub links.
- Shared Google Photos albums, automatic addition checks, and new-photo priority.
- Blurred backgrounds behind full photos; frame time and time-zone display beside the sleep schedule.
- Native hand gestures and front buttons: Plus next, Minus previous, Star pause/resume, hold Star for the controls and private QR code.
- Persistent Paused label, 30-second menu timeout, and web pause/resume.
- 0.5.15 improves navigation: actions begin on press, obsolete pending requests are dropped, Loading photo… is shown, and history survives same-album refreshes.
- Encrypted HTTPS controls and validated APK uploads. Follow the included pairing guide before trusting a device certificate.

## Validation and limits

Build, 46 unit tests, and lint passed. Installed over Wi-Fi ADB; HTTPS identity and existing settings were preserved. Owner confirmed the front controls, pause, held-Star overlay, blurred backgrounds, and improved navigation. Google's web interface is unofficial and may change. Uncached images can still take time to download. Other owners' frames and phone QR scanning need further validation. Android 10 Wi-Fi ADB resets after a power cycle.
