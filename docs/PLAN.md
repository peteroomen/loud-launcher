# Loud milestones

- [x] Phase 0: inspect Niagara, agree repo/navigation/Power Bubble/theme canvas, receive references.
- [x] Phase 1: two interactive design directions, five palettes, native theme tokens and sample-data gallery.
- [x] User visual review: keep the agreed selectable Square Punch and Round Paper shape options.
- [x] Phase 2a implementation: HOME registration, installed app/profile discovery and icon cache, search/ranking, A–Z navigation, shortcuts, hide/rename and return-home handling.
- [x] Phase 2b implementation: tile reorder/resize/recolour, Room layout/history, DataStore settings and validated JSON backup/restore.
- [x] Phase 2c implementation: cached weather, synced device calendar, media sessions, notifications, app frequency, browser/developer shortcuts and consent-based Android widget hosting.
- [x] Phase 2d implementation: notes, battery, focus/habit tiles, optional wallpaper/decorations, and matching Power Bubble appearance in its own repository.
- [x] Native build gate: commit `5c03b121` passed Kotlin compilation, 16 JVM tests, Android lint and APK assembly in GitHub Actions.
- [ ] Daily-use acceptance: permission grant/revocation, widget cancellation/restoration, large text, TalkBack, insets, keyboard, battery behaviour and phone testing.
- [ ] Performance measurement: startup traces, benchmarks and real-device launcher performance.

Implementation checks above describe delivered source, not completed phone acceptance.

## Release convention

Mirror Niagara's Gradle wrapper, Kotlin/Compose/SDK versions and GitHub Actions APK delivery. Build job runs JVM tests, Android lint and assembleDebug. Checkpoint branches build without publishing releases. With the existing shared `DEBUG_KEYSTORE_BASE64` secret, CI verifies its certificate and publishes versioned main-branch releases for Obtainium. Without it, CI supplies an Actions APK artifact only and explains the missing secret in the job summary.

## Current boundary

The Android app is `Loud`, application ID `io.github.peteroomen.loud`, and exposes HOME/DEFAULT plus a normal launcher entry. Source/device integrations are connected; native runtime acceptance remains pending. The browser preview stays a historical sample-data design reference.
