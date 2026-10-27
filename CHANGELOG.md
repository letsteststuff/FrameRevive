# Changelog

## 0.5.15-alpha

- Advance Plus/Minus on initial press rather than waiting for release; ignore repeats and release duplicates. Star retains short/hold behavior.

- Bound photo work to one active and one latest pending request. Skip obsolete work before decoding/blur, and retain the current photo while loading.
- Add on-frame and web Loading photo status. Shorten image-specific connect/read timeouts without changing album-scan timeouts.
- Retain Previous/Next displayed-photo history across same-album refreshes, replacing temporary URLs and removing deleted photos.
- Increased version code to 20.

## 0.5.14-alpha

- Map verified front Plus/Minus buttons to photo navigation and Star to pause/resume; holding Star opens controls without toggling on release.
- Add persistent on-frame Paused indicator and web pause/resume controls. Keep scheduled sleep and album refresh independent of pause.
- Add a device-generated QR code for the private controls link, Close button, and 30-second overlay timeout.
- Vendor the MIT-licensed Nayuki Java QR encoder with its license notices; no QR network service is used.
- Increased version code to 19.

## 0.5.13-alpha

- Show the frame's own date, time, time-zone ID and UTC offset beside the sleep schedule. Refresh the reading through existing status polling rather than using the phone or laptop clock.
- Increased version code to 18.

## 0.5.12-alpha

- Fill unused photo space with a darkened, blurred copy while retaining the sharp full image. Enabled by default in fit mode; crop mode retains priority.
- Add a blurred-background switch in web and native settings. Blur is prepared on the image worker at a bounded 96-pixel resolution.
- Increased version code to 17.

## 0.5.11-alpha

- Simplify controls styling to neutral charcoal with blue accents, system typography, compact headings, and direct functional labels. Remove promotional copy.
- Keep the Everyday, Setup, and Debug tabs and existing controls unchanged.
- Increased version code to 16.

## 0.5.10-alpha

- Redesigned the private controls page with Everyday, Setup, and Debug & updates tabs, responsive cards, larger touch controls, and keyboard navigation.
- Keep playback and display settings on Everyday; place album setup and HTTPS pairing under Setup.
- Add GitHub repository/release links and a copyable diagnostics summary that excludes private links, keys, cookies, album titles, and raw error messages.
- Preserve background status updates without resetting unsaved display settings or the active tab.
- Increased version code to 15. Existing app data and device certificate are preserved.

## 0.5.9-alpha

- Restore native left/right hand gestures, orientation mapping, Previous navigation and a gesture setting. Owner confirmed both directions.

## 0.5.8-alpha

- Require paired HTTPS with persistent Android Keystore identity; rotate the old HTTP management key once.
- Bound management concurrency and connection lifetime. Update certificate pairing, installer readiness and recovery guidance.

## 0.5.7-alpha

- Explicitly wake a locked display when a settings change leaves an active scheduled-sleep interval, rather than only restoring brightness.
- Capture the old schedule before saving, including when the activity is paused behind the sleeping display. Apply the behavior to web and on-frame settings.
- Preserve ordinary settings saves without unnecessary wake requests; retain existing alarms and schedule semantics.
- Increased version code to 12. Existing album, controls hostname, and settings remain unchanged.


## 0.5.6-alpha

- Poll shared-album initial-page counts every two minutes while the slideshow is awake. Count changes trigger a full refresh; hourly scans remain enabled.
- Queue newly discovered IDs from the same album for first display, including old captures, then return to the normal photo mix. Persist unshown priority IDs across app restarts.
- Show last count check, observed count, check errors and pending priority items on local controls.
- Compare against full-scan metadata count, so unsupported/filtered album entries do not force repeated scans.
- Increased version code to 11. Existing album source, settings, and signing key are preserved.


## 0.5.5-alpha

- Added a bounded, streamed APK-upload endpoint protected by the existing management key and same-origin checks.
- Validate package identity, compatible Android version, installed signing certificate, and non-decreasing version before staging. Android handles confirmation and installation through a narrow FileProvider grant; no silent installation.
- Added install-source permission guidance, cancel/retry/discard controls, and update status on the local page.
- Added a recovery guide to the controls page, docs, and Windows installer ZIP.
- Increased version code to 10. Existing app data is preserved when installing an update.


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
# 0.5.9-alpha — hand gestures

- Restore left-to-next and right-to-previous hand gestures using the frame's existing gesture input device, with no camera access.
- Follow Lenovo's direction mapping across display rotations and apply an 800 ms cooldown.
- Add a gesture on/off setting, last-gesture status and Previous buttons. Gestures pause during sleep and while on-frame controls are visible.
- Track up to 60 actually displayed photos for backward/forward navigation; reset history on app restart or completed album refresh.
- Build, 38 unit tests and lint passed. Owner confirmed both directions on the installed frame; normal settings restored.

# 0.5.8-alpha — security update

- Require HTTPS management with a persistent per-frame Android Keystore identity and explicit certificate pairing.
- Rotate the previously HTTP-exposed controls key once and update Windows/USB setup links.
- Bound request concurrency, aggregate headers and total handshake/header/body lifetime; close active sockets on shutdown.
- Add isolated TLS, slow-client, authentication and legitimate upload regression tests.
- Build, 33 unit tests, lint, and Android 10 HTTPS settings/upload/restart checks passed. The TLS key permissions support Conscrypt's prehashed/raw RSA signing while retaining signing-only key purpose and ECDHE/AES-GCM transport.

