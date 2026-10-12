# Loud / Phase 1 design review

Two proposals share the same content, grid and behaviours. Neither has been selected as the final default. The previews intentionally use sample data and do not request permissions or replace the home app.

## Directions

**Square Punch** follows the Snapmod reference: zero corner radius, 3dp ink outlines, 5dp down-right hard shadows, coloured clock and widget cards, condensed headings, boxed uppercase labels. It feels like a stack of printed cards.

**Round Paper** follows the rounded cream reference: 19dp corners, 2dp outlines, 4dp down-right shadows, cream clock face, broader grotesk typography and highlight-strip labels. Controls echo the rounded cards. It preserves strong colour and clear outlines without turning into Material defaults.

Both are reviewable in the inline preview and `preview/index.html`. Switch directions with the design picker; use LIVE/APPS/SEARCH and the appearance button. Browser preview interactions include search, palette changes, music play/pause, tile state examples, dot-grid decoration, compact notifications and a sample layout editor. No device APIs are called.

## Typography

- **Barlow Condensed Bold**: Square Punch headings and clock. Its condensed proportions match the reference and leave room for app names. Chosen over ultra-narrow poster fonts for legibility.
- **Space Grotesk**: body and Round Paper display. Softer without becoming handwritten or ornamental.
- **Space Mono**: small metadata, time stamps and uppercase tags.

Android bundles these fonts; no font network calls. SIL Open Font License files are included here. Browser review embeds the same fonts, so it works offline. The standalone review also bundles Lucide icons (license included); the inline review uses the host icon renderer.

## Tokens

| Token | Square Punch | Round Paper |
|---|---|---|
| Card corner | 0dp | 19dp |
| Border | 3dp | 2dp |
| Shadow | 5dp / 5dp, no blur | 4dp / 4dp, no blur |
| Card padding | 14dp | 14dp |
| Grid gap | 10–14dp, plus reserved shadow | Same |
| Tap target | Minimum 48dp | Minimum 48dp |
| Press | Translate into shadow, 90ms | Same |

`ui/theme/Tokens.kt` defines five palettes: Taxi, Riso, Acid, Night Brutal and Candy. Shape and palette are independent. Night uses light canvas text, pale outlines and light-filled cards with dark text. All token text/fill combinations and allowed per-tile fill overrides must meet 4.5:1 contrast. The JVM test enforces this.

Canvas follows the chosen theme colour. Wallpaper is optional and never required for the design to work. Dot-grid is optional. No glass, blur or wallpaper sampling is carried over from Niagara.

## Layout and navigation

Four logical columns. Tile height is a minimum, not a fixed clipping box: content grows at 1.3 font scaling. Supported tile sizes are declared per tile in `TileKind.sizes`; unsupported sizes are never offered. Shadows get real space in the layout.

Default order: clock; weather + note; music; calendar; compact Teams notification summary; dev shortcuts. Widget positions do not change when data changes. A user-selected compact state is preferred to automatic collapsing.

LIVE is a scrolling canvas. APPS is an A–Z list. A persistent SEARCH action opens app search. Bottom-origin swipe-up belongs to the drawer; in-canvas vertical drags belong to scrolling. App list reference includes an alphabet scrubber: the browser mock shows its placement, while interactive scrubbing is a Phase 2 feature. Home returns to LIVE and dismisses search after HOME registration is implemented.

## Screen and tile coverage

Native gallery: Home, Apps, Search, Appearance, Edit layout, Tile gallery, Permissions. `Previews.kt` supplies every screen and every declared tile size across Taxi/Night and Square/Round, plus 320dp and 1.3 font-scale previews. Gallery cycles Content, Loading, Empty, Error, Permission states.

Required tiles: clock, weather, calendar, music, frequent apps, web, dev, app, notifications, note. Proposed optional battery/focus/habit tiles are included for review, not assumed approved for the final scope. Focus timer demonstrates a visual start/pause state only.

Calendar 4×3 uses a month grid; 4×2 uses week and next event; 2×2 uses next event. Dates/events/weather/music are fixed fictional sample data. Third-party widgets have a branded outer frame, but their internal appearance belongs to their provider.

## Integration decisions for later wiring

- Copy well-bounded Niagara backend pieces rather than coupling two repos through a shared library initially.
- Keep Kotlin/Compose, manual DI, SDK 31 minimum, target/compile 36, current Gradle/Kotlin versions and APK/Obtainium release conventions.
- Keep Preferences DataStore for settings; introduce Room for layout/history.
- Preserve hidden-app discovery after two search characters; label this plainly in settings.
- Calendar reads synced device calendars with explicit calendar permission. Teams starts with active notification summaries and app links. Include Android widget hosting, checking actual installed providers; do not promise that Teams exposes a widget. Graph integration is separate, opt-in and subject to account consent.
- Keep Power Bubble for shade/lock actions. Add an opt-in Neubrutal appearance to that separate app later. The preview demonstrates appearance only; no Power Bubble code has been changed.
- Web history includes only launches through Loud; support manual pins and later Share-to-Loud. No Chrome history scraping.
- Weather: cached Hamilton NZ default, Celsius, optional coarse location. No live networking during design review.

## Motion, accessibility and review boundaries

Hard-shadow press animation follows Compose's system animation scale. Preview controls never run persistent animation loops. Device haptic feedback is used for clickable surfaces; final settings will include a global haptics toggle. Long app names wrap; tile heights can grow. Individual interactive controls retain their own accessible labels and focus semantics.

This is a design review, not a working launcher. No permissions, live data, installed-app discovery, saved layout, gesture interception, hosted remote widget, or device locking are connected. HOME registration follows approval. Native artwork is intentionally simplified; browser disc art is CSS, not real album artwork. Native app row symbols are placeholders.

## Review priorities

1. Square Punch versus Round Paper as the default.
2. Tile density and typography at actual phone scale.
3. Palette preference, especially Night Brutal.
4. Whether battery/focus/habit deserve home space.
