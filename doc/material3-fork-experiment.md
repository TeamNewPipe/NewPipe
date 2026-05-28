# Material 3 fork experiment

> **Fork-only experimental work**
>
> This document tracks incremental Material 3 migration work on the `experiment-material3-ui` branch in this fork. It is **not upstream-ready** and should be treated as branch-local experimentation notes.

## Completed changes

### Theme foundation
- Base app and dialog theme parents migrated to Material 3.

### Dialog sizing
- Minimum dialog width behavior restored after theme-parent migration.

### Toolbar
- Main toolbar migrated to `MaterialToolbar`.
- Toolbar color polish: main toolbar background moved to a neutral Material surface role (`colorSurface`) to reduce red dominance while keeping red as accent/brand color.
- Toolbar tint readability tuned for neutral toolbar surface: explicit per-theme `actionColor`/`toolbarSearchColor` contrast values set for Light/Dark/Black.
- Selected/activated control polish: `colorControlActivated` now maps to `colorOnSurface` (instead of `colorPrimary`) to reduce generic red dominance on neutral surfaces while preserving red as service/accent color.
- Ripple/highlight audit after neutral toolbar and activated-state polish: existing generic ripple/selector colors are already neutral (gray/white alpha), so no additional ripple resource changes were applied in this step to avoid unnecessary churn.
- Surface/background audit after toolbar neutralization: `colorSurface`, `android:windowBackground`/`windowBackground`, and custom card/background attrs are already consistently mapped per Light/Dark/Black themes, so no surface color resource changes were applied in this step.

### Settings row summary color polish
- Standard AndroidX Preference rows now use an app-local `preference_material` layout override that preserves the required AndroidX Preference IDs while tinting summary text with `colorOnSurfaceVariant`.
- Required IDs preserved in the layout override: `@android:id/title`, `@android:id/summary`, `@android:id/widget_frame`; icon IDs remain provided by the existing `image_frame` override (`@+id/icon_frame`, `@android:id/icon`).
- Scope: global for AndroidX Preference rows that use the Material preference layout; individual preference XML entries, keys, defaults, titles, summaries, and behavior were not changed.
- Preference icon tint QA passed locally.
- SwitchPreferenceCompat → MaterialSwitch behavior passed locally.

### SwitchPreferenceCompat MaterialSwitch experiment
- Settings `SwitchPreferenceCompat` controls are globally retargeted through the settings themes to a custom preference widget layout backed by `com.google.android.material.materialswitch.MaterialSwitch`.
- Files changed: `app/src/main/res/layout/preference_widget_material_switch.xml`, `app/src/main/res/values/styles.xml`, and `doc/material3-fork-experiment.md`.
- Application scope: global for settings screens that use `SwitchPreferenceCompat` under `LightSettingsTheme`, `DarkSettingsTheme`, or `BlackSettingsTheme`; individual preference XML entries were not edited.
- Binding approach: the widget layout keeps AndroidX Preference's expected `@+id/switchWidget` id and non-clickable/non-focusable widget flags so row-level and switch-area taps continue to flow through `SwitchPreferenceCompat` state handling.
- Color approach: no new tint resources were added; the Material switch uses the existing Material 3 theme roles and non-red settings accent mappings already defined for Light/Dark/Black settings themes.
Validation results:
- `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
- `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: PASS

Manual QA results:
- Light theme:
  - Appearance settings switches render correctly: PASS
  - Row tap toggles switch: PASS
  - Switch-area tap toggles switch: PASS
  - State persists after leaving and returning: PASS
  - Disabled switch state readable, if present: PASS
  - Switch alignment/size acceptable: PASS
  - Checked/unchecked colors acceptable: PASS
  - Rotation: PASS
- Dark theme:
  - Appearance settings switches render correctly: PASS
  - Row tap toggles switch: PASS
  - Switch-area tap toggles switch: PASS
  - State persists after leaving and returning: PASS
  - Disabled switch state readable, if present: PASS
  - Switch alignment/size acceptable: PASS
  - Checked/unchecked colors acceptable: PASS
  - Rotation: PASS
- Black theme:
  - Appearance settings switches render correctly: PASS
  - Row tap toggles switch: PASS
  - Switch-area tap toggles switch: PASS
  - State persists after leaving and returning: PASS
  - Disabled switch state readable, if present: PASS
  - Switch alignment/size acceptable: PASS
  - Checked/unchecked colors acceptable: PASS
  - Rotation: PASS

Additional settings screens checked:
- Content settings switches: not provided in the QA handoff.
- Download settings switches: not provided in the QA handoff.
- Notification settings switches: not provided in the QA handoff.

Observed issues:
- Not provided in the QA handoff.

Known risks:
- AndroidX Preference row/switch binding may differ across library versions.
- Disabled/checked state tinting may need further tuning after broader settings-screen QA.
- Manual QA is still required for every settings screen with switches because Content, Download, and Notification screen results were not provided.

### Tab bar color polish
- Main and channel top tab bars now use `colorSecondaryContainer` for the container and `colorOnSecondaryContainer` for tab icons/text, ripple, and selected indicator. This keeps the tab selector readable while replacing the stronger primary-green app-bar treatment with a softer Material 3 container role.

Validation results:
- `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
- `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: PASS

Manual QA results:
- Light theme:
  - Main screen tab bar: PASS
  - Main tab icons readable: PASS
  - Main selected indicator visible: PASS
  - Channel tab bar: PASS
  - Channel tab text/icons readable: PASS
  - Toolbar remains neutral/readable: PASS
  - Status bar readable: PASS
  - Drawer: PASS
  - Feed/list screen: PASS
  - Rotation: PASS
- Dark theme:
  - Main screen tab bar: PASS
  - Main tab icons readable: PASS
  - Main selected indicator visible: PASS
  - Channel tab bar: PASS
  - Channel tab text/icons readable: PASS
  - Toolbar remains neutral/readable: PASS
  - Status bar readable: PASS
  - Drawer: PASS
  - Feed/list screen: PASS
  - Rotation: PASS
- Black theme:
  - Main screen tab bar: PASS
  - Main tab icons readable: PASS
  - Main selected indicator visible: PASS
  - Channel tab bar: PASS
  - Channel tab text/icons readable: PASS
  - Toolbar remains neutral/readable: PASS
  - Status bar readable: PASS
  - Drawer: PASS
  - Feed/list screen: PASS
  - Rotation: PASS

Observed issues:
- Not provided in the QA handoff.

### Cards
- `feed_group_card_item` root migrated to `MaterialCardView`.
- `feed_group_add_new_item` root migrated to `MaterialCardView`.
- `feed_group_reorder_item` root migrated to `MaterialCardView`.
- `feed_group_card_grid_item` root migrated to `MaterialCardView`.
- `feed_group_add_new_grid_item` root migrated to `MaterialCardView`.
- `item_instance` root migrated to `MaterialCardView`.
- `list_choose_tabs` root migrated to `MaterialCardView` (non-feed-group simple card row).
- Non-feed-group card-layout audit (next migration candidate): no additional eligible `androidx.cardview.widget.CardView` list/card roots remain outside `feed_group_*` and already-migrated `list_choose_tabs`.
- Conservative Material card styling normalized for feed-group cards:
  - elevation
  - ripple
  - stroke

### Dialogs
- Playlist creation flow migrated to `MaterialAlertDialogBuilder`.
- History delete playback-states confirmation flow migrated to `MaterialAlertDialogBuilder`.
- History delete search-history confirmation flow migrated to `MaterialAlertDialogBuilder`.
- History delete watch-history confirmation flow migrated to `MaterialAlertDialogBuilder`.
- Backup/restore settings reset confirmation migrated to `MaterialAlertDialogBuilder`.
- Update settings auto-update consent dialog migrated to `MaterialAlertDialogBuilder`.
- Permission helper "display over apps" guidance dialog migrated to
  `MaterialAlertDialogBuilder`.
- Choose Tabs "restore defaults" dialog migrated to `MaterialAlertDialogBuilder`.
- Migration manager user-info dialog migrated to `MaterialAlertDialogBuilder`.
- Kore install prompt dialog migrated to `MaterialAlertDialogBuilder`.
- PeerTube instance list "restore defaults" dialog migrated to `MaterialAlertDialogBuilder`.
- PeerTube instance list "add instance" dialog migrated to `MaterialAlertDialogBuilder`.
- Subscription import confirmation dialog migrated to `MaterialAlertDialogBuilder`.

### Dialogs intentionally skipped in this batch
- Player dialogs skipped:
  - `org.schabi.newpipe.player.helper.PlaybackParameterDialog`
  - `org.schabi.newpipe.fragments.detail.VideoDetailPlayerCrasher`
- Download dialogs skipped:
  - `org.schabi.newpipe.download.DownloadDialog`
  - `org.schabi.newpipe.settings.DownloadSettingsFragment`
  - `org.schabi.newpipe.streams.io.NoFileManagerSafeGuard`
- Complex/list-based dialogs skipped:
  - `org.schabi.newpipe.RouterActivity` (adapter/list choice flows)
  - `org.schabi.newpipe.settings.tabs.AddTabDialog` (adapter-backed tab list dialog)
  - `org.schabi.newpipe.settings.custom.NotificationSlot` (custom button handling)
  - `org.schabi.newpipe.info_list.dialog.InfoItemDialog` (menu-style item list handling)

### Checkstyle cleanup
- Fixed known `LineLength` violation in `PlaylistCreationDialog`.
- `./gradlew runCheckstyle -DskipFormatKtlint` passes.


### Feed group reorder item migration (latest QA record)

- Local validation results for this migration (as provided):
  - `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: PASS
- Manual QA results for this migration (as provided):
  - Feed group reorder item display: PASS
  - Drag handle visible: PASS
  - Drag/reorder interaction: PASS
  - Scrolling: PASS
  - Light theme: PASS
  - Dark theme: PASS
  - Black theme: PASS
  - Rotation: PASS


### list_choose_tabs migration (latest QA record)

- Local validation results for this migration:
  - `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: PASS
- Manual QA results for this migration:
  - Choose tabs row display: PASS
  - Tab icon/name visible: PASS
  - Drag handle visible: PASS
  - Reorder/drag behavior: PASS
  - Scrolling: PASS
  - Light theme: PASS
  - Dark theme: PASS
  - Black theme: PASS
  - Rotation: PASS


### Additional non-feed-group card migration audit (latest)

- Scope searched: `app/src/main/res/layout/` for `androidx.cardview.widget.CardView`.
- Result at that step: only `feed_group_add_new_grid_item` and `feed_group_card_grid_item` used `CardView`; both were `feed_group_*` and were excluded by task rules then.
- Migration action in this step: none (no safe new non-feed-group, non-player, non-download `CardView` target found).
- Validation for this audit/doc step:
  - `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: not run in this container step
- Manual QA for this audit/doc step: not applicable (no new layout migration performed).

### feed_group_card_grid_item migration (latest QA record)

- Local validation results for this migration:
  - `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: PASS
- Manual QA results for this migration:
  - Feed group grid card display: PASS
  - Icon/title visible: PASS
  - Click opens expected feed group: PASS
  - Scrolling: PASS
  - Light theme: PASS
  - Dark theme: PASS
  - Black theme: PASS
  - Rotation: PASS

### feed_group_add_new_grid_item migration (latest QA record)

- Local validation results for this migration:
  - `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: PASS
  - `rg "androidx.cardview.widget.CardView" app/src/main/res/layout -n`: no matches
- Manual QA results for this migration:
  - Feed group grid add-new card display: PASS
  - Icon/title visible: PASS
  - Click opens expected add/create flow: PASS
  - Scrolling: PASS
  - Light theme: PASS
  - Dark theme: PASS
  - Black theme: PASS
  - Rotation: PASS

### CardView layout search after latest migration

- Scope searched: `app/src/main/res/layout/` for `androidx.cardview.widget.CardView`.
- Result: no remaining `androidx.cardview.widget.CardView` layouts found.

### AlertDialog.Builder search after dialog batch migration

- Scope searched: `app/src/main/java/` for `new AlertDialog.Builder`.
- Result: remaining usages are intentionally skipped complex or player/download dialogs:
  - `org.schabi.newpipe.RouterActivity`
  - `us.shandian.giga.ui.fragment.MissionsFragment`
  - `us.shandian.giga.ui.adapter.MissionAdapter`
  - `org.schabi.newpipe.info_list.dialog.InfoItemDialog`
  - `org.schabi.newpipe.MainActivity`
  - `org.schabi.newpipe.util.NavigationHelper`
  - `org.schabi.newpipe.settings.tabs.AddTabDialog`
  - `org.schabi.newpipe.settings.DownloadSettingsFragment`
  - `org.schabi.newpipe.settings.custom.NotificationSlot`
  - `org.schabi.newpipe.download.DownloadDialog`
  - `org.schabi.newpipe.streams.io.NoFileManagerSafeGuard`
  - `org.schabi.newpipe.player.helper.PlaybackParameterDialog`
  - `org.schabi.newpipe.fragments.detail.VideoDetailFragment`
  - `org.schabi.newpipe.fragments.list.search.SearchFragment`
  - `org.schabi.newpipe.local.bookmark.BookmarkFragment`
  - `org.schabi.newpipe.local.playlist.LocalPlaylistFragment`

### Dialog batch migration (latest QA record)

- Local validation results for this migration batch:
  - `rg "new AlertDialog.Builder" app/src/main/java -n`: PASS (remaining list audited)
  - `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`:
    PASS
- Manual QA results for this migration batch:
  - UpdateSettingsFragment auto-update consent dialog: PASS
  - PermissionHelper overlay-permission guidance dialog: PASS
  - ChooseTabsFragment restore-defaults dialog: PASS
  - MigrationManager migration-info dialog: PASS
  - KoreUtils install prompt dialog: PASS
  - PeertubeInstanceListFragment restore-defaults dialog: PASS
  - PeertubeInstanceListFragment add-instance dialog: PASS
  - ImportConfirmationDialog subscription import confirmation dialog: PASS
  - Light theme: PASS
  - Dark theme: PASS
  - Black theme: PASS
  - Rotation: PASS

## Validation status

- ✅ Checkstyle: `./gradlew runCheckstyle -DskipFormatKtlint` passes.
- ✅ Full local validation (branch-wide): `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint` passes
  in an Android SDK-configured local environment.
- ⚠️ Feed-group reorder-item-specific validation status is tracked in the dedicated reorder-item section and depends on the explicitly provided results recorded there.
- ⚠️ Container note: full Gradle Android validation remains unavailable in Codex/container without
  Android SDK configuration (`ANDROID_HOME`/`sdk.dir`).

## Manual QA checklist

- [x] App launch
- [x] Drawer (main drawer)
- [x] Settings
- [x] Selected/activated controls remain readable and usable in Light / Dark / Black themes.
- [x] Selected/activated control color result acceptable for this fork.
- [x] Feed group cards
- [x] Playlist creation dialog
- [x] History delete playback states dialog
- [x] History delete search history dialog
- [x] History delete watch history dialog
- [x] Download dialog
- [x] Video detail
- [x] Light / Dark / Black themes
- [x] Rotation

## Known risks

- Material 3 theme parent compatibility regressions in edge screens.
- `MaterialToolbar` tint/title/elevation nuance differences versus prior toolbar behavior.
- Service branding is less visually dominant in main app chrome after neutral toolbar background shift.
- Toolbar icon/search tint contrast should be watched across Light/Dark/Black themes and services.
- Visual/ripple/elevation differences between `CardView` and `MaterialCardView`.
- `MaterialAlertDialogBuilder` rendering differences versus `AlertDialog.Builder`.
- Dialog width behavior variance on tablet/foldable form factors.

## Future commit rule (branch discipline)

- Migrate **one component** or **one dialog flow** per commit.
- Run Checkstyle and build validation after each change.
- Smoke-test exactly the flow touched by the commit.

### Material 3 color-role migration (latest)

- Scope: conservative theme/color-role remap only (no layout/dialog/behavior migration).
- Generic app chrome/control roles were moved away from YouTube red and aligned to Material 3 role colors.
- YouTube red resources remain in place for explicit service/brand usage.

Changed attrs in app themes:
- `colorPrimary`
- `colorOnPrimary`
- `colorPrimaryContainer`
- `colorOnPrimaryContainer`
- `colorSecondary`
- `colorOnSecondary`
- `colorSecondaryContainer`
- `colorOnSecondaryContainer`
- `colorSurface`
- `colorOnSurface`
- `colorSurfaceVariant`
- `colorOnSurfaceVariant`
- `colorOutline`
- `colorAccent` (mapped to non-red Material 3 secondary in app themes)
- `colorControlActivated` (mapped to `colorSecondary`)
- `android:statusBarColor`
- `android:navigationBarColor`

System bar updates:
- Light/Dark/Black app themes now map status/navigation bars to `colorSurface` rather than red service colors.
- Opening theme navigation bar moved from red service color to neutral surface/background colors per day/night variant.
- Existing `windowLightNavigationBar` handling remains in `values-v27` (`true` light, `false` dark/black).

Settings/control tint updates:
- Settings accent colors now map to Material 3 secondary colors (`light_m3_secondary_color` / `dark_m3_secondary_color`) to reduce red-dominant toggles/switches/radio indicators.

Known risks (updated):
- Service identity may feel less YouTube-branded as generic app chrome is now neutral/Material.
- Status/navigation bar icon contrast must be watched across Android versions and OEM skins.
- Settings controls may still need follow-up tint tuning after broader device QA.

### System-bar contrast + green/neutral palette tuning (latest)

- Fixed light-theme status bar icon contrast by adding API 23+ `windowLightStatusBar=true` for Light theme and `false` for Dark/Black, while keeping status bar backgrounds surface-aligned.
- Kept API 27+ navigation bar icon contrast behavior (`windowLightNavigationBar=true` light, `false` dark/black) and aligned inheritance so v27 extends the v23 status-bar handling.
- Tuned the static Material 3 app-chrome palette from blue/purple toward green/neutral role colors (`light_m3_*`/`dark_m3_*`) for primary and secondary roles, including settings accent mapping, to better match Material You-like green system palettes.
- Dynamic color decision: investigated and deferred in this commit. Current theme stack includes many explicit role mappings and service-themed overrides; applying runtime dynamic colors safely would require broader theme audit/testing and is intentionally out-of-scope for this focused, reversible tuning step.

Additional known risk:
- OEM/system-bar icon contrast behavior can vary by Android version and vendor skin; verify light status/nav icon readability on representative devices.


### Dynamic color support (latest)

- Stage 1 system dynamic color support is implemented with the existing Material
  Components `DynamicColors` API; no new dependency was added.
- Dynamic colors are registered from `App.onCreate()` after settings are
  initialized and before activities are created. Activity-level theme application
  also reapplies dynamic color through `ThemeHelper` after explicit `setTheme()`
  calls, so supported Android 12+ devices can resolve Material You theme roles
  for app activities that choose their theme at runtime.
- Unsupported Android versions/devices keep the existing static Material 3
  fallback palette because `DynamicColors.applyToActivitiesIfAvailable()` is a
  no-op when dynamic color is unavailable. Existing fallback colors were not
  removed.
- Black theme decision: dynamic colors are skipped when the selected theme
  resolves to Black theme, including automatic device theme with Black as the
  selected night theme. This is the least risky Stage 1 behavior because it
  preserves the fork's black surfaces, status/navigation bar treatment, and
  OLED-friendly visual intent.
- Manual accent/color settings are not implemented in this stage; they remain
  planned for later static-palette and settings UI stages.

Known risks:
- OEM dynamic color palettes can vary across Android 12+ implementations.
- Contrast can vary with wallpaper-derived palettes and should be manually
  checked for toolbar, tabs, settings controls, dialogs, cards, and system bars.
- Black theme interaction may need deeper work if a future stage tries to apply
  dynamic accent roles while preserving pure black surfaces.
- Manual accent override and preset palette export/import behavior are not part
  of this stage.

Validation results for Stage 1 dynamic color support:
- `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
- `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: PASS

Android 12+ dynamic color QA results:
- System palette green applied to app: PASS
- System palette changed to another color and app followed after relaunch: PASS
- Light theme dynamic colors: PASS
- Dark theme dynamic colors: PASS
- Black theme remains black enough: PASS
- Status bar contrast: PASS
- Navigation bar contrast: PASS
- Toolbar: PASS
- Top tab bar: PASS
- Settings switches: PASS
- Dialogs: PASS
- Video detail page: PASS
- Download dialog: PASS
- Rotation: PASS

Older/unsupported Android QA results:
- App launches with fallback palette: NOT CHECKED
- Fallback palette readable: NOT CHECKED
- No crash: NOT CHECKED

Observed issues:
- None observed.
