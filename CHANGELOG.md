# Changelog
All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versioning: [SemVer](https://semver.org).

## [Unreleased]

## [1.2.0] - 2026-11-21
### Added
- Settings controls for **Reduce motion**, **Use current location**, and **Clear cache & refresh** (`SettingsBinder`, `layout_settings.xml`).
- **Undo** Snackbar when removing a saved city (`MainActivity`).
- Distinct sunset vector icon (`ic_sunset.xml`) on the Sunrise & Sunset card.
- Regression unit tests for administrative name cleaning, GPS coordinate bucketing, JSON `null`/`NaN` handling, current-hour visibility, and proximity deduplication (`BugFixRegressionTest`).

### Changed
- **Location accuracy**: filtered cached fixes by accuracy (`<= 1500 m`, preferring `< 5 min` fixes with `<= 250 m`), added a brief GPS refinement window in `awaitFreshFix()`, capped fallback fixes to 2 hours, bucketed GPS IDs to `~1.1 km` (`gps_%.2f_%.2f`), and used API 33+ async `Geocoder.GeocodeListener`.
- **Performance & battery**: paused `WeatherEffectView` when hidden/offscreen, converted particle physics and lightning timers to delta-time, cached `sunCoronaGradient`, eliminated per-draw `RadialGradient` allocations in `SolarArcView`, and switched `SavedLocationsAdapter` and `SearchAdapter` to `DiffUtil`.
- **UX & accessibility**: reused `HourlyAdapter` to preserve scroll position, diff-gated city pills and weekly rows, highlighted the active city card, auto-opened the soft keyboard on Search with `IME_ACTION_SEARCH`, respected OS `ANIMATOR_DURATION_SCALE` and `weatherBackgroundsEnabled`, and increased touch targets.
- Parallelized saved-city forecast refreshes and separated forecast cache into `weather_forecast_cache` `SharedPreferences`.

### Fixed
- Preserved numbered Vietnamese wards and districts (e.g., `"Phường 1"`, `"Quận 7"`) during reverse geocoding (`LocationResolver`).
- Fixed `"Now"` hourly badge off-by-one and current-hour visibility index (`ForecastView`, `WeatherRepositoryImpl`, `WeatherWidgetProvider`).
- Guarded Open-Meteo numeric JSON parsing against explicit `null`/`NaN` values and closed Nominatim HTTP connections in `finally`.
- Aligned preset city IDs, coordinates, and `Asia/Ho_Chi_Minh` timezones with defaults, widened proximity deduplication, fixed RTL switch/bottom-nav scrubbing, and separated geocoding base URLs.

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
