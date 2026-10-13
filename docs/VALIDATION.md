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

The repo also includes a JVM test for every text/fill pairing, including per-tile colour override fills.

## Historical Phase 1 native validation

`testDebugUnitTest lintDebug assembleDebug` completed successfully locally with Temurin 21 and Android SDK 36. Palette test: 1 test, 0 failures/errors. An installable debug APK was produced. Compose previews include 320dp and 1.3 font scale cases; these declarations alone are not a device test. No emulator/phone behaviour or real launcher performance has been measured. The local review APK is signed with a local debug key; a future CI APK signed with the shared stable key may require uninstalling this review build first.

## Historical Phase 1 publication

Initial source commit exists locally. Public GitHub push was blocked by automatic approval review pending explicit permission to publish source/workflow to the public repository. No remote commit or CI build is claimed.

## Functional launcher validation

Build stack: Kotlin 2.2.20, AGP 8.13.0, Gradle 8.14.3, compile/target SDK 36, min SDK 31, Compose, manual DI, Room/KSP, DataStore and WorkManager. The manifest registers HOME/DEFAULT, launcher entry, notification listener and consent-dependent widget binding. App data is excluded from Android cloud and device-transfer backup; explicit JSON export/import is the supported portable backup.

Implementation native gate is pending integration:

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
