# Loud

A maximalist Android 12+ home launcher with **Square Punch** and **Round Paper** shapes, five palettes, a theme canvas and optional wallpaper.

The native app now uses installed apps and persistent data: searchable A–Z app list, shortcuts, hide/rename, editable tiles, Room layout/history, DataStore appearance, and JSON export/import. Live tiles include clock/alarms, weather, synced device calendars, media controls, notification counts, app frequency, browser/developer links, notes, battery, focus and habits. Third-party Android widgets use the standard consent and configuration flow. Optional Power Bubble supplies screen-off and notification-shade actions.

CI has compiled, run JVM tests and assembled the implementation APK. The latest lint gate needs one targeted location-permission fix; phone acceptance remains pending. See [validation](docs/VALIDATION.md) for the exact tested commit and remaining device checks.

## Install and set up

1. Download **loud-debug** from a successful [Build workflow](https://github.com/peteroomen/loud-launcher/actions/workflows/build.yml), unzip and install its APK. Stable signed Releases become available after the signing secret is provisioned.
2. Open Loud and choose it in Android **Settings → Apps → Default apps → Home app**, or use Loud's access screen.
3. Grant calendar access when wanted. Enable notification access for notification counts and music controls; usage access is optional because Loud records its own launches. Choose a weather city manually or grant approximate location.
4. For screen lock and notification shade, install [Power Bubble](https://github.com/peteroomen/power-bubble) and explicitly enable its accessibility service in its own setup.

Notification badges count posted notifications; they are not guaranteed unread-message counts. Weather uses Open-Meteo, caches readings locally, and refreshes through Android-managed WorkManager scheduling. Calendar data comes from calendars already synced to the device; no separate account login is built into Loud.

## Obtainium and stable updates

Use `https://github.com/peteroomen/loud-launcher` as the Obtainium source URL. Updates become available after a successful GitHub Release containing a signed APK.

In repository **Settings → Secrets and variables → Actions**, add `DEBUG_KEYSTORE_BASE64` using the existing shared keystore used by Niagara/Power Bubble, then run **Build → Run workflow** on `main`. The workflow verifies its expected signing certificate before publishing. Without this secret, CI uploads a review APK artifact and explains the missing secret in its job summary; it does not publish a release. Reuse the same key to update an existing installation. Keep the key out of the repository. A review APK signed with a different local key must be uninstalled before switching to the stable build.

CI APK version codes increase with the workflow run number, and releases use `v0.2.<run number>` with `loud-0.2.<run number>.apk` assets.

## Build and design reference

Android 12+; compile/target SDK 36; Kotlin 2.2.20; Gradle 8.14.3; AGP 8.13.0; Compose BOM 2025.09.00; manual DI, Room/KSP and DataStore. CI uses Temurin 21; local builds require Android SDK 36 and JDK 17+.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

`preview/index.html` is the historical, offline sample-data design reference. Its browser interactions do not validate native launcher behaviour. Android Studio Compose previews remain available in `ui/Previews.kt`.

See [design decisions](docs/DESIGN.md), [milestones](docs/PLAN.md) and [validation](docs/VALIDATION.md). Bundled fonts are SIL OFL licensed; license texts live in `docs/`.
