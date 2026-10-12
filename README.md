# Loud

A maximalist Android launcher, currently at **Phase 1: design review**.

Two directions: **Square Punch** and **Round Paper**. Same content, five palettes, different geometry and typography. Background colour is theme-driven; wallpaper is optional later.

## Review

- Open `preview/index.html` in a browser. Choose a direction, try LIVE/APPS/SEARCH and appearance settings. All data is illustrative.
- After publication and a successful CI run, download **loud-preview** from the latest successful [Build workflow](https://github.com/peteroomen/loud-launcher/actions/workflows/build.yml), unzip and install the APK. It appears as **Loud Preview** and does not replace your home app.
- Android Studio: run the app or open `ui/Previews.kt` for per-screen and per-size Compose previews, including large text.

The native gallery includes appearance, tile resizing/recolouring samples, permission layouts, all tiles and loading/empty/error states. No live accounts, permissions or device actions are connected.

## Local review bundle

The review bundle contains `loud-preview.apk`, `preview/index.html` and the source project. The APK uses a local debug signing key; moving to a future stable CI signing key may require uninstalling this review build first. It never registers as HOME or replaces Niagara.

## Build

Android 12+; compile/target SDK 36; Kotlin 2.2.20; Gradle 8.14.3; AGP 8.13.0; Compose BOM 2025.09.00. Mirrors `peteroomen/niagara-like-launcher`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI supplies the Android SDK. Local builds require Android SDK 36 and JDK 17+. CI uses Temurin 21 as Niagara does. Provision the existing `DEBUG_KEYSTORE_BASE64` secret to enable stable-signed releases for Obtainium; without it CI only uploads an artifact.

See [design decisions](docs/DESIGN.md), [milestones](docs/PLAN.md) and [validation](docs/VALIDATION.md). Bundled fonts are SIL OFL licensed; license texts live in `docs/`.
