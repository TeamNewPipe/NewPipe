# NewPipe Material roadmap

> **Independent fork notice**
>
> NewPipe Material is an independently maintained fork of NewPipe focused on
> Material 3 UI modernization. It is not the official NewPipe app, is not
> endorsed by TeamNewPipe/NewPipe e.V., and must preserve clear attribution to
> upstream NewPipe and its contributors.

## Project identity

NewPipe Material exists to preserve the functionality, privacy expectations,
and service support that users expect from NewPipe while modernizing the app's
visual system toward Material 3. The fork should remain practical: UI polish is
valuable only when playback, downloads, subscriptions, settings, import/export,
and other core NewPipe behavior continue to work reliably.

Project communication should be explicit that:

- NewPipe Material is an independent maintained fork, not an official NewPipe
  release channel.
- The fork's primary product goal is Material 3 UI modernization while retaining
  NewPipe behavior wherever possible.
- User-facing pages, release notes, issue templates, screenshots, and About
  screen text must avoid implying endorsement by the official NewPipe project.
- Upstream NewPipe attribution, copyright notices, and license obligations must
  remain intact.

## Material 3 roadmap

### Completed areas

The current fork already has a Material 3 foundation underway. Keep these areas
tracked as completed unless later QA identifies regressions:

- Theme and palette foundation for Light, Dark, and Black themes.
- Toolbar modernization and neutral surface/color polish.
- Top tab styling and selected-state readability improvements.
- Card migration/polish for feed groups and related list/card rows.
- Several safe confirmation and settings dialogs migrated to Material-styled
  dialogs.
- Settings switch modernization with `MaterialSwitch` where AndroidX Preference
  binding remains compatible.
- Settings icon and text styling improvements, including summary text contrast.

### In-progress areas

Treat these as active, incremental UI workstreams:

- Continue auditing preference screens for spacing, typography, disabled-state
  contrast, icon tinting, and switch alignment in Light, Dark, and Black themes.
- Expand dialog migration only for simple confirmation flows with low behavior
  risk and clear manual test coverage.
- Continue replacing legacy card/list surface treatments with Material 3 roles
  when the change is visual-only and easy to validate.
- Keep a running QA log for each UI migration, including the affected screen,
  themes checked, commands run, and any known regressions.
- Identify reusable Material 3 style resources so future changes are isolated in
  themes/styles instead of scattered across feature logic.

### Future areas

Plan these after the current foundation is stable:

- README and release screenshots that show the Material 3 identity without
  claiming official NewPipe status.
- About screen copy that explains upstream NewPipe attribution and the fork's
  independent maintenance status.
- A consistent fork release notes format that separates upstream merges from
  NewPipe Material UI changes.
- Iconography review for user-facing fork branding after the package/name
  strategy is decided.
- Broader layout polish for empty states, error states, bottom sheets, menus,
  and service-specific screens, provided the changes remain UI-only.
- Accessibility review for dynamic type, focus order, touch targets, color
  contrast, TalkBack labels, and high-contrast Black theme behavior.

### Playback parameter dialog polish

- PlaybackParameterDialog now uses Material 3 role colors while preserving player behavior: the dialog surface uses `colorSurface`, labels use `colorOnSurface`, helper/min/max text uses `colorOnSurfaceVariant`, active controls use `colorPrimary`, dividers use `colorSurfaceVariant`, and seekbar tracks/thumbs use a dialog-local role-based style.
- Reset/apply/cancel behavior, speed/pitch values, step-size persistence, unhook/skip-silence preferences, playback callbacks, and all main player overlay controls remain unchanged.
- QA should cover speed and pitch adjustments, reset, cancel/dismiss, rotation, Light/Dark/Black, Follow system dynamic color, App default, and one manual preset.

### Player overlay controls audit

- Player overlay controls were audited and intentionally left unchanged in this pass. The main overlay uses white-on-black/translucent-black affordances over arbitrary video frames, and several sensitive player elements still use legacy/service colors: programmatic red seekbar tint, translucent red closing overlay, `colorAccent` queue controls, legacy-red popup close FAB, and `colorAccent` playback-parameter dialog controls.
- No low-risk visual-only change was applied because quality/audio/caption/speed popup menus, seekbar tint, queue controls, gestures, captions, and player mode overlays are tightly coupled to playback visibility, focus, dismissal, and gesture behavior.
- Follow-up should be a dedicated player-controls visual pass with real-device QA for fullscreen, embedded, background/audio, popup, queue, captions, speed, quality, fast seek, brightness/volume gestures, rotation, TV/desktop mode, and Light/Dark/Black plus dynamic/manual palettes.

### Video detail action area polish

- The video detail page content below the player now uses Material 3 role colors: primary title/channel/view text uses `colorOnSurface`, secondary uploader/like/meta text and action icons use `colorOnSurfaceVariant`, subtle dividers use `colorSurfaceVariant`, and the detail tab strip uses primary selected state with theme ripple.
- Add To, Background, Popup, Download, Share, Open in browser, Kodi, debug crash, description expansion, comments, and related-video behavior are preserved; the change is limited to XML/style visual styling.
- QA should cover video detail readability and every action callback across Light/Dark/Black, Follow system dynamic color, App default, one manual preset, and rotation.

### Download UI visual polish

- Download dialog surfaces now use `colorSurface`, supporting labels/metadata use `colorOnSurfaceVariant`, primary content uses `colorOnSurface`, and radio/seekbar/loading accents use `colorPrimary`.
- Follow-up contrast pass removed legacy service-red overrides from dialog theme accents, added readable enabled/disabled radio selectors, kept the stream quality row compatible with plain instrumentation themes, and moved Downloads screen mission card/progress colors to `colorSurfaceVariant`/`colorPrimaryContainer` roles.
- Downloads manager rows now keep filenames on `colorOnSurface`, move status/progress metadata, section headers, and subdued icons to `colorOnSurfaceVariant`, and set the activity/list surfaces to `colorSurface` without changing mission state, queue, pause/resume/retry/delete, storage, worker, notification, or playback behavior.
- The change preserves download destination/path handling, file picker behavior, stream/format selection, thread persistence, queue/start/cancel behavior, StreamItemAdapter behavior, and download business logic.
- QA should cover opening the dialog from video detail, switching media type/quality/audio track/subtitle options, editing filename/path UI, starting/canceling a download, empty Downloads state, active/paused/completed/failed Downloads rows, row overflow actions, Download settings, rotation, and Light/Dark/Black plus dynamic/manual theme colors. Pending and finished mission rows need real-device review because the card colors intentionally changed from file-type-specific legacy colors to Material role-based progress surfaces and the latest row-icon/status treatment is XML-only.

### Empty/error/loading state polish

- Shared and common inline empty states now use `colorOnSurfaceVariant` for kaomoji/supporting copy, keeping empty screens readable but visually quieter across Light, Dark, Black, dynamic color, and manual presets.
- The shared error panel now uses a rounded `colorSurfaceVariant` container with on-surface text roles and primary/on-primary retry/action buttons while preserving existing retry/open/report actions.
- Audited loading indicators now use `colorPrimary` progress styles so loading emphasis follows dynamic/manual theme color without changing loading state logic.
- QA should cover empty bookmarks/subscriptions/feed groups, search no-results, representative loading, and representative error/retry states, including long translated error text in the shared panel.

### Transient feedback polish

- Snackbars now use the app-level Material 3 snackbar styles: inverse surface for the container, on-inverse-surface message text, inverse-primary action text, rounded corners, and Material snackbar elevation. Existing snackbar messages, actions, callbacks, and durations are preserved.
- Legacy hard-coded yellow snackbar action overrides were removed so error/report, notification-warning, channel-subscription, settings, and history snackbars share the same themed treatment.
- Toasts remain platform `Toast.makeText(...)` messages and are intentionally unchanged because they are not safely themeable across Android versions without introducing custom toast infrastructure.
- QA should cover representative snackbars and toasts across Light/Dark/Black, Follow system dynamic color, App default, and one manual preset.

### Search UI polish

- Expanded toolbar search now uses Material 3 role colors: neutral `colorSurface` around a rounded `colorSurfaceVariant` field, `colorOnSurface` input text, `colorOnSurfaceVariant` hint/clear/suggestion icons, and `colorPrimary` for focused input accent.
- Main search suggestions and auxiliary result text now use on-surface roles while preserving suggestion selection, insertion, deletion, query submission, keyboard behavior, and result loading logic.
- Because the toolbar search layout is shared, QA should cover main search, settings search, and any feed-group search entry point across Light/Dark/Black plus dynamic/manual color presets.

### Navigation drawer polish

- Main drawer visual styling now follows the fork Material 3 role mapping: the drawer container uses `colorSurface`, selected rows use a rounded `colorPrimaryContainer` background with `colorOnPrimaryContainer` content, unselected icons/text use on-surface roles, and ripple feedback uses `colorControlHighlight`.
- Drawer destinations, menu grouping, open/close handling, and service switching remain unchanged; the change is limited to layout/color/drawable styling resources.
- Header content is preserved, with a neutral `colorSurfaceVariant` fallback behind the existing header artwork to reduce red dominance. Continue to QA service identity/header contrast across Light, Dark, Black, dynamic color, and manual presets.

### Main tabs polish

- Top main tabs continue to use the existing `ScrollableTabLayout`.
- Bottom main tabs now use a real Material `BottomNavigationView` when the
  selected tab count is five or fewer, while more than five tabs fall back to the
  existing scrollable bottom `TabLayout` so no user-selected tabs are dropped.
- Main TabLayout, BottomNavigationView, and channel TabLayout use a Material
  3-style surface container, primary-colored selected state/indicator, muted
  on-surface-variant unselected state, and theme ripple so dynamic/manual theme
  colors continue to drive selected tab emphasis.
- NewPipe Material defaults the main tabs position to Bottom for new installs or
  unset preferences only. Existing saved Top or Bottom values keep using the
  same `main_tabs_position` preference key and are not migrated or overwritten.
- Bottom navigation items are generated from the selected main tabs at runtime,
  preserving tab order, titles, and icons. The BottomNavigationView uses a
  Material 3 active indicator/pill, selected-only labels to reduce long-label
  truncation, a fixed Material-sized height so content can be constrained above
  the bar reliably, and a bottom-nav-only `Bookmarks` display label for the
  Bookmarked Playlists tab without changing tab identity. QA must cover 4-tab
  and 5-tab bottom
  navigation, more-than-5-tab fallback scrolling, ViewPager sync, rotation, and
  Light/Dark/Black plus dynamic/manual color combinations.

### Risky areas to defer

Defer or handle only with dedicated test plans because regressions here can
break core functionality:

- Player dialogs and controls, including playback parameters, queue handling,
  background/popup player transitions, and crash-recovery flows.
- Download dialogs, file-picker safeguards, storage permission flows, and
  download settings that interact with filesystem behavior.
- Shared player/download logic and any extractor, database, migration, or
  networking behavior.
- Complex adapter-backed dialogs and menus where replacing the UI component may
  change selection, lifecycle, or state restoration behavior.
- Launcher icon and signing changes until their productization decisions are
  complete.
- Java/Kotlin package renaming; the fork currently changes Android identity only
  and keeps existing source package declarations to minimize runtime risk.

## Theme color strategy

NewPipe Material should use Material You dynamic colors when available without
turning any single static color, including green, into universal app chrome. The
long-term color system should support dynamic color, explicit user choice, and a
neutral Material 3 fallback while preserving the existing Light, Dark, and Black
theme modes.

### Theme color priority

Resolve the active app accent/palette in this order:

1. **User-selected accent color, if set.** A manual choice always overrides
   system dynamic color so older Android users and users who dislike their
   wallpaper-derived palette can still control the app accent.
2. **System dynamic color, if available and enabled.** On Android 12+ and other
   dynamic-color-capable environments, the default path should follow Material
   You when the user has not selected a manual accent.
3. **Neutral Material 3 fallback palette.** If no user color is selected and
   dynamic colors are unavailable or disabled, use a neutral fallback palette
   rather than a hard-coded green app-wide treatment.

### Proposed setting

Add a future Appearance setting named **Theme color**. Proposed values:

- **Follow system**
- **App default** (stored internally as `newpipe_material`)
- **Neutral**
- **Green**
- **Blue**
- **Purple**
- **Orange**
- **Pink**
- **Red**

Default behavior:

- Android 12+ / dynamic-color-capable devices: **Follow system**.
- Unsupported devices: **App default** (the internal `newpipe_material` preset) or
  **Neutral**, depending on the final fallback palette chosen during implementation.

### Behavior rules

- **Follow system** uses Material You dynamic color when the platform and theme
  stack support it.
- Manual color choices override system dynamic color immediately after the theme
  is reapplied.
- Older Android users can still customize the app accent through static preset
  palettes.
- **Red** is allowed as an explicit user-selected accent, but red should not be
  used as generic default chrome across toolbars, tabs, switches, cards, and
  dialogs.
- Service-specific colors, warning/error colors, and content metadata colors
  should remain semantically scoped instead of being repurposed as global brand
  color.

### Implementation stages

1. **Stage 1: dynamic color support.** Implemented for supported Android
   versions/devices through the existing Material Components dynamic color API.
   Dynamic color is registered before activities are created and reapplied after
   explicit activity `setTheme()` calls, unsupported devices keep the current
   static fallback palette, and Black theme is excluded so its black surfaces
   remain visually black.
2. **Stage 2: static preset palettes and runtime resolution.** Implemented for
   App default (stored as `newpipe_material`), Neutral, Green, Blue, Purple,
   Orange, Pink, and Red. Runtime
   priority is manual preset -> system dynamic color -> existing static fallback
   palette.
3. **Stage 3: settings UI for theme color.** The Appearance setting and
   preference key/resources have been added with `follow_system` as the default,
   and the value is saved through normal preferences for export/import
   compatibility. The saved value now participates in runtime theme color
   resolution.
4. **Stage 4: optional preview chips.** Consider compact preview chips or color
   swatches in the settings UI after the underlying palette behavior is stable.
5. **Stage 5: QA matrix.** Validate dynamic color, presets, fallbacks, and
   theme switching across the UI before enabling the feature in a public
   release.

### Technical considerations

- Preserve existing Light, Dark, and Black theme support; color selection should
  choose the palette/accent inside the selected brightness mode, not replace the
  brightness mode.
- Stage 1 skips dynamic color when the selected theme resolves to Black theme;
  revisit this only if a later implementation can preserve pure black surfaces
  while applying dynamic accent roles safely.
- Continue auditing `ThemeHelper` behavior in later stages so applying manual
  colors does not introduce activity restart loops, stale resources, or partial
  theme application. The current `Theme color` preference is applied through
  static overlays; when changed from Appearance settings, the user is prompted
  to apply now by safely recreating Settings or apply later after screens are
  reopened/restarted.
- Avoid app restart bugs: switching the color should either reapply the theme
  predictably or request a controlled activity recreation with saved state.
- Maintain status bar and navigation bar contrast in Light, Dark, and Black
  themes, including gesture navigation edge cases.
- Ensure settings controls, `MaterialSwitch` widgets, preference text/icons,
  top/bottom TabLayout tabs, dialogs, cards, toolbar surfaces, and
  selected/activated states all update from the resolved palette.
- Keep a neutral fallback palette for older Android and dynamic-color-disabled
  devices so unsupported devices do not fall back to fixed green chrome.
- Define the new preference key/value format carefully so settings export/import
  remains backward-compatible and unsupported values fall back safely.
- Do not add dependencies unless a later implementation plan proves they are
  necessary; prefer existing Material Components and AndroidX capabilities.

### QA checklist

Before shipping theme color selection, verify:

- Android 12+ dynamic color in Light and Dark themes.
- Black theme remains black enough because Stage 1 skips dynamic color there.
- Older Android or dynamic-color-unavailable fallback behavior.
- Manual color override for every preset value.
- Light, Dark, and Black modes combined with dynamic and manual colors.
- Settings switches, tabs, dialogs, cards, toolbar surfaces, selected states,
  status bar, and navigation bar contrast.
- App restart/recreation after changing color, including returning to the same
  settings screen without losing state.
- Export/import of the new setting, including imports from builds that do not
  know the setting yet.
- `Theme color` setting persistence plus the Apply now/Restart/Later prompt
  behavior for activity recreation/restart timing.

## Fork productization checklist

Before presenting NewPipe Material as a user-installable maintained fork, make
explicit decisions for each item:

- **App name:** Chosen as **NewPipe Material** for the visible launcher/app
  label.
- **App icon:** Design fork-specific launcher and notification-safe branding
  that does not confuse users into thinking it is the official NewPipe app.

### Launcher icon audit and NewPipe Material icon plan

Current launcher icon structure:

- The app manifest points `android:icon`, `android:logo`, and
  `android:roundIcon` to `@mipmap/ic_launcher`. The round icon intentionally
  reuses the adaptive launcher icon for this first XML/vector-only pass instead
  of introducing a separate round-icon resource.
- Android Auto notification metadata also references `@mipmap/ic_launcher`, so
  any future launcher-icon replacement must be checked for notification-safe
  silhouette/readability before release.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` is the adaptive icon
  definition used on Android 8.0+. It uses `@color/ic_launcher_background` for
  the adaptive background, `@drawable/ic_launcher_material_foreground` for the
  foreground, and `@drawable/ic_launcher_material_monochrome` for Android
  themed icons.
- `@color/ic_launcher_background` is now defined as `#202124`, replacing the
  upstream/NewPipe red background with a neutral Material-friendly dark surface
  that is not fixed to a green-only fork color.
- Legacy fallback launcher PNGs are present in `mipmap-mdpi`, `mipmap-hdpi`,
  `mipmap-xhdpi`, `mipmap-xxhdpi`, and `mipmap-xxxhdpi` as `ic_launcher.png`.
  Foreground PNGs with the same density buckets are present as
  `ic_launcher_foreground.png` for the adaptive foreground resource.
- No separate `ic_launcher_round` resource was added. The adaptive icon is used
  for both standard and round launcher references for now.
- Android themed-icon support now uses the dedicated one-color vector
  `app/src/main/res/drawable/ic_launcher_material_monochrome.xml` instead of
  reusing the full-color foreground.
- There is no `drawable-v24` resource directory in the current tree. Launcher
  icon XML inputs are concentrated in `drawable`, `mipmap-anydpi-v26`, and the
  launcher background color in `values/colors.xml`.

Proposed NewPipe Material icon direction:

- Create a distinct fork mark that communicates **NewPipe Material** as an
  independent maintained fork, not an official NewPipe build or endorsement.
  Avoid reusing the official red play-pipe composition as the primary brand
  shape without clear visual differentiation.
- Keep the concept video/player-adjacent, but simplify it into a Material 3
  compatible geometric symbol: for example, a rounded play form, layered
  material surface/card motif, or abstract media tile that reads clearly at
  small launcher sizes without copying upstream branding.
- Prefer a neutral/dynamic-friendly base: avoid hard-coding the final identity
  as green only. Use neutral surfaces with a configurable/accent-friendly
  foreground or a palette that can sit beside the app's dynamic/manual Material
  colors without implying the launcher icon itself follows every in-app theme
  color.
- Keep adaptive icon safe zones in mind: the foreground must remain legible
  after circle, squircle, rounded-square, and other OEM launcher masks. The
  background should be simple enough to survive launcher scaling and should not
  contain essential detail.
- Add a purpose-built monochrome/themed icon layer instead of reusing the full
  color foreground. It should be a single-color silhouette that remains readable
  in Android 13+ themed icon light and dark launcher modes.
- Treat notification and automotive use separately during QA because the current
  manifest metadata also points Google car notification icon metadata at the
  launcher resource. If a future notification-specific icon is needed, plan it
  deliberately rather than relying on a detailed launcher foreground.

XML/vector-only pass completed:

- Replaced the upstream red adaptive icon background with neutral `#202124`.
- Added `app/src/main/res/drawable/ic_launcher_material_foreground.xml` as the
  first fork-specific full-color foreground mark: a simple layered media tile
  with a play shape, designed to remain centered within adaptive icon safe zones.
- Added `app/src/main/res/drawable/ic_launcher_material_monochrome.xml` as a
  clean one-color silhouette for Android themed icon recoloring.
- Updated `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` to use the new
  foreground and monochrome vector drawables while keeping the existing adaptive
  icon resource name.
- Added `android:roundIcon="@mipmap/ic_launcher"` because a safe adaptive
  launcher resource already exists. A separate `ic_launcher_round` resource is
  still optional future work only if launcher previews show a need for it.
- Left all legacy raster fallback PNGs unchanged. Regenerating `ic_launcher.png`
  and `ic_launcher_foreground.png` in the density-specific `mipmap-*` folders is
  pending until the XML/vector direction is reviewed and approved.

Future XML/vector implementation notes:

- The first XML/vector implementation now uses the existing
  `@color/ic_launcher_background` name with a neutral value, plus dedicated
  foreground and monochrome vector drawables. Future iterations can refine these
  XML resources without touching app logic.
- Do not make the background a permanent green-only brand color; keep it neutral
  or otherwise accent-compatible so the launcher identity works beside Material
  You expectations.
- Raster legacy fallback icons should be generated only after the vector design
  is approved, so density PNGs remain consistent with the final adaptive icon.

Future icon implementation checklist:

- Review and refine the first XML/vector adaptive icon foreground.
- Review and refine the neutral adaptive icon background color/shape treatment.
- Validate the dedicated monochrome/themed icon silhouette on Android 13+ launchers.
- Decide whether a separate `ic_launcher_round` resource is needed or whether
  `android:roundIcon="@mipmap/ic_launcher"` is sufficient.
- Regenerate legacy fallback launcher icons for pre-adaptive launchers if needed.
- Preview launcher masks across circle, squircle, rounded-square, and OEM shapes.
- Check Android themed icons in both light and dark launcher modes.
- Check install/update, recents/task switcher, Android Auto notification metadata,
  and any store/release artwork that could still imply official NewPipe
  endorsement.
- **Package/applicationId decision:** Chosen as
  **`org.wisso.newpipematerial`**. This Android applicationId lets NewPipe
  Material install as a separate app beside official NewPipe while preserving
  the existing Java/Kotlin package and namespace structure for now.
- **Signing and release channel:** Establish fork-owned signing keys, document
  key custody, and choose release channels before publishing APKs.
- **README screenshots:** Replace or supplement screenshots only after the UI
  direction is stable and captions clearly identify this as NewPipe Material.
- **About screen attribution:** Add clear upstream attribution, license links,
  fork maintainer information, and an independent-fork disclaimer before public
  releases.
- **License compliance:** Preserve GPL notices, source availability, copyright
  notices, dependency notices, and any artwork/license requirements.
- **Issue templates and support policy:** Create fork-specific bug report and
  feature request templates that route NewPipe Material issues to this fork and
  avoid burdening upstream NewPipe maintainers with fork-only regressions.

## Upstream maintenance strategy

NewPipe Material should remain maintainable by minimizing divergence from
upstream NewPipe:

- Keep an `upstream` Git remote pointing at the official NewPipe repository.
- Regularly merge or rebase from upstream `dev` on a predictable cadence, such
  as weekly during active upstream development and before every fork release.
- Prefer small, reviewable Material 3 commits that touch themes, styles,
  layouts, and fork documentation instead of feature logic.
- Keep Material changes isolated where possible behind reusable styles,
  resource overlays, and narrowly scoped UI components.
- Avoid unnecessary behavior changes, especially in playback, downloads,
  subscriptions, database migrations, extractors, and network handling.
- Document every non-trivial upstream conflict in a maintenance note: affected
  files, conflict reason, chosen resolution, and follow-up QA.
- After each upstream merge/rebase, run the fork QA checklist and verify that
  Material 3 resources still apply correctly across Light, Dark, and Black
  themes.

## Compatibility and data strategy

The fork now uses `org.wisso.newpipematerial` as its Android applicationId for
side-by-side installation with official NewPipe:

- NewPipe Material and official NewPipe install as separate Android apps.
- Android keeps separate app data, permissions, shortcuts, backups, and update
  paths for `org.wisso.newpipematerial` and official NewPipe.
- Data is not shared automatically between the apps. Users should use NewPipe's
  supported export/import flows to move subscriptions, playlists,
  history/settings exports, and other supported data where formats remain
  compatible.
- Automatic data migration is intentionally deferred until it is designed,
  reviewed, and tested as a dedicated product decision.
- Java/Kotlin source package renaming is intentionally deferred; package
  declarations remain in their existing upstream namespaces to reduce merge
  conflicts and avoid behavior risk.
- Clearly warn users when importing data from another build or fork could be
  unsupported or risky.

## QA and release checklist

No Gradle validation is required for this documentation-only roadmap change, but
future code or resource changes should use the checklist below before release.

### Programmatic checks

- Build the app variant intended for testing or release.
- Run lint for the changed variant.
- Run unit tests affected by the change.
- Run checkstyle/format checks required by the repository.
- For upstream merges, run a broader verification pass even if the fork changes
  are UI-only.

### Manual UI matrix

Validate visual and behavioral correctness in:

- Light theme.
- Dark theme.
- Black theme.
- Portrait and landscape where the screen supports both.
- Small, normal, and large font/display sizes.
- Gesture navigation and three-button navigation when relevant.

### Core behavior checks

Before a public NewPipe Material release, manually verify:

- Playback: stream playback, background playback, popup playback, queue actions,
  seeking, audio/video switching, and player restoration.
- Downloads: download dialog, format selection, storage location, paused/resumed
  downloads, completed downloads, and failure recovery.
- Subscriptions: channel subscription/unsubscription, feed loading, refresh,
  groups, and notification-related settings.
- Settings: Appearance, Content, Video/Audio, Download, History, Notification,
  Privacy, backup/restore paths, preference category/header contrast,
  preference title/summary/icon colors, ListPreference dialogs, and the
  Appearance theme-color restart prompt.
- Import/export: settings export/import, database export/import, subscriptions,
  playlists, and failure handling for incompatible files.
- Android versions: minimum supported API, current target API behavior, and at
  least one recent Android release.
- OEM skins: stock Android plus common customized environments such as Samsung,
  Xiaomi/HyperOS, OnePlus/OxygenOS, and GrapheneOS or another privacy-focused
  Android distribution when available.

## Immediate follow-ups

- Keep future identity changes focused and separately reviewed.
- Do not change launcher icons, signing, version code/name, or runtime behavior
  until those decisions have dedicated implementation plans.
- Continue using this file as the checklist for future NewPipe Material planning
  issues and pull requests.

### Current Material polish note: playlists, subscriptions, and feed groups

- Continue keeping playlist, subscription, and feed-group visual changes scoped to
  resource-level Material role mapping unless a separately reviewed behavior task
  requires code changes.
- Manual QA for this surface should cover subscription group create/edit/delete/
  reorder/selection, playlist create/rename/delete/bookmark/unbookmark, Add to
  playlist, row title/summary/icon contrast, rotation in dialogs, and Light/Dark/
  Black plus dynamic/App default/manual palettes.
- Preserve playlist/subscription/feed database, import/export, playback,
  downloads, navigation, settings, and player overlay behavior while iterating on
  this visual surface.

### Current Material polish note: search and common list rows

- Keep search/list-result polish focused on resource-level Material role mapping:
  row titles should use `colorOnSurface`, supporting uploader/view/date/count and
  helper text should use `colorOnSurfaceVariant`, and actions/important accents
  should continue using established Material roles.
- Manual QA for this surface should cover the main feed/home list, video search
  results, channel and playlist search results, channel tabs, remote playlist
  rows, empty/loading/error states where practical, row title/metadata/icon
  contrast, rotation, and Light/Dark/Black plus dynamic/App default/manual
  palettes.
- Preserve adapter behavior, extractor/service logic, search/list loading,
  stream/channel/playlist opening, playback, downloads, queue/player overlay,
  navigation, database behavior, and settings behavior while iterating on this
  visual surface.

### Current Material polish note: empty, loading, no-content, and retry states

- Keep empty/loading/error polish focused on resource-level Material role mapping:
  visible empty/no-results titles should use `colorOnSurface`, helper text and
  subdued glyphs/icons should use `colorOnSurfaceVariant`, and retry/action
  affordances should use established primary-role action treatment.
- Manual QA for this surface should cover fresh/empty main or feed states,
  nonsense-query no-results, settings-search no-results, subscriptions/bookmarks/
  playlists empty states, generic error/retry panels, loading indicators, title/
  helper/icon/action contrast, rotation, and Light/Dark/Black plus dynamic/App
  default/manual palettes.
- Preserve error handling, retry behavior, loading behavior, search/list behavior,
  network/extractor/service logic, playback, downloads, queue/player overlay,
  navigation, database behavior, and settings behavior while iterating on this
  visual surface.

### Generic dialog and picker polish

- Generic text-input dialogs and safe custom picker rows now use Material 3 role colors: primary labels on `colorOnSurface`, helper/secondary text and subdued picker icons on `colorOnSurfaceVariant`, dividers and PeerTube instance row strokes on `colorSurfaceVariant`, and action affordances/FAB icons on primary roles.
- This pass deliberately stayed in XML/resource styling for generic app and picker dialogs. Behavior, validation, import/export flows, service/network/extractor logic, playback, downloads, queue/player overlays, navigation, database behavior, settings behavior, playback speed dialog, and download dialog remain out of scope.

### Current Material polish note: toolbar, app bar, overflow, search action, and tabs

- Keep app-bar and menu polish focused on XML/theme role mapping: toolbar surfaces should remain neutral `colorSurface`, title/action icon text should use `colorOnSurface`, subtitles/hints/subdued popup icons should use `colorOnSurfaceVariant`, selected tab indicators should use `colorPrimary`, and ripple/highlight behavior should continue to use the existing control highlight role.
- Manual QA for this surface should cover main/search/video/detail/channel/playlist/settings/feed-group/about/ReCAPTCHA toolbars, overflow menus, expanded search text/hint/clear controls, top tabs and detail tabs, rotation during search/menu screens, and Light/Dark/Black plus dynamic/App default/manual palettes.
- Preserve toolbar/menu/search/navigation behavior, search query and service-selection handling, playback, downloads, queue/player overlay, database behavior, settings behavior, extractor/service logic, playback speed dialog, and download dialog behavior while iterating on this visual surface.

### Current Material polish note: notification/update/debug settings surfaces

- Keep notification, update, debug, and report-adjacent polish focused on Material role mapping: primary row labels on `colorOnSurface`, helper/summary/detail text and subdued icons on `colorOnSurfaceVariant`, active/checked indicators on `colorPrimary`, and settings surfaces on `colorSurface`.
- Manual QA for this surface should cover Settings > Player notification action rows and action chooser dialogs, notification channel toggles, Updates settings, Debug settings, reachable error-report surfaces, checked/disabled/helper/icon contrast, rotation, and Light/Dark/Black plus dynamic/App default/manual palettes.
- Preserve notification behavior, action selection and saving, update checking, debug behavior, ACRA/error reporting, playback, downloads, extractor/service logic, queue/player overlays, database behavior, navigation, and settings preference logic while iterating on this visual surface.

### Current Material polish note: backup, restore, import, export, and migration surfaces

- Keep backup/restore/import/export polish focused on Material role mapping and
  Material dialog chrome: primary titles/actions should resolve through
  `colorOnSurface`/`colorPrimary`, helper and warning body copy should use
  on-surface variant roles unless it is truly destructive/error, and surfaces
  should stay on `colorSurface` or established dialog surfaces.
- Manual QA for this surface should cover Settings > Backup and restore,
  database export/import through the safe pre-picker/confirmation steps,
  optional settings-import warning/result prompts, subscription import/export
  entry points, migration-info prompts when reachable, cancel paths, rotation,
  and Light/Dark/Black plus dynamic/App default/manual palettes.
- Preserve import/export behavior, file picker behavior, backup format,
  serialization/deserialization, database behavior, storage permissions,
  SAF/document-tree behavior, validation logic, subscription worker behavior,
  playback, downloads, extractor/service logic, queue/player overlays,
  navigation, notification behavior, and settings preference logic while
  iterating on this visual surface.

### Current Material polish note: history, local feed, and local list management

- Keep local-library polish focused on XML/resource Material role mapping: primary labels on `colorOnSurface`, helper/secondary metadata and subdued icons on `colorOnSurfaceVariant`, active local playback/action controls on `colorPrimary`, and local feed/history surfaces and dividers on `colorSurface`/`colorSurfaceVariant`.
- Manual QA for this surface should cover History, Feed / What's New refresh and loading rows, local playlist/history playback controls, clear-history confirmation cancel paths, local management row title/helper/icon/action contrast, rotation, and Light/Dark/Black plus dynamic/App default/manual palettes.
- Preserve history sorting/deletion behavior, feed loading/update behavior, watched-state handling, playlist/subscription behavior, import/export, navigation, database behavior, settings preference logic, downloads, notifications, extractor/service logic, playback, queue/player overlays, and player overlay colors while iterating on this visual surface.

### Current Material polish note: remaining visual-gap audit

- Latest audit was documentation-only and intentionally avoided broad code/resource changes. Remaining non-Material or partially Material hits are now classified before entering higher-risk surfaces.
- Low-risk next PR candidates are XML-only and outside behavior-sensitive areas: channel/playlist avatar stroke literals, video-detail thumbnail badge literals, and narrow non-artwork vector tint cleanup where the rendered output stays equivalent.
- Medium-risk candidates need focused PRs plus manual QA: AppCompat/Preference/Material bridge attrs (`colorAccent`, `textColorPrimary`, `textColorSecondary`, `colorControlNormal`), legacy-named YouTube progress/theme aliases, license/about/preference text appearances, info-list duration/live badge resources, and notification color setup.
- High-risk/deferred areas remain player-sensitive: main/popup player overlays, queue overlay, seekbar/progress/gesture feedback, brightness/volume/fast-seek feedback, speed/quality/audio/caption popups, loading/error overlays inside player, and closing overlay colors.
- Intentional exceptions remain documented: `stream_quality_item.xml` platform attrs for test-theme compatibility, OS/system-controlled chrome, palette definitions in `colors.xml`/manual theme styles, launcher/artwork/vector identity colors, and white/black/red player overlay affordances over arbitrary video frames.
- Recommended order: non-player thumbnail/avatar XML cleanup first, info-list duration/live badge audit second, preference/license/about style pass third, bridge-attr cleanup fourth, and dedicated player-controls visual pass last with real-device QA.

### Current Material polish note: non-player thumbnail/avatar cleanup

- The first low-risk follow-up from the remaining visual-gap audit is complete: channel/playlist avatar stroke literals and video-detail thumbnail badge literals now use named non-player color resources while preserving the previous rendered values.
- Still deferred: player/queue overlays, seek/gesture feedback, speed/quality/audio/caption popups, launcher/icon/splash/brand artwork, broad theme attr rewiring, and info-list duration/live badge retheming.
- Next recommended PR remains the info-list duration/live badge audit outside the player, followed by preference/license/about style work, bridge-attr cleanup, and a dedicated player-controls visual pass last.

### Current Material polish note: info-list duration/live badge audit

- The info-list duration/live badge follow-up is complete as a minimal XML/resource cleanup. Static stream-row duration badges and playlist stream-count overlays now use info-list-specific thumbnail badge color names while preserving the existing translucent black/opaque black backgrounds and off-white text for readability over arbitrary thumbnails.
- Live badges and programmatically rebound duration backgrounds intentionally keep the existing shared high-contrast resources in this pass to avoid adapter churn. Player overlay, queue overlay, seek/gesture feedback, video-detail thumbnail badge resources, launcher/icon/splash/brand artwork, and broad theme attr rewiring remain deferred.
- Manual QA should cover home/feed rows, search results, channel/playlist/related-video rows, list/grid/mini/card variants, live rows when available, light and dark thumbnails, and Light/Dark/Black plus dynamic/App default/manual palettes, with a specific check that player overlay time/seek controls are unchanged.

### Current Material polish note: preference/license/about style pass

- The preference/license/about follow-up is complete as a narrow visual-role pass. License screen titles and component names now use `colorOnSurface`; license explanatory copy, component copyright/license metadata, and subdued row text use `colorOnSurfaceVariant`; read-license/link emphasis uses `colorPrimary`.
- License detail WebView styling now resolves the active Material theme roles (`colorSurface`, `colorOnSurface`, and `colorPrimary`) instead of fixed light/dark license colors, so dynamic color, App default, and manual palettes are respected in the license dialog body.
- Deferred/skipped: broad bridge-attr cleanup, unrelated preference XML rewiring, About/fork-attribution text or behavior changes, license loading/navigation behavior, import/export, database, playback, downloads, extractor/service logic, notifications, player overlay, queue overlay, seek/gesture feedback, launcher/icon/splash artwork, and brand identity colors.
- Manual QA should cover About, fork attribution/app info/link actions, Licenses list rows, license detail dialogs, settings rows that share preference styles, rotation where available, and Light/Dark/Black plus Follow system dynamic color, App default, and one manual palette such as Orange or Purple.

### Current Material polish note: bridge-attr cleanup audit

- The bridge-attr follow-up is audit-first and intentionally avoids broad rewiring. Remaining `colorAccent`, `textColorPrimary`, `textColorSecondary`, `colorControlNormal`, `colorControlActivated`, and `colorButtonNormal` usage is now classified by risk instead of globally replaced.
- Tiny scoped cleanup applied: the two video-detail metadata label layouts now use `colorOnSurface` rather than platform `textColorPrimary`. This keeps the change XML-only and outside player/queue overlays, playback, downloads, extractor/service logic, database/import/export, notifications, navigation, and settings behavior.
- Intentional exceptions: `stream_quality_item.xml` platform text-color attrs remain for `Theme.DeviceDefault` instrumentation compatibility; player/queue overlays, fast-seek feedback, launcher/splash/artwork vectors, black/white overlay affordances, file-picker bridge styles, and AppCompat/AndroidX settings/dialog bridge attrs remain deferred.
- Recommended next PR order: targeted settings/dialog bridge-attr cleanup with widget QA, then file-picker bridge audit as a separate third-party-theme pass, then notification color setup if needed, and player/queue visual cleanup last with real-device QA.

### Current Material polish note: settings/dialog bridge attr cleanup

- The first narrow settings/dialog bridge cleanup is complete: settings themes now map their legacy `colorAccent` bridge to `colorPrimary`, keeping old AppCompat/Preference controls aligned with the active Material action/accent role without changing preference keys, defaults, or settings logic.
- Deferred/skipped: dialog-wide `colorAccent` and `colorControlActivated`, file-picker bridge attrs, `stream_quality_item.xml`, player/queue overlays, seek/gesture feedback, speed/quality/audio/caption popups, playback/download/extractor/database/import/export/navigation/notification behavior, tests, launcher/splash/artwork, and brand identity colors.
- Recommended next PR order: a dedicated dialog bridge cleanup with dialog button/control QA, then file-picker bridge audit separately, then notification color setup if still needed, and player/queue visual cleanup last with real-device QA.

### Current Material polish note: dialog bridge attr audit

- The dialog-wide bridge pass classified remaining `colorAccent`, `colorControlActivated`, `colorButtonNormal`, `textColorPrimary`, `textColorSecondary`, `android:textColorPrimary`, and `android:textColorSecondary` uses before making changes.
- Tiny scoped cleanup applied: manual dialog theme-color overlays now map `colorControlActivated` to `?attr/colorPrimary`, restoring the base dialog active-control role for checked controls while preserving existing MaterialAlertDialog surfaces, text roles, button behavior, and theme parents.
- Deferred/skipped: dialog-wide `colorAccent`, file-picker bridge attrs, `stream_quality_item.xml`, player/queue overlays, seek/gesture feedback, speed/quality/audio/caption popups, playback/download/extractor/database/import/export/navigation/notification behavior, tests, launcher/splash/artwork, and brand identity colors.
- Manual QA should cover the theme-color restart dialog, text-input dialogs, playlist/feed-group/subscription dialogs, backup/import confirmations, and license detail dialogs across Light/Dark/Black plus dynamic/App default/manual palettes, checking buttons, active radio/checkbox states, and text contrast.

### Current Material polish note: file-picker bridge attr audit

- The file-picker bridge-attr pass is complete as documentation-only. It located the integrated NoNonsense FilePicker activity/theme handoff and confirmed that `FilePickerThemeLight`, `FilePickerThemeDark`, `FilePickerAlertDialogThemeLight`, and `FilePickerAlertDialogThemeDark` remain third-party compatibility surfaces.
- Classification outcome: `colorAccent` is directly set in the file-picker and file-picker alert styles but remains deferred as a third-party/AppCompat bridge; `colorControlActivated`, `colorButtonNormal`, `colorControlNormal`, `textColorPrimary`, `textColorSecondary`, `android:textColorPrimary`, and `android:textColorSecondary` are not directly set by these file-picker styles and remain inherited/deferred rather than being added speculatively.
- No tiny cleanup was applied because no file-picker-local mapping was clearly safe without changing NoNonsense FilePicker widget/action/selection rendering. This intentionally avoids broad file-picker retheming.
- Preserve download folder selection, picker launch extras, selected paths, SAF/storage permissions, import/export/backup behavior, settings preference logic, downloads, playback, extractor/service logic, notifications, player/queue overlays, and dialog behavior outside any future file-picker-theme-only patch.
- Future work should only change these attrs in a dedicated file-picker-theme PR with device/emulator QA over picker toolbar, rows, breadcrumbs/path text, action buttons, selection states, and alert wrappers in Light/Dark/Black, dynamic color, App default, and one manual palette.

### Current Material polish note: notification color setup audit

- The notification color setup pass is complete as documentation-only. It located player/media notification setup, action icon selection, notification-channel creation, new-stream notification builders, feed-loading foreground notifications, error-report notifications, preferred-player-fetcher foreground notifications, and download notification call sites.
- Classification outcome: channel setup and Android 13+ media action handling are Android/system-controlled; player `setColor`, `setColorized`, small-icon, thumbnail, `MediaStyle`, and media-session attachment are notification compatibility or playback/service-sensitive; new-stream launcher-background color hints and colorized templates are notification compatibility; error/feed/download/default notifications are OS-template or service-flow sensitive.
- No tiny cleanup was applied because replacing notification color hints/icons with Material roles, changing colorized defaults, or adding notification-specific theme indirection is medium-risk across OS/OEM notification templates and high-risk when tied to media-session or foreground-service behavior.
- Preserve notification actions, media controls, channel ids/importance, Android 13 permission/channel behavior, foreground service lifecycle, playback/background/popup behavior, download notifications, settings/database/extractor/service logic, player and queue overlays, file-picker, `stream_quality_item.xml`, playback popups, launcher/splash artwork, and brand colors.
- Future notification visual work should be isolated to a dedicated PR with real-device QA over player foreground/background notifications, action controls, light/dark system notification surfaces, Android 13+ permission/channel behavior, and representative new-stream/error/feed/download notifications.

### Current Material readiness note: release QA checklist

- Final readiness audit is documentation-only and aligns this roadmap with `doc/material3-fork-experiment.md`: completed low-risk Material 3 polish covers splash/theme foundation, bottom navigation, theme color/restart prompt, settings, About/licenses, download dialog and downloads manager, snackbars, video detail, playback-parameter dialog, playlist/subscription/feed-group surfaces, search/list rows, empty/retry states, generic dialogs/pickers, toolbar/app-bar/search/menu/tabs, backup/import/export surfaces, local library/feed/history, thumbnail/badge naming, bridge-attr audits, file-picker audit, and notification audit.
- Deferred high-risk areas remain explicit: player overlay, queue overlay, seekbar/gesture/fast-seek feedback, speed/quality/audio/caption popups, notification visual changes, and file-picker visual changes. These require dedicated follow-up PRs with real-device QA because they touch arbitrary video-frame overlays, OS/OEM notification templates, media/session behavior, storage picker behavior, or third-party UI surfaces.
- Release/manual QA checklist: run through Light, Dark, Black, Follow system dynamic color, App default, and one manual palette such as Orange or Purple; include rotation, cold-start splash, bottom navigation with 4/5 tabs and >5 fallback, Settings theme color Apply now/Restart/Later, About/licenses, download dialog/downloads manager, search/list rows, playlists/subscriptions/feed groups, backup/import safe flows, notifications, file picker launch/cancel paths, and a player/queue overlay smoke test that confirms those deferred overlays still behave and render as before.
- Release readiness checks should also verify CI/release APK artifact generation and decide whether Fastlane screenshots need a refresh after device QA; no README, workflow, screenshot, code, resource, behavior, or visual runtime changes were made in this audit.

### Current Material defaults status: bundled preference snapshot

- Status: complete for default SharedPreferences. NewPipe Material now ships the canonical preference baseline as `app/src/main/res/raw/newpipe_material_default_preferences.json`.
- Fresh installs / cleared app data apply the bundled snapshot before ordinary settings use, and Reset settings clears the default SharedPreferences file then reapplies that same snapshot before restart.
- No supplied snapshot keys are intentionally skipped, including runtime-looking values such as `is_in_background`, `import_export_data_path`, `kao_last_checked`, `stream_info_selected_tab`, `service`, and `media_tunneling_device_blacklist_version`.
- Existing users keep their chosen settings during normal updates; the snapshot is only applied on first run or explicit Reset settings. Non-preference user data such as databases, watch history, subscriptions, downloads, caches, player queues, app identity/signing, player/download/navigation behavior, and `shared/` are not changed by this defaults work.

### Stream thumbnail polish status

- Stream thumbnail polish: rounded preview thumbnails for list/grid/card, card-mode horizontal margins, and thumbnail-local watched-progress strips.
- Status: complete for stream item layouts. List, mini/related, grid, and card stream preview thumbnails use the shared 12dp rounded stream-thumbnail shape overlay, card-mode rows keep a 12dp horizontal inset from the screen/RecyclerView edges, and watched-progress bars now sit lifted inside the thumbnail near the bottom edge.
- Watched-progress bars use a stream-thumbnail-specific Material drawable driven by `colorPrimaryFixedDim`/`colorPrimaryContainer` instead of the global YouTube/service progress alias, so player/download progress drawables remain intentionally untouched.
- The progress strip is lifted inside the rounded thumbnail, and stream holders raise duration/time badges only while visible watched progress is present; unwatched items keep the normal lower badge position and playback/loading/watch-history behavior is unchanged.
