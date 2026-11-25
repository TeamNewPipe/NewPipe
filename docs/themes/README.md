# Creating WizeStream theme packs

Theme packs are portable, **data-only JSON files**. Version 1 provides named palette recipes.
Version 2 adds glass surfaces, native listening-player templates and embedded PNG
skins/artwork/icons. Version 3 adds custom `#RRGGBB` palette seeds in the existing accent
fields and retains the v2 extensions. All three versions can be imported. Older apps
reject versions they do not support instead of silently discarding custom colors.
Version 2 packs require WizeStream 1.35.0 or later. Use version 1 palette recipes
when sharing packs with older app versions.

Themes never execute HTML, CSS, JavaScript, native code or plugins. They cannot add
network requests, replace playback logic, change permissions, or hide required controls.
The native renderer owns accessibility, touch targets, focus, navigation and player state.
This is the extension foundation for #1105 and #1130, not an arbitrary layout engine.

## Import, preview, export and recovery

1. Open **Settings → Appearance → Theme packs**.
2. Choose **Import theme pack**, then select a `.json` file with Android's file picker.
3. Inspect the day/night palette cards, listening-style sample and custom icon row.
   Glass previews sample the native player illustration. The main app samples its page
   content or the pack's `skin` image instead.
4. Choose **Apply theme**. Cancel/Back leaves the active appearance unchanged.
5. Reopen existing player screens after applying or restoring a theme. Appearance
   changes use the existing activity refresh flow; an already-created player may
   retain its previous pack until recreated.

**Export current theme** includes all embedded assets and overrides. Without an active
pack it exports a v1 recipe for regular named appearance settings, or a v3 recipe
containing the exact custom color. Importing that v3 recipe does not depend on the
recipient's saved custom color. **Restore previous
appearance** removes the active pack and restores those settings; applying a pack
never overwrites them. A malformed restored pack falls back to regular appearance.

Packs are stored locally as part of app preferences and included in settings backups.
They are not part of device-sync profile settings. Import preview/export payloads are
kept outside Android's saved-state Bundle; only small cache identifiers survive screen
recreation. If Android clears that cache or a pending draft is over a day old, reimport
or restart the export. The active applied pack is not stored in that cache.

The bundled **Glass Record** preset is a complete v2 example, including original
geometric artwork and icons, in
[`app/src/main/assets/themes/glass-record.json`](../../app/src/main/assets/themes/glass-record.json).
The built-in **Glass** preset keeps the standard player and enables live blur
behind the top and bottom bars while scrolling What’s New on supported phones.
The Forest, Ocean and Midnight presets remain v1 examples.

## Minimal v2 example

Save this exact UTF-8 text as `record.json`. It needs no external assets:

```json
{
  "format": "wizestream-theme",
  "version": 2,
  "name": "Quiet Record",
  "author": "Your name",
  "mode": "system",
  "night": "dark",
  "lightAccent": "blue",
  "darkAccent": "cyan",
  "glass": { "blur": 8, "tint": 80, "radius": 16 },
  "player": { "layout": "vinyl" }
}
```

Omit `glass` for normal opaque navigation. Omit `player` for the existing visualizer.
For a v1-compatible palette-only pack, remove every v2 extension and set `version` to 1.
Do not simply change the version while keeping extension fields: it will be rejected.

## Root fields

Unknown and duplicate keys are rejected at every nesting level. Fields marked optional
can be omitted, but cannot be `null`. JSON comments, trailing commas and trailing data
are not supported. Numbers must be integer literals: `2`, not `2.0`, `2e0` or `"2"`.
Arrays are not part of this format. Boolean values are accepted only for fields that
explicitly allow them, such as `glass.scrollUnderBars`. Key spelling is case-sensitive.

| Field | Required | Value / default |
| --- | --- | --- |
| `format` | Yes | Exact string `wizestream-theme` |
| `version` | Yes | Integer `1`, `2` or `3` |
| `name` | Yes | Nonblank display name, 1–80 UTF-16 code units |
| `author` | Yes | Nonblank creator name, 1–80 UTF-16 code units |
| `mode` | No | `system` (default), `light`, `dark`, `black`, `eye_comfort` |
| `night` | No | `dark` (default) or `black`; used by `system` at night |
| `lightAccent` | No | Palette name or v3 `#RRGGBB` seed; default `wizestream` |
| `darkAccent` | No | Palette name or v3 `#RRGGBB` seed; default `wizestream` |
| `glass` | v2/v3 only, optional | Object described below |
| `skin` | v2/v3 only, optional | Asset ID used behind main navigation surfaces |
| `player` | v2/v3 only, optional | Native listening template and asset references |
| `icons` | v2/v3 only, optional | Supported player icon slots → asset IDs |
| `assets` | v2/v3 only, optional | Asset IDs → canonical base64 PNG strings |

Names/authors cannot contain control characters or Unicode bidirectional embedding,
override or isolate controls (U+202A–U+202E, U+2066–U+2069). Emoji count as UTF-16
code units, so some occupy two. Keep attribution/license information on your download
page; there is no arbitrary metadata field in the file.

Accent names: `wizestream`, `neutral`, `green`, `blue`, `cyan`, `purple`, `yellow`,
`orange`, `pink`, `red`, `follow_system`. Version 3 also accepts exactly six hexadecimal
digits prefixed by `#`, for example `"lightAccent": "#0061A4"`; letter case is normalized.
Keep version 1 or 2 for packs that use only named accents. V1/v2 packs with HEX values
and the preference-only name `custom` are rejected.

A custom color is a **Material palette seed**, not an exact override for every control.
Light and dark modes derive readable tonal roles from that seed, so dark red accents
can be lighter than the entered color. Activities, player controls and import previews
use the same seed. Generated palettes require Android 12 or newer with Material dynamic
color support. Older or unsupported devices use the closest built-in accent palette,
with native light/dark contrast and black surfaces; Appearance settings explain this
fallback. The original HEX seed remains saved and exported unchanged. The same fallback
is used if Android cannot install a generated palette.

Named accents continue to use the existing native palettes. `follow_system` uses Material
dynamic colors on supported devices; otherwise native fallback colors apply. Eye Comfort retains its
warm palette rather than applying accent overrides. Black mode retains black surfaces.
`night` has no effect when `mode` is fixed to another appearance.

## Glass surfaces

| `glass` field | Default | Allowed values |
| --- | --- | --- |
| `blur` | 8 | Integer 0–16; radius in the downsampled backdrop, 0 disables blur |
| `tint` | 80 | Integer 60–95; surface-color overlay opacity in percent |
| `radius` | 16 | Integer 0–32; corner radius in density-independent pixels |
| `scrollUnderBars` | false | Boolean; let What’s New scroll behind the main toolbar and bottom navigation on supported phones |

Glass applies to the **main toolbar container, bottom navigation, navigation rail and
navigation drawer**. Each uses its current palette's surface tint and a subtle native
border. Text/icons remain sharp; the renderer blurs a separate backdrop, never the
foreground view. Individual opaque children (such as a drawer header or search field)
may retain their own surface. Settings pages, dialogs, popup windows, list cards and
video overlays are not glass targets in this revision.

Without `skin`, the drawer samples the underlying page at its screen position.
Bars normally sample the nearest page edge. With `scrollUnderBars: true`, What’s
New can scroll beneath the bars in portrait mode on phones using bottom navigation.
The app restores the normal insets during contextual search, Incognito, and while
the player is visible. Tablets, Android TV, and low-memory devices retain their
normal layout. With `skin`, the embedded image supplies the backdrop.

A `skin` without `glass` still gets an 80% surface tint, with no blur or rounded border.

Rendering is bounded to at most 96 pixels on the longest backdrop side, no more than
once per 125 ms during view redraws, with no perpetual animation timer. The renderer
uses a small separable CPU blur, available on supported Android versions; it does not
need Android 12 RenderEffect. It releases snapshots when detached. Low-RAM devices
use opaque surfaces. Main navigation also becomes opaque while a player overlay is
visible, because SurfaceView video cannot be accurately captured with this renderer.
For live page backdrops, thumbnail requests use software-compatible bitmaps in the
existing bounded image cache. A separate memory-cache key prevents reusing a hardware
bitmap loaded before the theme changed. A native view that cannot be sampled also
falls back to opaque. No video frames, screenshots or images leave the device. There
is no blur permission.

## Listening player templates

| `player` field | Default | Meaning |
| --- | --- | --- |
| `layout` | `standard` | `standard`, `vinyl`, `pocket`, `cassette` |
| `background` | None | Asset stretched across the listening canvas with a dark readability scrim |
| `artwork` | None | Asset cropped to a circular record label, pocket display, or cassette reels |

Enter the existing **Listen mode** from the main player to see the template. Leaving
Listen mode restores video. A custom layout (or `background`) replaces the visualizer
canvas in that mode; it does not change the stream, queue, audio effects or playback
controls. The ordinary standard layout without a background keeps the visualizer.
`artwork` alone has no visible effect in `standard` mode.

The vinyl disc, pocket-player body and cassette reels are native, static decorative
compositions scaled to fit portrait, landscape and small player bounds. They take
colors from the current palette. Backgrounds/artwork are bundled images, not automatic
album artwork. These are **not** animated turntables, draggable knobs or an iPod click
wheel. Play/pause, seek, repeat and other actions continue through the standard native
controls, preserving accessibility and TV focus. Background notification controls,
popup-player layouts and the separate background-player queue screen keep their existing
layout. Arbitrary positioning, CSS/HTML and new interactive control layouts are not
supported. New templates must be implemented and reviewed in native app code.

## Third-party icons

`icons` maps a semantic slot to an asset ID, for example:

```json
"icons": { "play": "triangle", "pause": "bars", "next": "forward" }
```

Supported slots: `play`, `pause`, `replay`, `previous`, `next`, `shuffle`, `repeatOff`,
`repeatOne`, `repeatAll`. These affect native video-player controls, including their
listening presentation. Missing slots retain native artwork. Play/pause/replay and
repeat variants update with playback state. Shuffle retains its native enabled/disabled
alpha. Supply every state you want to customize; never use the same indistinguishable
picture for repeat-off/one/all in a published pack.

Use transparent square PNGs with an opaque white silhouette and enough internal padding.
Native tint and state treatment remain in effect; full-color artwork is better suited
to `player.artwork`. The app retains each button's content description, click action,
focus and minimum touch area. Launcher, notification, toolbar, service and menu icons
are outside this slot registry. Theme icons do not change the app's identity.

## Embedded assets and limits

A theme is a single JSON file, **not ZIP**. Each `assets` value is a raw, standard
base64 string containing one PNG. There is no `data:` prefix and no whitespace or line
wrapping inside the string. Do not embed SVG, WebP, GIF, fonts, paths or URLs. Export a
static PNG from your editor; animation metadata has no supported meaning.

| Limit | Value |
| --- | --- |
| v1 complete UTF-8 file | 16 KiB (16,384 bytes) |
| v2/v3 complete UTF-8 file | 512 KiB (524,288 bytes), including JSON and base64 |
| Asset count | At most 6 distinct IDs |
| Individual decoded PNG file | At most 96 KiB (98,304 bytes) |
| Asset dimensions | Each side 1–1024 pixels |
| Total decoded pixel budget | At most 1,048,576 pixels across all assets |
| Icon dimensions | Each side at most 256 pixels |
| Asset ID | `[a-z][a-z0-9_-]{0,31}` |

All references must point to existing assets, even if a particular template does not
use them. Reusing one asset for several slots counts once against the pixel budget.
Unused assets still count, so remove them. PNG signature, decoder dimensions and full
decoding are checked before a pack is accepted. Limits apply to canonical export too.
Reduce image size, optimize PNG compression or remove assets when a pack exceeds them.
A huge compressed image cannot bypass the decoded-pixel budget.

Use [`embed_asset.py`](embed_asset.py) to add an existing PNG to a recipe:

```sh
python3 docs/themes/embed_asset.py record.json label artwork.png
```

Then set `"artwork": "label"` inside `player`. The helper embeds bytes and checks basic
size/signature limits; import remains the authoritative validator for decoding,
dimensions, references and the total pixel budget. The built-in Glass Record preset
shows a working skin, circular artwork and play/pause icons together in one file.

## Validation and testing before sharing

The [JSON Schema](theme-pack.schema.json) supports editor autocomplete and structural
validation for v1/v2. It cannot enforce PNG decoding, existing references, duplicate
keys, literal-number spelling, UTF-16 counting or complete-file byte size. Always test
an actual import. Never add `$schema` to a pack itself; unknown root keys are rejected.

- Test import, Cancel, Apply, export/reimport and Restore previous appearance.
- Rotate during preview and while choosing an export destination. Reopen the player.
- Check light, dark, black, Eye Comfort and system-following appearances as applicable.
- Check long titles, large font sizes, TalkBack, TV focus and portrait/landscape bounds.
- For glass, scroll a page and open the drawer; verify text remains readable and no
  stale video appears. Expect opaque fallback on low-RAM devices/active player overlays.
- For skins, enter/exit Listen mode and verify play, pause, replay, seek, queue, shuffle
  and all repeat states still work. Test missing icon slots and transparent PNG edges.
- Check older Android devices and a current version. Glass does not require Android 12.
- Keep download metadata outside the JSON: screenshots, required v2-capable build,
  author, version history, license and credits for every third-party asset.

Share the final `.json` file or a download link. Use artwork you own or have permission
to redistribute, including permission to bundle third-party icon sets. A theme file is
not a trusted executable extension; the app never downloads referenced images or code.

## Troubleshooting

**Import fails:** confirm UTF-8, exact `format`/`version`, supported fields, correct
value types, no duplicate keys/comments, valid references and all size limits. Test the
minimal example, then add one extension at a time. Export PNGs again if decoding fails.

**An older build rejects my pack:** v1-only builds intentionally reject v2. Provide a
separate v1 palette fallback; do not silently remove extensions while keeping the same
advertised capabilities.

**Glass looks opaque:** check low-RAM fallback, visible player overlays, opaque child
surfaces and tint strength. Bars sample adjacent content; they do not enable underlap.

**Skin is not visible:** enter Listen mode in a recreated main player, choose a nonstandard
layout or supply a background, and verify asset IDs. `standard` alone keeps the visualizer.

**Icons look recolored:** native tint is intentional for contrast and state visibility.
Use silhouette PNGs. Theme packs cannot turn an action icon into a different action.

**Export/recreation loses a pending preview:** Android may have evicted the draft cache.
Reimport the source file. The applied pack and regular appearance settings remain intact.
