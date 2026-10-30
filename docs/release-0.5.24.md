# FrameRevive 0.5.24 Alpha — collages and simpler setup

- Automatic 2-, 3- and 4-photo collages, with portrait and landscape layouts. Default: one collage after 20 individual photos; adjustable.
- Gentle 0.6-second crossfades, without an on-frame loading label.
- Automatic preloading with live Debug queue states, plus an adjustable Recent/Memories photo mix.
- Shared Google Photos links are now the supported setup. Session imports and the signed-in picker are retired; existing saved sessions remain temporarily with migration guidance.
- Debug shows the active album link; copied diagnostics omit it.
- Retains front buttons, hand gestures, sleep/wake, HTTPS controls and webpage APK updates.

## Install or update

New Windows installation: download **FrameRevive-0.5.24-Windows.zip**, extract it, and follow **README-FIRST.md**. Use a USB data cable and Bluetooth mouse to enable and authorize USB debugging. Platform-Tools are required; Android Studio is optional.

Existing installation: upload **FrameRevive-0.5.24-alpha.apk** in **Debug & updates** and confirm Android installation on the frame. Do not uninstall first. GitHub's automatic Source code archives are for building from source.

## Controls preview

These screenshots use demonstration data; no owner's album link, controls key, certificate or photos are included.

![Everyday, Setup and Debug tabs](https://raw.githubusercontent.com/letsteststuff/FrameRevive/main/docs/screenshots/controls-overview-0.5.24.jpg)

[Everyday — full size](https://raw.githubusercontent.com/letsteststuff/FrameRevive/main/docs/screenshots/everyday-0.5.24.jpg) · [Setup — full size](https://raw.githubusercontent.com/letsteststuff/FrameRevive/main/docs/screenshots/setup-0.5.24.jpg) · [Debug — full size](https://raw.githubusercontent.com/letsteststuff/FrameRevive/main/docs/screenshots/debug-0.5.24.jpg)

## Validation and limits

Android build, 66 unit tests and lint passed. Installed on the owner's Lenovo CD-3L501F (Android 10) over Wi-Fi with settings and HTTPS identity preserved. Owner confirmed collage layouts and fade speed; the on-frame loading label was subsequently removed. Retired import actions were checked for rejection without altering saved sessions or settings. The Debug album link matches the active catalog and is excluded from copied diagnostics.

Google Photos access remains unofficial and can change. A fresh installation on another owner's frame and live playback of a legacy signed-in account have not been revalidated. Network-folder/SMB support is on the roadmap, not implemented.
