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
- **NewPipe Material**
- **Neutral**
- **Green**
- **Blue**
- **Purple**
- **Orange**
- **Pink**
- **Red**

Default behavior:

- Android 12+ / dynamic-color-capable devices: **Follow system**.
- Unsupported devices: **NewPipe Material** or **Neutral**, depending on the
  final fallback palette chosen during implementation.

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
2. **Stage 2: static preset palettes.** Define Material 3-compatible preset
   palettes for NewPipe Material, Neutral, Green, Blue, Purple, Orange, Pink,
   and Red.
3. **Stage 3: settings UI for theme color.** Add the Appearance setting, persist
   the selected value, and ensure export/import carries the new preference safely
   with existing settings backup flows.
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
  theme application.
- Avoid app restart bugs: switching the color should either reapply the theme
  predictably or request a controlled activity recreation with saved state.
- Maintain status bar and navigation bar contrast in Light, Dark, and Black
  themes, including gesture navigation edge cases.
- Ensure settings controls, `MaterialSwitch` widgets, preference text/icons,
  top tabs, dialogs, cards, toolbar surfaces, and selected/activated states all
  update from the resolved palette.
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

## Fork productization checklist

Before presenting NewPipe Material as a user-installable maintained fork, make
explicit decisions for each item:

- **App name:** Chosen as **NewPipe Material** for the visible launcher/app
  label.
- **App icon:** Design fork-specific launcher and notification-safe branding
  that does not confuse users into thinking it is the official NewPipe app.
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
  Privacy, and backup/restore paths.
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
