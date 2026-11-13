# K3 Weather

A lightweight (< 2 MB) Android weather app with an Apple Weather–inspired
"Liquid Glass" UI, home-screen widgets (2×2, 4×2, 4×4), Vietnamese/English
localization and real GPS-based location.

- Weather data: [Open-Meteo](https://open-meteo.com/)
- Place names: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors (Nominatim)

## Requirements
- JDK 17
- Android SDK (compileSdk 35, minSdk 26)

## Build
```bash
./gradlew assembleDebug          # debug APK  -> app/build/outputs/apk/debug
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android lint
./gradlew assembleRelease        # release APK (signed if keystore configured)
```
Machine-specific settings (JDK path, aapt2 override) belong in
`~/.gradle/gradle.properties`, not in the repo.

## Versioning
[Semantic Versioning](https://semver.org). The single source of truth is
`VERSION_NAME` in [`gradle.properties`](gradle.properties).

| Item | Value |
|------|-------|
| `versionName` | `MAJOR.MINOR.PATCH+<git short hash>` (e.g. `1.0.0+abc1234`) |
| `versionCode` | `MAJOR*10000 + MINOR*100 + PATCH` |
| Debug builds | `-debug` suffix |

The version and commit hash are shown in **Settings → About** and exposed as
`BuildConfig.VERSION_SEMVER` / `BuildConfig.GIT_HASH`.

## Release signing
Release signing config is read from `keystore.properties` (git-ignored) or
environment variables (CI). Without it, release builds fall back to the debug key.

```properties
# keystore.properties
storeFile=app/release-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```
CI secrets: `K3_KEYSTORE_BASE64`, `K3_KEYSTORE_PASSWORD`, `K3_KEY_ALIAS`, `K3_KEY_PASSWORD`.

> Never commit keystores or passwords.

## Releasing
See [CONTRIBUTING.md](CONTRIBUTING.md#releasing). In short:
```bash
scripts/bump-version.sh minor     # edit CHANGELOG.md too
git commit -am "chore(release): v1.1.0"
git tag -a v1.1.0 -m "v1.1.0" && git push --follow-tags
```
The `Release` workflow builds the signed APK and publishes a GitHub Release
with a SHA-256 checksum.

## License
[MIT](LICENSE)
