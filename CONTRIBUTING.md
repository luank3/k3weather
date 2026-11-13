# Contributing

## Workflow
1. Fork / branch from `main`: `feat/<topic>`, `fix/<topic>`, `chore/<topic>`.
2. Make focused changes; keep the release APK small (< 2 MB) and avoid new heavy dependencies.
3. Run locally before pushing:
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
4. Open a PR against `main`. CI must be green.

## Commit messages — Conventional Commits
```
<type>(<scope>): <summary>
```
Types: `feat`, `fix`, `perf`, `refactor`, `style`, `docs`, `test`, `build`, `ci`, `chore`, `revert`.
Breaking change: `feat!:` or a `BREAKING CHANGE:` footer.

| Commit type | SemVer bump |
|-------------|-------------|
| `fix`, `perf` | PATCH |
| `feat` | MINOR |
| breaking | MAJOR |

## Code & UI rules
- Kotlin, 4-space indent (see `.editorconfig`).
- Every user-facing string goes in **both** `values/strings.xml` and `values-vi/strings.xml`.
- UI follows the Liquid Glass design system (translucent cards, hairline rims, Apple-style typography). Reuse existing drawables (`bg_card_translucent`, `bg_pill_accent_translucent`, …).
- Attach before/after screenshots to UI PRs.
- Never commit secrets, keystores, or `local.properties`.

## Versioning
SemVer, source of truth `VERSION_NAME` in `gradle.properties`. Build metadata is the git short hash (`1.2.3+abc1234`).

## Releasing
1. Ensure `main` is green.
2. `scripts/bump-version.sh <major|minor|patch>`.
3. Move **Unreleased** entries in `CHANGELOG.md` under the new version heading with date.
4. `git commit -am "chore(release): vX.Y.Z"`.
5. `git tag -a vX.Y.Z -m "vX.Y.Z" && git push --follow-tags`.
6. The `Release` workflow verifies the tag equals `VERSION_NAME`, builds the signed APK, and publishes a GitHub Release.

Pre-release suffixes (`-rc.1`) are not supported by the versionCode scheme; use a normal bump.
