# Validation record

## Historical Phase 1 browser review

Passed automated interaction checks in headless Chromium for both Square Punch and Round Paper:

- No JavaScript exceptions.
- No phone-surface horizontal overflow at 320, 390 and 736px viewport widths.
- Palette selection updates the home view; Night Brutal inspected visually.
- App search filters results and shows an empty state.
- Permission-state preview changes with the state selector.
- Sample editor removes/restores tiles.
- Music play/pause updates the control.

Visually inspected full-page home captures in both directions, Night Brutal, and the app drawer / appearance screen at 320px. Fixed missing standalone icons/fonts and low-contrast inner outlines in Night Brutal. Browser preview now embeds fonts and standalone icons for offline review. Screenshot captures are internal QA, not claimed as native Android screenshots.

## Palette contrast

Independently calculated WCAG relative luminance from the Kotlin token values. Lowest normal-text contrast by theme:

| Theme | Minimum ratio |
|---|---:|
| Taxi | 6.39:1 |
| Riso | 8.06:1 |
| Acid | 8.26:1 |
| Night Brutal | 9.42:1 |
| Candy | 10.75:1 |
| Pop Garden | 9.18:1 |
| Baby Blue | 10.40:1 |
| Rave | 7.01:1 |

The repo also includes a JVM test for every text/fill pairing, including per-tile colour override fills.

## Historical Phase 1 native validation

`testDebugUnitTest lintDebug assembleDebug` completed successfully locally with Temurin 21 and Android SDK 36. Palette test: 1 test, 0 failures/errors. An installable debug APK was produced. Compose previews include 320dp and 1.3 font scale cases; these declarations alone are not a device test. No emulator/phone behaviour or real launcher performance has been measured. The local review APK is signed with a local debug key; a future CI APK signed with the shared stable key may require uninstalling this review build first.

## Historical Phase 1 publication

The initial review recorded a publication permission block. This was later resolved: the foundation and implementation checkpoint source are now on GitHub. The current implementation CI results below supersede that earlier publication boundary.

## Functional launcher validation

Build stack: Kotlin 2.2.20, AGP 8.13.0, Gradle 8.14.3, compile/target SDK 36, min SDK 31, Compose, manual DI, Room/KSP, DataStore and WorkManager. The manifest registers HOME/DEFAULT, launcher entry, notification listener and consent-dependent widget binding. App data is excluded from Android cloud and device-transfer backup; explicit JSON export/import is the supported portable backup.

GitHub Actions [run 38382737470](https://github.com/peteroomen/loud-launcher/actions/runs/38382737470), job `115247081990`, passed the full native gate for commit `5c03b121eb19ab54a516545c86e72a225e24b8bb`:

- Kotlin and Room/KSP compilation passed.
- 16 JVM tests passed: 12 core/search/backup tests, 3 system-policy tests and 1 palette test; 0 failures, 0 ignored.
- Android lint: 0 errors, 40 warnings. The coarse-location permission error from the earlier checkpoint was fixed with a directly checked permission and explicit `SecurityException` handling, without a lint baseline or broad suppression.
- APK assembly and APK/report artifact uploads passed.
- Loud stable-signing and Release steps were skipped because its signing secret was absent. No Obtainium-ready Loud release is claimed until that key is provisioned and the main workflow succeeds.

Power Bubble [main run 38382990564](https://github.com/peteroomen/power-bubble/actions/runs/38382990564) built commit `ee30a497b0cec308445f24dc9deafd8922772f81`: APK assembly, Android lint and the expected shared-signature check passed. [Release v0.1.11](https://github.com/peteroomen/power-bubble/releases/tag/v0.1.11) includes `power-bubble-0.1.11.apk`. This build result does not validate real overlay/accessibility behaviour on a phone.

Native gate command:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI uploads debug APK and validation reports. GitHub Releases require the shared `DEBUG_KEYSTORE_BASE64` and verify its expected SHA-256 certificate. Obtainium source is `https://github.com/peteroomen/loud-launcher`. VersionCode is `GITHUB_RUN_NUMBER + 1`; versionName is `0.2.<run number>`. Local APKs without the shared key are review builds and may require uninstalling before a stable signed build.

### Device acceptance still required

No phone/emulator behaviour is claimed until these flows run on Android 12+:

- Choose Loud as default HOME, press Home repeatedly, switch away/back, rotate and restart the process.
- Launch installed/work-profile apps, grant/revoke calendar/notification/usage access, and verify unavailable providers.
- Add, configure, resize/remove an AppWidget; check consent cancellation and process restart do not leak IDs.
- Edit layout/preferences; export, reset/import a backup; verify malformed imports are rejected.
- Check keyboard dismissal, edge insets, TalkBack, 320dp width and large font scale on native screens.
- Refresh weather offline/online and check cache timestamps. Periodic work timing is OS managed.
- Install optional Power Bubble, explicitly enable its accessibility service, then invoke screen-off/notifications.

Browser screenshots and Compose preview declarations do not validate Android flows.
