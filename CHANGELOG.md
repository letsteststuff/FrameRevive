# Changelog

## 0.5.4-alpha

- Added current source, last refresh success/attempt, refresh errors, and timestamped photo-download errors to local controls, with background status polling.
- Keep on-frame controls hidden during playback; explicit taps reveal them for 15 seconds without photo changes extending the timer.
- Added an IPv4 mDNS hostname with a per-install random suffix, independent of the secret management key. The stable bookmark retains authentication. IP/DHCP-reservation fallback remains available.
- Added bounded DNS parser tests and clean HTTP response shutdown for early form rejection.
- Increased version code to 9. Existing source, session, album, and display settings are preserved.


## 0.5.3-alpha

- Added shared-album links as an anonymous, cookie-free source, with full pagination and atomic index replacement. Signed-in album access remains available.
- Saved the selected source for restart and hourly refresh. Shared-link requests send no account cookies and do not save server cookies.
- Photo HTTP 403 errors request an album-link refresh at most once per five minutes. Cached playback no longer repeatedly opens the error controls over a displayed photo.
- Increased version code to 8; upgrading preserves existing settings and the last complete photo index.
- Shared links and account/session data must never be committed or included in installer packages.


## 0.5.2-alpha

- Local controls submit actions without leaving the page, with button spinners and inline results. Album/setup progress updates without reloading unsaved display settings.
- Lenovo front button LEDs turn off when idle, light on proximity or interaction, and turn off after eight seconds. Uses the existing firmware LED service; no root or extra privileges. Other firmware may lack this service.
- Version code increased to 7. Existing albums, sessions, and settings are preserved on upgrade.
- Build, unit tests, and lint passed. On-frame checks confirmed inline controls and proximity-triggered LED timeout. Full overnight wake validation remains pending.


## Repository maintenance

- Promoted the native 0.5.1-alpha implementation to `main`, preserving both branch histories.
- Retired the unused React/WebView prototype, bundled web assets, old bridge, placeholder layout, and tracked IDE settings.
- Archived prototype planning documents and replaced the current documentation index, architecture, and roadmap.
- This cleanup does not change the maintained app's behavior or version.

## 0.5.1-alpha

- Replaced skipped background wake broadcasts with direct activity alarms.
- Restored explicit daytime brightness and exposed the next scheduled wake in local controls.
- Build, 21 unit tests, and Android lint passed before promotion. Short hardware wake tests passed; full overnight confirmation remains pending.

## 0.5-alpha

- Added guided local Google session import/renewal and private album selection without a prebuilt seed index.

## 0.4-alpha

- Established the standalone native slideshow, private Google Photos album access, local controls, caching, launcher support, and nightly scheduling.

See the [prototype changelog](docs/archive/CHANGELOG-prototype.md) for the retired implementation.
