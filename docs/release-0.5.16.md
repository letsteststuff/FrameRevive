# FrameRevive 0.5.16 Alpha

The recent 300 photos now rank strictly by when they were taken. Adding an old photo today no longer places it in the recent group. Additions still get one priority showing, and eligible old photos remain in the seasonal years-past mix.

All 0.5.15 controls, backgrounds, gestures, pause/resume and responsiveness improvements are included.

## Install or update

- First installation on Windows: extract **FrameRevive-0.5.16-Windows.zip** and follow **README-FIRST.md**.
- Existing frame: upload **FrameRevive-0.5.16-alpha.apk** through **Debug & updates**, then confirm installation on the frame. Do not uninstall first.
- GitHub's automatic Source code ZIP/tar.gz archives are for building from source.

## Validation

Build, 47 unit tests and lint passed. Regression tests cover old additions receiving priority without displacing newer captures, plus seasonal eligibility. Installed and verified over Wi-Fi ADB, preserving the existing album, settings and HTTPS identity.

Google Photos web access remains unofficial and may change. Uncached photos still depend on network performance. Follow the HTTPS pairing guide before trusting a frame certificate.
