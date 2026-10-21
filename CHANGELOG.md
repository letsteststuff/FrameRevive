# Changelog

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
