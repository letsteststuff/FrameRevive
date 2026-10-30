# Web controls

- **Everyday** opens first: album health, previous/next/refresh and pause/resume, playback interval, brightness, photo dates, cropping or blurred background, sleep schedule, the frame clock/time zone, and hand gestures. Save settings to apply display changes.
- **Setup**: connect a shared album link that opens without signing in, and find your private bookmark and HTTPS pairing help.
- **Debug & updates**: connection/activity details, copy diagnostics, upload and verify an APK, recovery steps, and GitHub repository/releases links.

Tabs work on phones and with a keyboard. Background status updates preserve unsaved display fields. Copy diagnostics excludes private links, album titles, credentials, and raw error messages. The controls bookmark still contains a private access key; keep it private.

The normal photo mix alternates the 300 newest photos by capture time with previous-year photos within 14 days of today's date. New additions get one priority showing regardless of capture date. Discovery/upload time does not change membership in the recent 300.

0.5.19 includes a Recent/Memories slider, recent count and seasonal window, automatic preloading and occasional collage controls, and a live preload-status panel plus collage preview in Debug. See [the detailed guide](preloading-mix-collages.md).

## Gentle transitions

Everyday has a **Gentle transitions** switch, enabled by default. The complete photo and blurred background crossfade together over 0.6 seconds once the next image is ready. It applies to collages and manual navigation too. Pause ends the fade on the selected image. Rapid navigation replaces the unfinished fade instead of queuing animations. Android animation settings can shorten or disable the effect.

Signed-in session import, renewal and the signed-in album picker are retired in 0.5.23. Existing saved-session albums display a migration notice; playback and refresh remain temporarily available using the existing encrypted session. Shared-link users do not see that notice.
