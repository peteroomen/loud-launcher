# Phase 1 validation

## Browser review

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

## Native validation

`testDebugUnitTest lintDebug assembleDebug` completed successfully locally with Temurin 21 and Android SDK 36. Palette test: 1 test, 0 failures/errors. An installable debug APK was produced. Compose previews include 320dp and 1.3 font scale cases; these declarations alone are not a device test. No emulator/phone behaviour or real launcher performance has been measured. The local review APK is signed with a local debug key; a future CI APK signed with the shared stable key may require uninstalling this review build first.

## Publication

Initial source commit exists locally. Public GitHub push was blocked by automatic approval review pending explicit permission to publish source/workflow to the public repository. No remote commit or CI build is claimed.
