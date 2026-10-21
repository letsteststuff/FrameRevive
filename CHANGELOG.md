# Changelog

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
