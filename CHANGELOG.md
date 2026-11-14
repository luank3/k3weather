# Changelog
All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versioning: [SemVer](https://semver.org).

## [Unreleased]

## [1.1.0] - 2026-11-14
### Added
- Scrubbable segmented Chart/List switch on the multi-day forecast card with smooth cross-slide transitions.
- Redesigned Liquid Glass weather icons (including layered fog icon) and enriched animated atmospheric skies (`WeatherEffectView` and widget sky drawables).
- Stale/offline forecast status pill with updated timestamp (`ForecastView`) and regression test suite (`BugFixRegressionTest`).

### Changed
- ColorOS-inspired visual hierarchy and spacing across forecast, saved locations, bento metrics, and launcher widgets; inset-aware bottom navigation clearance on small screens.
- Centered adaptive launcher icon foreground (`ic_launcher_foreground.xml`).
- Display visibility below `1 km` in metres and below `1 mi` to one decimal place.

### Fixed
- Prevented out-of-order forecast requests and detected-location updates from overwriting newer selections (`WeatherViewModel`).
- Cleared previous city metrics and hid forecast cards on uncached load failure instead of showing layout preview data (`ForecastView`).
- Protected widget broadcast updates with `goAsync()` and timeout bounds, fixed cross-city cache fallback labeling, and deduplicated/serialized widget refreshes (`WeatherWidgetProvider`, `MainActivity`).
- Managed `WeatherViewModel` via `ViewModelProvider`, refreshed GPS on pull-to-refresh or stale resume, and stopped overriding explicitly chosen cities on startup.
- Preserved `isStale` cache flags and validated `"Now"` / `"Today"` labels against actual timestamps and timezones.
- Fixed daily temperature range bars not sizing while `dailyRows` starts `GONE` and paused hidden `HeroWeatherArtworkView` animators.
- Switched secondary IP geolocation failover to HTTPS (`get.geojs.io`) for all primary failures, added bounded cache pruning, round-tripped `reduceMotion`, and preserved `"system"` locale on configuration changes.
- Removed stray `"()"` text on launcher widgets and improved location-service re-enable warmup detection.

## [1.0.0] - 2026-11-13
### Added
- Liquid Glass UI: hero temperature, narrative summary, hourly and 7-day forecast, bento metric cards.
- Home-screen widgets: 2×2, 4×2, 4×4 with day/night/rain skies.
- Search & add location with curated popular cities.
- Settings: temperature, wind, precipitation and pressure units; language.
- Vietnamese and English localization.
- New launcher icon.
- Standard project scaffolding: CI, release workflow, SemVer + commit-hash versioning.
