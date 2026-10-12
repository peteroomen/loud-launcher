# Loud milestones

- [x] Phase 0: inspect Niagara, agree repo/navigation/Power Bubble/theme canvas, receive references.
- [x] Phase 1: two interactive design directions, five palettes, native theme tokens, every supported tile-size preview across two themes, sample-data gallery.
- [ ] User visual review: choose/refine direction before connecting live functionality.
- [ ] Phase 2a: HOME registration, reuse app discovery/cache, search/ranking, A–Z scrubbing, shortcuts, hide/rename, return-home handling.
- [ ] Phase 2b: tile grid editing, drag/resize/recolour, Room layout/history, DataStore settings, JSON backup/restore.
- [ ] Phase 2c: weather, synced calendar, media sessions, notifications, frequency, browser/dev shortcuts; widget hosting and installed-provider checks.
- [ ] Phase 2d: approved extra tiles; Power Bubble matching appearance in its repo; optional wallpaper and decorations.
- [ ] Daily-use validation: permission states, large text, TalkBack, battery behaviour, startup traces/benchmarks and phone testing.

## Release convention

Mirror Niagara's Gradle wrapper, Kotlin/Compose/SDK versions and GitHub Actions APK delivery. Build job runs unit tests, Android lint and assembleDebug. If the existing shared DEBUG_KEYSTORE_BASE64 secret is provisioned in this repo, verify its certificate and publish versioned releases on main for Obtainium. Without it, provide an Actions APK artifact only; do not publish throwaway-signed releases.

## Current review boundary

No device integrations are active. Android app is `Loud Preview`, application ID `io.github.peteroomen.loud`, with a normal LAUNCHER entry only. Same ID can become the actual launcher after approval, preserving the eventual upgrade path.
