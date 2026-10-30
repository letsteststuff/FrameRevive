# Roadmap

The native Android app on `main` is maintained. The [prototype roadmap](docs/archive/ROADMAP-prototype.md) is historical.

## Verified on the owner's frame

- Automatic discovery and first display of new shared-album photos.
- Scheduled sleep/wake, restart recovery, and stable local controls hostname.
- HTTPS management, shared albums, and owner-confirmed APK updates.
- Hand gestures, front-button navigation, pause indicator, blurred backgrounds, and the frame clock.
- Improved navigation while album additions are being processed in 0.5.15.

## Further validation

- Guided installation and HTTPS pairing on another owner's frame.
- Long-term reliability through repeated nights and large album changes.
- Compatibility if Google changes its shared-album web interface.
- QR scanning across more phones and physical frame orientations.

## Planned source expansion

- Network folders over SMB (Windows shared folders and NAS devices), configured from Setup alongside Google Photos shared links.
- Enter the server/share path and use read-only credentials when required. Store credentials encrypted on the frame and exclude them from diagnostics and bundles.
- Scan subfolders incrementally, use capture metadata for the photo mix, and reuse collage/preloading/playback behavior across sources.
- Keep a bounded local cache for temporary network outages. Prototype compatibility and performance before offering this as supported setup.

Network-folder access is not implemented yet. Source selection should separate catalog loading from slideshow rendering so it can be added without changing playback controls.

## Possible future work

- Burst-photo flipbook playback (deferred).
- Easier network changes and debugging recovery after power cycles, subject to Android permissions.

These are priorities, not promises of compatibility or a release schedule. See [setup and limitations](docs/standalone-alpha.md).
