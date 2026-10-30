# Preloading, photo mix and collages (0.5.20)

Everyday includes the Recent/Memories slider, recent-photo count and seasonal memory window. Recent means capture date. Newly discovered photos still get their first showing before the usual mix.

Automatic preloading prepares three upcoming photos on disk, or up to four near the next collage. One background download runs at a time. Debug shows Ready, Loading, Queued and Failed states with capture dates, without private photo URLs. An existing download may finish after a plan changes.

## Occasional collages

Enabled by default, with a collage after 20 successfully displayed individual photos. Automatic collages cycle through 2, 3 and 4 photos. Choose 5–100 individual photos between collages or switch collages off. A collage lasts the normal photo interval. It uses distinct upcoming photos from the regular mix, avoiding the current photo. If the selected photos are not ready in the disk cache, ordinary playback continues and tries again later.

The layout is chosen from tall side-by-side panels, stacked panels, a larger main panel with two smaller panels, and a four-photo grid. Photo dimensions determine the arrangement that shows the most image area without cropping. Each cell fits the complete image. The blurred-background setting fills unused cell space, and the date setting shows each photo's date. Pause, Next and Previous work with the whole collage. History contains photo references rather than retained bitmaps. After an album refresh, a historical collage is removed if any member is no longer available.

Debug's **Preview collage** cycles through 2, 3 and 4 cached photos without changing the saved frequency or pause state. The collage counter restarts with the app; it is not an exact wall-clock schedule.

Burst playback has been removed for now.
