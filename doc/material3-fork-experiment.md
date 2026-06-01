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

### Download UI visual polish
- Download dialog and metadata-loading dialog roots now use `colorSurface` so the dialog body follows the neutral Material 3 surface role.
- Download dialog labels, helper/warning text, and stream-format/size metadata now use `colorOnSurfaceVariant`, while editable filename text, selected quality text, and thread count use `colorOnSurface`.
- Download media-type radio controls, the thread seekbar, and the loading spinner now use `colorPrimary` so dynamic/manual theme colors provide accent emphasis without changing selected defaults or callbacks.
- Follow-up contrast fix: download dialog themes no longer override the app accent with legacy YouTube red, so the toolbar/title/icon, filename underline, radio control tint, and thread seekbar inherit the active Material 3/dynamic/manual color roles instead of the old service palette.
- Disabled media-type radio text now uses a readable on-surface-variant selector, and the filename field explicitly uses on-surface text with a primary accent underline.
- Downloads screen mission rows now use theme-resolved `colorSurfaceVariant` tracks and `colorPrimaryContainer` progress fills, with on-surface text roles, to reduce red-dominant video cards while preserving progress/status binding.
- Downloads manager follow-up polish maps the activity/fragment/recycler backgrounds to `colorSurface`, keeps mission titles on `colorOnSurface`, moves status/size/date/header text and row icons to `colorOnSurfaceVariant`, and leaves progress state binding/action callbacks untouched. QA should inspect empty, active, paused, completed, and failed rows across Light/Dark/Black, dynamic color, App default, and one manual palette.
- Files changed: `app/src/main/res/layout/download_dialog.xml`, `app/src/main/res/layout/download_loading_dialog.xml`, `app/src/main/res/layout/stream_quality_item.xml`, `app/src/main/res/layout/activity_downloader.xml`, `app/src/main/res/layout/missions.xml`, `app/src/main/res/layout/missions_header.xml`, `app/src/main/res/layout/mission_item.xml`, `app/src/main/res/layout/mission_item_linear.xml`, `app/src/main/res/color/download_control_tint.xml`, `app/src/main/res/color/download_option_text_color.xml`, `app/src/main/res/values/styles.xml`, and `app/src/main/java/us/shandian/giga/util/Utility.java`.
- Behavior scope: download path selection, file picker/file manager behavior, stream/format selection, thread count persistence, queue/start/cancel behavior, and download business logic were not changed.
- Compatibility note: `stream_quality_item.xml` uses platform text color attrs for its spinner row icon/text so it can inflate under both the app Material theme and the plain `Theme.DeviceDefault` instrumentation-test theme used by `StreamItemAdapterTest`; do not reintroduce direct fork-only attrs into platform `ImageView` attributes there.
- Known risk: the native spinner/dropdown and edit-text underline rendering still depends on platform/AppCompat widgets, so manual QA should verify contrast across Light/Dark/Black and dynamic/manual theme presets. Downloads mission cards now trade file-type-specific red/black/cyan backgrounds for role-based progress surfaces; QA should verify pending vs finished rows remain clear with real missions.

### Empty/error/loading state polish
- Shared empty-state views now tint their kaomoji and explanatory copy with `colorOnSurfaceVariant` for quieter Material 3 empty states on Light/Dark/Black surfaces.
- Inline empty states for search, playlists, kiosks, channel videos, comments, related items, settings search, and selection screens now use the same muted on-surface-variant treatment without changing their messages or visibility logic.
- The shared `error_panel` now uses a rounded `colorSurfaceVariant` container, `colorOnSurface` for the primary error message, `colorOnSurfaceVariant` for supporting service/explanation text, and primary/on-primary themed retry/action buttons.
- Loading indicators in audited list/search/feed/channel selection states now use `colorPrimary` through shared progress styles so dynamic/manual theme colors drive loading emphasis.
- Files changed: `app/src/main/res/layout/error_panel.xml`, `app/src/main/res/layout/list_empty_view.xml`, `app/src/main/res/layout/list_empty_view_subscriptions.xml`, selected empty/loading state layouts, `app/src/main/res/values/styles_misc.xml`, and `app/src/main/res/drawable/state_panel_background.xml`.
- Behavior scope: loading state transitions, retry/error actions, messages, search no-results logic, feed refresh logic, and selection-screen loading logic were not changed.
- Known risk: the shared `error_panel` is used by many fragments, so device QA should verify long translated error messages still fit comfortably inside the rounded surface container.

### Search UI polish
- Expanded toolbar search now keeps the toolbar neutral with a `colorSurface` outer container and uses a rounded `colorSurfaceVariant` search field background.
- Search input text now uses `colorOnSurface`, hint/clear icon use `colorOnSurfaceVariant`, and the input accent/cursor roles are retargeted to `colorPrimary` so dynamic/manual theme colors provide focused emphasis without changing input behavior.
- Main search suggestions now use a surface background, on-surface query text, on-surface-variant icons, and existing `colorControlHighlight` touch feedback.
- Search result auxiliary text was clarified with Material roles: corrected-suggestion text uses `colorPrimary`, meta information uses `colorOnSurfaceVariant`, and the suggestions panel uses `colorSurface`.
- Files changed: `app/src/main/res/layout/toolbar_search_layout.xml`, `app/src/main/res/layout/fragment_search.xml`, `app/src/main/res/layout/item_search_suggestion.xml`, `app/src/main/res/values/styles_misc.xml`, and `app/src/main/res/drawable/search_toolbar_field_background.xml`.
- Behavior scope: search opening/closing, query submission, suggestions, keyboard handling, filters, and result loading logic were not changed.
- Known risk: `toolbar_search_layout` is shared by main search, settings search, and feed-group search UI, so device QA should verify all visible search entry points after this shared visual resource change.

### Navigation drawer polish
- Main drawer container now uses the Material surface role (`colorSurface`) instead of the generic window background.
- Drawer item states are defined with dedicated resources: selected/activated rows use a rounded `colorPrimaryContainer` pill, selected icons/text use `colorOnPrimaryContainer`, unselected icons use `colorOnSurfaceVariant`, and unselected text uses `colorOnSurface`.
- Drawer ripple feedback is wired to the existing subtle theme highlight (`colorControlHighlight`) so Light/Dark/Black and dynamic/manual theme colors remain consistent with the rest of the app chrome.
- Drawer header content and service-switch behavior are preserved; the header fallback background now uses `colorSurfaceVariant` behind the existing header art to reduce red dominance while keeping the existing white header content readable.
- Files changed: `app/src/main/res/layout/drawer_layout.xml`, `app/src/main/res/layout/drawer_header.xml`, `app/src/main/res/color/drawer_navigation_icon_color.xml`, `app/src/main/res/color/drawer_navigation_text_color.xml`, `app/src/main/res/drawable/drawer_navigation_item_background.xml`, `app/src/main/res/drawable/drawer_navigation_item_checked.xml`, and `app/src/main/res/drawable/drawer_navigation_item_mask.xml`.
- Known risk: service identity in the header still depends on each service icon and the existing header image; contrast should be rechecked whenever service branding or header art changes.

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
- Main and channel `TabLayout` bars now use a neutral `colorSurface` container,
  `colorPrimary` for selected text/icons and the indicator,
  `colorOnSurfaceVariant` for unselected tab content, and
  `colorControlHighlight` for ripple feedback.
- Top mode still uses `ScrollableTabLayout`. Bottom mode now uses a real
  Material `BottomNavigationView` when the selected tab count is five or fewer,
  and keeps the existing scrollable bottom `TabLayout` fallback when more than
  five tabs are enabled.
- NewPipe Material now defaults the main tabs position switch to Bottom for new
  installs or unset preferences. Existing saved Top/Bottom preferences are
  preserved because the preference key and stored boolean values were not
  changed and no migration code was added.
- Bottom navigation items are built dynamically from the selected tabs list, so
  user tab order, titles, and icons remain the source of truth and no tabs are
  dropped when falling back to the scrollable TabLayout.
- BottomNavigationView polish: the active indicator style now inherits Material
  Components' Material 3 bottom-navigation active-indicator dimensions/shape and
  overrides only the color role to `colorPrimaryContainer`, so the selected pill
  is visible behind the icon. The bar uses a fixed Material-sized height plus
  modest item padding so the ViewPager can be constrained above it reliably, and
  selected-only labels reduce long-label clutter. The Bookmarked Playlists tab
  gets a bottom-nav-only display label (`Bookmarks`) while preserving the full
  tab title/content description and tab identity.
- Known risks/QA: verify ViewPager and bottom navigation selection sync, tab
  reselection behavior, rotation, 4-tab/5-tab BottomNavigationView layouts,
  active indicator visibility, more-than-5-tab fallback scrolling, and
  Light/Dark/Black plus dynamic/manual color contrast.

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

### Theme color setting UI/key (latest)

- Added the Appearance settings `Theme color` `ListPreference` and backing
  preference key/resources for the planned color strategy.
- Values added for the future accent mode/presets: `follow_system`,
  `newpipe_material`, `neutral`, `green`, `blue`, `purple`, `orange`, `pink`,
  and `red`; the default value is `follow_system`. The visible label for
  `newpipe_material` is **App default** so it does not repeat the app name while
  preserving the stored value for compatibility.
- Runtime color override is intentionally not implemented in this step. The
  setting saves through normal Android preferences and is therefore covered by
  the existing settings export/import flow, but `ThemeHelper` and dynamic color
  behavior are unchanged.
- Follow-up completed in the runtime theme color resolution step below, which
  wires the saved user-selected value into theme resolution with static overlays.


### Runtime theme color resolution (latest)

- Stage 2 runtime theme color resolution is implemented for the saved
  `theme_color` preference.
- Runtime priority is now: manual preset -> system dynamic color -> existing
  static fallback palette.
- `follow_system` keeps Material You dynamic color behavior on supported devices
  and falls back to the existing static palette when dynamic color is unavailable.
- Manual presets disable dynamic color and apply static Material 3 role overlays
  after the base Light/Dark/Black theme is selected and before views are
  inflated.
- Implemented manual presets: `newpipe_material` (shown as **App default**),
  `neutral`, `green`, `blue`, `purple`, `orange`, `pink`, and `red`. Red is
  available only when explicitly selected by the user.
- Black theme behavior: preset overlays only affect accent/container/control
  roles and do not change window/background/surface roles, so Black theme keeps
  black surfaces while allowing user-selected accents where safe.
- Theme color changes recreate the Appearance settings activity so the selected
  value can be applied through normal theme initialization; the value persists
  through normal preferences and existing export/import behavior.

Known risks:
- Preset contrast and tone choices may need additional device QA across OEMs,
  especially for system bars, tabs, settings controls, and dialogs.
- Some legacy views or service-specific themes may still use older attrs or
  explicit service colors instead of the new preset overlay roles.
- Changing the theme color may still require activity recreation or app restart
  for every already-open screen to pick up the new overlay.
- Older/unsupported Android fallback behavior for every manual preset still
  needs device QA if not tested.

### Theme color change UX prompt (latest)

- Changing the `Theme color` preference now saves the selected value and shows a
  Material-styled prompt instead of immediately recreating settings without
  explanation.
- Prompt actions:
  - `Apply now`: safely recreates the current Settings activity so the selected
    color can be applied there through normal theme initialization.
  - `Restart`: relaunches the app from its launcher intent and clears the old
    task so the selected color can be applied app-wide.
  - `Later`: dismisses the dialog and leaves the user in place; the saved color
    applies after settings/app screens are reopened or the app is restarted.
- Runtime theme color resolution, dynamic color behavior, palette values, and
  Black theme protection are unchanged by this UX-only step.

### Transient feedback polish (latest)

- Snackbar styling is now pinned to the app theme instead of relying on legacy
  per-call yellow action overrides. The global snackbar style uses Material 3
  inverse surface roles for the container and message text, inverse primary for
  action text, the existing Material snackbar elevation, and a rounded 8dp
  container shape.
- Explicit `setActionTextColor(Color.YELLOW)` calls were removed from snackbar
  creation sites so the shared theme style can apply consistently. Snackbar
  messages, actions, callbacks, and durations are unchanged.
- Toast creation remains through platform `Toast.makeText(...)` calls. No custom
  toast view or wrapper was introduced because platform toasts are not safely
  themeable across Android versions without changing infrastructure/behavior.
- Follow-up QA should trigger error/report, subscription notification, settings
  permission, and history-delete snackbars plus representative platform toasts
  across Light/Dark/Black, Follow system dynamic color, App default, and at least
  one manual preset.


### Video detail action area polish (latest)

- The video detail content area now uses explicit Material 3 role colors below
  the player: title, channel name, and view count use `colorOnSurface`, while
  uploader/subscriber metadata, like/dislike counts, disabled-like text, meta
  info, and expand/action icons use `colorOnSurfaceVariant`.
- Detail action buttons keep the same IDs, order, click listeners, long-click
  listeners, visibility logic, and callbacks, but their icon/text tint now comes
  from a shared `VideoDetailActionText` style so Add To, Background, Popup,
  Download, Share, Open in browser, Kodi, and debug crash actions are readable
  across Light/Dark/Black and dynamic/manual palettes.
- The detail meta-info dividers now use `colorSurfaceVariant`, and the
  description/comments/related tab strip uses the existing Material tab color
  selector plus `colorPrimary` indicator and `colorControlHighlight` ripple.
- Player controls, playback overlay, seekbar, download logic, share/open/browser
  behavior, queue/background/popup behavior, loading logic, and action callbacks
  are unchanged.
- Follow-up QA should open a video detail page, exercise the action buttons and
  secondary controls, expand/collapse the description, switch comments/related
  tabs, rotate, and verify Light/Dark/Black plus Follow system, App default, and
  one manual preset.

### Player overlay controls Material 3 audit (latest)

- Audit scope covered `player.xml`, `activity_player_queue_control.xml`,
  `dialog_playback_parameter.xml`, fast-seek/popup-close overlays,
  `stream_quality_item.xml`, and the player UI/helper code that builds quality,
  audio-track, playback-speed, and captions menus.
- The main player overlay intentionally sits on top of video content rather than
  an app surface: transport controls, close/collapse/fullscreen buttons, title,
  channel, quality, speed, captions, seek timestamps, volume/brightness overlays,
  queue headers, and the popup-player close affordance mostly use hardcoded
  white-on-black/translucent-black treatment for contrast over arbitrary video
  frames.
- Legacy/service color usage still exists in sensitive places: the playback
  seekbar thumb/progress is programmatically tinted `Color.RED`, the closing
  overlay uses a translucent red background, queue repeat/shuffle/add controls
  use `colorAccent`, the popup close FAB uses the legacy YouTube red resource,
  and playback-parameter dialog labels/step controls use `colorAccent`.
- Popup/dropdown status: quality, audio-track, and captions menus use the
  existing `DarkPopupMenu` overlay, while the playback-speed popup is created
  from the player context. Changing these menu contexts could affect anchoring,
  dismissal timing, focus, and hide-controls behavior, so it was not changed in
  this audit-only step.
- `stream_quality_item.xml` remains intentionally platform-attr based for
  compatibility with both app Material themes and plain instrumentation-test
  themes; this was preserved.
- No visual code/resource change was applied. The safest follow-up is a dedicated
  player-controls visual pass with real-device QA for fullscreen, embedded,
  background/audio, popup, queue, captions, speed, quality, fast seek,
  brightness/volume gestures, rotation, TV/desktop mode, and Light/Dark/Black
  plus dynamic/manual palettes.

Known risks / candidates for a future dedicated player pass:
- Candidate: map playback-parameter dialog accent text/seekbars from
  `colorAccent` to Material 3 roles, but only after confirming dialog button,
  seekbar, step, reset, and pitch-mode behavior across themes.
- Candidate: retheme queue header repeat/shuffle/add icons from `colorAccent` to
  `colorPrimary` or overlay-safe white, but only after checking selected/repeat
  states and queue mode affordance clarity.
- Candidate: replace programmatic red seekbar tint with a theme role, but this is
  high risk because seek progress is part of active playback/gesture feedback and
  must remain visible over video frames in all player modes.

### Playback parameter dialog visual polish (latest)

- Applied the low-risk candidate from the player overlay audit to
  `dialog_playback_parameter.xml` only. The main player overlay, playback
  controls, seek gestures, queue controls, quality/audio/caption popups, popup
  mode, background mode, and download/shared code remain untouched.
- The dialog root now uses the Material surface role, section labels and checkbox
  labels use `colorOnSurface`, min/max helper values use
  `colorOnSurfaceVariant`, active step/current values and controls use
  `colorPrimary`, separators use `colorSurfaceVariant`, and the pitch-mode
  expand icon uses `colorOnSurfaceVariant`.
- Tempo, pitch-percent, and pitch-semitone seekbars now share a small
  `PlaybackParameterSeekBar` style that maps thumb/progress to `colorPrimary`,
  track to `colorSurfaceVariant`, and secondary progress to
  `colorPrimaryContainer`, so dynamic/manual theme colors affect active dialog
  controls without changing any values.
- Behavior preservation: IDs, listeners, seekbar ranges/progress calculations,
  step-size persistence, unhook/skip-silence preferences, reset/apply/cancel
  handling, and playback callback behavior are unchanged.

Known risks / QA:
- Device QA should verify the playback speed/parameter dialog in Light, Dark,
  Black, Follow system dynamic color, App default, and one manual preset, and
  should exercise speed changes, pitch changes, reset, cancel/dismiss, and
  rotation if the dialog remains open.

### Settings surface Material 3 polish (latest)

- Settings screens now map preference title text to `colorOnSurface`, summaries,
  search-result secondary text, and normal preference controls/icons to
  `colorOnSurfaceVariant`, and category/header text to `colorPrimary`.
- Scope was limited to settings resource styling/layout tinting. Preference XML
  keys/defaults, SettingsActivity navigation, Appearance theme-color persistence,
  backup/import/export behavior, playback/download behavior, and the player
  overlay were not changed.
- The Appearance theme-color restart dialog remains behaviorally unchanged: Apply
  now recreates the current settings activity, Restart relaunches the app, and
  Later dismisses the dialog.
- QA scope: Settings main screen, Appearance, Theme color list and restart dialog,
  several ListPreference dialogs, Backup and restore settings, rotation in
  settings, and Light/Dark/Black plus Follow system dynamic color, App default,
  and one manual preset such as Orange or Purple.

### About/license/preference role polish (latest)

- About/license styling now keeps the existing About tab behavior and license navigation while tightening Material role usage on license surfaces: license screen titles use `colorOnSurface`, explanatory/license metadata uses `colorOnSurfaceVariant`, and the read-license action uses `colorPrimary`.
- Software component rows on the Licenses tab now explicitly map component names to `colorOnSurface` and copyright/license metadata to `colorOnSurfaceVariant` while preserving the existing selectable row background and context-menu registration.
- License detail WebView CSS now resolves `colorSurface`, `colorOnSurface`, and `colorPrimary` from the active theme instead of using fixed light/dark license colors, so Light/Dark/Black, dynamic color, App default, and manual palettes share the same Material role mapping as the surrounding About UI.
- Scope was intentionally limited to license/about-adjacent layout colors plus the existing license stylesheet helper. About text/attribution strings, license component loading, dialog buttons/navigation, preference XML keys/defaults, settings logic, import/export, playback/download behavior, player overlay, and queue overlay were not changed.
- QA scope: open About, inspect fork attribution/app info/link actions, open Licenses, inspect software component rows and license detail dialogs, then spot-check settings preference rows/search results across Light/Dark/Black, Follow system dynamic color, App default, and one manual preset such as Orange or Purple.

### Bridge attr cleanup audit (latest)

- Audit scope covered remaining `colorAccent`, `textColorPrimary`, `textColorSecondary`, `colorControlNormal`, `colorControlActivated`, `colorButtonNormal`, `android:textColorPrimary`, `android:textColorSecondary`, and fixed black/white contrast resources in app resources.
- Tiny scoped cleanup applied: video-detail metadata label layouts now use the app Material role `colorOnSurface` instead of platform `textColorPrimary`; this is XML-only, non-player-overlay, and keeps metadata content/loading behavior unchanged.
- Intentional compatibility/bridge usage remains: `stream_quality_item.xml` keeps platform text-color attrs for `Theme.DeviceDefault` instrumentation compatibility; settings, toolbar, popup, search, snackbar, file-picker, dialog, and action-button styles keep scoped bridge attrs where AppCompat/AndroidX/third-party widgets still consume them.
- Medium-risk global/theme usage remains deferred: base theme and preset palette `colorAccent`/`colorControlActivated`, settings theme `android:textColorPrimary`/`android:textColorSecondary`/`colorControlNormal`, and dialog/file-picker bridge attrs should only be changed in dedicated PRs with widget-by-widget QA.
- High-risk player/overlay usage remains untouched: `player.xml`, `activity_player_queue_control.xml`, fast-seek feedback, play queue text themes, player-control gradients, and white/black overlay affordances are intentionally deferred to a dedicated player-controls visual pass.
- Non-visible palette/resource definitions remain as definitions or named overlay/artwork resources: `colors.xml` palette values, Black theme resources, launcher/splash/monochrome artwork, thumbnail/avatar overlay names, transparent gradients, and vector source fills.
- Recommended next order: keep `stream_quality_item.xml` unchanged unless test-theme compatibility is reworked; split any base/dialog/settings bridge-attr cleanup into narrow PRs; keep file-picker bridge attrs separate because they are third-party-theme integration; handle player/queue overlay colors last with real-device QA.

### Settings/dialog bridge attr cleanup (latest)

- Tiny settings-scoped bridge cleanup applied: `LightSettingsTheme`, `DarkSettingsTheme`, and `BlackSettingsTheme` now map the legacy `colorAccent` bridge to `?attr/colorPrimary` so older AppCompat/Preference widgets in settings use the active Material primary/action role instead of the static settings-accent aliases.
- Existing settings text/control bridge attrs remain intentionally scoped: `android:textColorPrimary` stays on `colorOnSurface`, `android:textColorSecondary` stays on `colorOnSurfaceVariant`, and `colorControlNormal` stays on `colorOnSurfaceVariant` for preference rows/icons.
- Dialog theme `colorAccent`/`colorControlActivated` bridge attrs were inspected and deferred because changing them affects every app dialog; file-picker bridge attrs remain excluded for a separate third-party-theme pass.
- Scope exclusions preserved: no `stream_quality_item.xml`, player/queue overlays, file-picker styles, playback/download/extractor/database/import/export/navigation/notification logic, tests, launcher/splash/artwork, or brand identity colors were changed.

### Playlist, subscription, and feed-group row polish (latest)

- Playlist dialog rows, local/bookmarked playlist rows, playlist detail headers,
  subscription rows, feed-group cards, feed-group reorder rows, feed-group picker
  rows, and subscription section headers now use Material 3 role colors directly:
  primary titles use `colorOnSurface`, supporting metadata/helper text and subdued
  row controls use `colorOnSurfaceVariant`, section/add accents use `colorPrimary`,
  selected subscription chips use `colorPrimaryContainer`/`colorOnPrimaryContainer`,
  and simple dialog dividers use `colorSurfaceVariant`.
- Feed-group cards now use the Material surface role for the card container and
  surface-variant for the label strip instead of legacy card contrast colors, so
  Light, Dark, Black, dynamic, App default, and manual palettes stay aligned with
  the rest of the fork.
- Scope was intentionally limited to XML resource tint/text/background polish.
  Playlist creation/rename/delete, bookmarking, feed-group create/edit/delete,
  subscription selection, import/export, playback, downloads, navigation, and
  player overlays were not changed.
- QA scope: subscriptions, group create/edit/delete/reorder/select flows,
  bookmarks/playlists, playlist create/rename/delete/bookmark/unbookmark, Add to
  playlist, row title/summary/icon contrast, rotation in dialogs, and Light/Dark/
  Black plus Follow system dynamic color, App default, and one manual preset such
  as Orange or Purple.

Known risks / QA:
- Subscription channel rows share common channel item layouts with other channel
  lists, so device QA should spot-check search/channel-list channel rows for the
  same on-surface/on-surface-variant treatment.
- XML-only icon tinting assumes feed-group/add/reorder vectors are theme-tintable;
  if a future drawable adds intrinsic colors, revisit those specific icons rather
  than broadening behavior code.

### Search and common list-row surface polish (latest)

- Common stream/search result rows now map primary titles to `colorOnSurface` and
  uploader/view/date/supporting metadata to `colorOnSurfaceVariant` across list,
  mini, grid, card, and playlist-queue row variants.
- Channel card search/list rows now use the same role mapping for title,
  description, and subscriber/count metadata so they match the already-polished
  channel list rows.
- Stream playlist drag handles now use `colorOnSurfaceVariant` as subdued row
  controls. Existing duration/playlist badges, thumbnail placeholders,
  progress drawables, row click selectors, loading panels, error panel surface,
  and search suggestions were left on their established Material roles.
- Scope was intentionally limited to XML color/tint attributes plus this
  documentation. No adapters, extractor/service logic, search/list loading,
  opening/navigation behavior, database behavior, playback, downloads, queue or
  player overlay resources were changed.

Known risks / QA:
- Device QA should spot-check the main feed, search results, channel tabs,
  remote playlist rows, empty/error/loading states, and row pressed states in
  Light, Dark, Black, Follow system dynamic color, App default, and one manual
  palette such as Orange or Purple.


### Stream watched-progress thumbnail polish (latest)

- Stream list, grid, card, and mini/related watched-progress bars now use a
  thumbnail-local Material drawable instead of the global service progress attr,
  so the strip follows the softer `colorPrimaryFixedDim` theme role while
  optional secondary progress uses `colorPrimaryContainer`.
- The watched-progress views now sit lifted inside the rounded thumbnail bounds,
  with a thin 3dp height, 3dp bottom margin, and small horizontal inset to reduce
  square-corner protrusion against rounded thumbnails.
- Duration/live badges remain inside thumbnails, and stream holders now use a
  larger raised margin only while visible watched progress is present so the
  badge clears the strip while unwatched items keep the normal lower position.
- Global/player/download progress drawables and the `progress_horizontal_drawable`
  theme attr were intentionally untouched; existing watch-progress calculation,
  visibility logic, stream loading, playback, download, and navigation behavior
  were not changed.

### Empty, loading, no-content, and retry state polish (latest)

- Empty/no-results titles for search, settings search, comments, kiosks, and
  related-video panels now use `colorOnSurface`, while existing kaomoji/status
  marks and helper copy remain on `colorOnSurfaceVariant`.
- Generic error-panel actions now use a borderless primary-colored text treatment
  so retry/report/open-in-browser affordances read as Material actions without
  changing the panel IDs, listeners, or retry/error flow.
- Feed refresh/loading state copy now maps title text to `colorOnSurface`, helper
  text to `colorOnSurfaceVariant`, and the refresh affordance icon to
  `colorPrimary`; picker empty/loading states now use the same Material role
  mapping and progress indicator style.
- Scope was intentionally limited to XML resource color/tint/style attributes and
  this documentation. Error handling, retry behavior, loading behavior, search
  and list behavior, extractor/service/network logic, playback, downloads,
  navigation, database behavior, settings behavior, and player/queue overlays
  were not changed.

Known risks / QA:
- Device QA should inspect fresh/empty home/feed states, nonsense-query search
  no-results, settings search no-results, comments/related/kiosk empty states,
  picker empty/loading states, generic error/retry panels, rotation, and Light,
  Dark, Black, Follow system dynamic color, App default, and one manual palette
  such as Orange or Purple.

### Generic dialog and picker polish (latest)

- Generic text-input dialog content now uses the dialog surface role, on-surface input text, on-surface-variant hints, and a primary underline so add/rename prompts stay readable across Light, Dark, Black, dynamic color, and manual palettes.
- Shared custom dialog title rows, choose-tab picker rows, feed-group reorder dividers/actions, feed-group icon picker icons, PeerTube instance rows, channel/kiosk picker rows, and import helper/input surfaces now map primary labels to `colorOnSurface`, helper text and subdued icons to `colorOnSurfaceVariant`, dividers/strokes to `colorSurfaceVariant`, and action/FAB emphasis to `colorPrimary`/`colorOnPrimary`.
- Scope was intentionally limited to XML resource color/tint/style attributes and this documentation. Dialog/list selection behavior, validation, import/export behavior, service selection, network/extractor logic, playback, downloads, queue/player overlays, navigation, database behavior, and settings behavior were not changed. Playback speed and download dialogs were not touched.

Known risks / QA:
- Device QA should inspect generic add/rename text-input dialogs, choose-tabs add picker rows, PeerTube instance add/list/restore prompts, feed-group icon/subscription picker dialogs, import helper/input screens, unsupported URL/open-with prompts if reachable, rotation while dialogs are open, and Light, Dark, Black, Follow system dynamic color, App default, and one manual palette such as Orange or Purple.

### Toolbar, app bar, search action, overflow, and tab chrome polish (latest)

- Shared toolbar tinting now uses Material surface roles instead of legacy fixed contrast colors: toolbar title/action/icon text resolves through `colorOnSurface`, subtitles and popup-menu supporting/icon tinting resolve through `colorOnSurfaceVariant`, and toolbar search tint follows the active on-surface role.
- Toolbar popup/overflow theme styling now keeps popup text/icons on Material on-surface roles while preserving the existing highlight/ripple role. The feed-group search action was moved off the static contrast color resource and onto the same on-surface toolbar role.
- ReCAPTCHA chrome now uses the same neutral `colorSurface` app-bar treatment and on-surface title/subtitle/navigation tint as the shared app toolbar, without changing WebView or menu behavior.
- Large-land video-detail tabs now match the regular detail tab strip by using `colorSurface` for the container, the shared tab selector for text/icons, `colorPrimary` for the indicator/selected state, and the existing control highlight ripple.
- Scope was intentionally limited to XML resource/style attributes and this documentation. Search query handling, service selection, menu/navigation destinations, playback, downloads, queue/player overlays, database behavior, settings behavior, extractor/service logic, playback speed dialog, and download dialog behavior were not changed.

Known risks / QA:
- Device QA should inspect main, search, video/detail, channel, playlist, settings, feed-group, about, and ReCAPTCHA toolbar/overflow/menu surfaces; top and bottom/detail tabs; search entry/clear affordances; rotation during search/menu screens; and Light, Dark, Black, Follow system dynamic color, App default, and one manual palette such as Orange or Purple.

### Notification, update, debug, and report-adjacent settings polish (latest)

- Notification action configuration rows now place primary slot labels on `colorOnSurface`, helper/action summaries and row icons on `colorOnSurfaceVariant`, and compact-slot checkboxes on a state list that uses `colorPrimary` only for checked/active emphasis.
- The channel notification configuration screen now uses `colorSurface` for its root/list background, `colorOnSurface` for channel names, and the same checked/active primary state list for row indicators.
- The notification action chooser dialog keeps its existing single-choice behavior while moving row text to `colorOnSurface` and action icons to `colorOnSurfaceVariant`.
- The error report surface, which is reachable from debug/error flows, now uses `colorSurface` for the report body, `colorOnSurface` for headings/comment text, and `colorOnSurfaceVariant` for stack/device details and report helper copy.
- Scope was intentionally limited to XML/resource color attributes plus one visual-only icon tint attr lookup in the existing notification action chooser. Notification behavior, action selection/saving, update checking, debug/ACRA/error behavior, playback, downloads, extractor/service logic, queue/player overlays, database behavior, navigation, and settings preference logic were not changed.

Known risks / QA:
- Device QA should inspect Settings > Player notification action rows and chooser dialogs, Settings > Notifications channel toggles, Updates settings, Debug settings, reachable error-report surfaces, row checked/disabled states, rotation, and Light, Dark, Black, Follow system/dynamic color, App default, and one manual palette such as Orange or Purple.

### Backup, restore, import, export, and migration-adjacent polish (latest)

- Backup/restore import confirmation and optional settings-import prompts now use
  `MaterialAlertDialogBuilder`, matching the existing Material dialog role
  mapping for titles, body copy, actions, and surfaces while preserving the same
  confirmation/cancel listeners.
- The subscription import helper screen now explicitly uses the Material
  `colorSurface` role for its root surface and the existing primary text-button
  treatment for its import action; helper copy remains on
  `colorOnSurfaceVariant`, entered text remains on `colorOnSurface`, and the
  active input underline/action color remains `colorPrimary`.
- Scope was intentionally limited to dialog-builder selection and XML
  color/style attributes. Import/export behavior, file picker behavior, backup
  format, serialization/deserialization, database behavior, storage permissions,
  SAF/document-tree behavior, validation logic, subscription import/export work,
  playback, downloads, extractor/service logic, queue/player overlays,
  navigation, notification behavior, and settings preference logic were not
  changed.

Known risks / QA:
- Device QA should inspect Settings > Backup and restore, database import/export
  confirmation prompts, optional settings-import warning prompts, subscription
  import helper screens, previous-subscription-export import/export file-picker
  entry points where safe, cancel paths, rotation, and Light, Dark, Black, Follow
  system/dynamic color, App default, and one manual palette such as Orange or
  Purple.

### History, local feed, and local list management polish (latest)

- History sort/header controls now use a `colorSurface` header surface, `colorOnSurface` label text, and `colorOnSurfaceVariant` icon tint so the local history management row no longer falls back to platform/default text and vector colors.
- Shared local playlist/history playback controls now map active action labels and compound icons to `colorPrimary`, while the vertical separators use `colorSurfaceVariant` instead of the legacy accent role.
- The local feed refresh surface now sits on `colorSurface`, keeps its existing ripple/click target, and uses `colorSurfaceVariant` for its refresh-row divider while preserving the existing primary refresh affordance and on-surface title/helper text mapping.
- Scope was intentionally limited to XML resource color/tint attributes and this documentation. History sorting/deletion, feed loading/update behavior, watched-state handling, playlist/subscription behavior, import/export, navigation, database behavior, settings logic, downloads, notifications, extractor/service logic, playback, queue/player overlays, and player overlay colors were not changed.

Known risks / QA:
- Device QA should inspect History, Watch history clear confirmation up to the cancel path, Feed / What's New refresh and loading states, local playlist/history playback controls, local list management rows, rotation on those screens, and Light, Dark, Black, Follow system dynamic color, App default, and one manual palette such as Orange or Purple.

### Remaining Material 3 visual-gap audit (latest)

Audit scope and commands:
- Inspected `app/src/main/res/layout/`, `layout-land/`, `layout-large-land/`, `menu/`, `drawable/`, `color/`, `values/styles.xml`, `values/styles_misc.xml`, `values/colors.xml`, `app/src/main/java/org/schabi/newpipe/player/`, `app/src/main/java/org/schabi/newpipe/`, and the Material roadmap docs.
- `app/src/main/res/layout-large/` does not exist in this checkout, so no files were available to inspect there.
- XML/resource hard-coded color search: `rg -n --glob '*.xml' '#[0-9A-Fa-f]{3,8}' app/src/main/res/layout app/src/main/res/layout-land app/src/main/res/layout-large-land app/src/main/res/menu app/src/main/res/drawable app/src/main/res/color app/src/main/res/values/styles.xml app/src/main/res/values/styles_misc.xml app/src/main/res/values/colors.xml`.
- Legacy/platform color search: `rg -n 'textColorPrimary|textColorSecondary|colorAccent|colorControlNormal|@android:color|\?android:attr|\?attr/colorControl|youtube|Youtube|black|white|red|contrast' app/src/main/res/layout app/src/main/res/layout-land app/src/main/res/layout-large-land app/src/main/res/menu app/src/main/res/color app/src/main/res/drawable app/src/main/res/values/styles.xml app/src/main/res/values/styles_misc.xml app/src/main/res/values/colors.xml`.
- Player-sensitive color search: `rg -n 'Color\.RED|Color\.WHITE|Color\.BLACK|R\.color\.(white|black|.*youtube.*|.*red.*|dark_background_color)|colorAccent|setProgressTintList|setThumbTintList|setBackgroundColor|setColorFilter|PorterDuffColorFilter|setTextColor' app/src/main/java/org/schabi/newpipe/player app/src/main/java/org/schabi/newpipe/player -S`.

Audit summary:
- No broad code/resource changes were made in this audit. The output is documentation-only so the next pass can choose focused, risk-appropriate PRs.
- Remaining hard-coded layout colors are concentrated in media-thumbnail overlays and player/queue overlays: video-detail thumbnail hold/duration badges, channel/playlist avatar strokes, player seek preview/closing overlay, and player queue translucent backgrounds.
- The large hard-coded color count in `colors.xml` and manual-theme sections of `styles.xml` is mostly palette definition, not per-surface leakage. Those values are expected until the fork finishes dynamic/manual palette consolidation.
- Remaining legacy attrs are a mix of intentional compatibility (`stream_quality_item.xml` platform attrs), existing text appearance inheritance, and style/theme bridge attrs (`colorAccent`, `textColorPrimary`, `textColorSecondary`, `colorControlNormal`) still needed by AppCompat/preference/dialog widgets.
- Java color-sensitive player uses remain intentionally untouched: red/white/black `PorterDuffColorFilter` and loading-panel background calls in `VideoPlayerUi`, plus notification color setup in `NotificationUtil`.

Intentional exceptions to keep for now:
- Player overlay white/black/red affordances over video frames remain intentional and high-risk: seekbar/gesture feedback, seek preview, loading panels, close overlays, queue overlay backgrounds, and popup-player close affordances need dedicated real-device QA before any retheme.
- `stream_quality_item.xml` should continue using platform text color attrs for spinner row icon/text compatibility with both app Material themes and the plain `Theme.DeviceDefault` instrumentation-test theme used by `StreamItemAdapterTest`.
- OS/system-controlled chrome and inherited platform text appearances can remain unless a specific surface shows contrast regressions; replacing all `?android:attr/textAppearance*` uses at once would be broad and noisy.
- Palette resources in `colors.xml`, theme-preset values in `styles.xml`, launcher/icon artwork colors, placeholder artwork, white/black vector path fills, splash artwork, and named overlay/duration resources are not automatically defects just because they are hard-coded.
- Runtime playback, gesture, seek, notification, download worker, extractor/service, database, import/export, navigation, settings logic, and tests remain out of scope for this audit.

Remaining low-risk XML-only Material polish candidates:
- Channel and playlist avatar border strokes currently use literal white strokes in `fragment_channel.xml` and `playlist_header.xml`. A focused XML-only PR could move them to a named overlay/border color or a Material outline/on-surface-variant role after screenshot/manual contrast checks over real thumbnails.
- Video-detail thumbnail badges in `fragment_video_detail.xml` and `layout-large-land/fragment_video_detail.xml` use literal translucent black backgrounds plus white text for hold-to-append and duration chips. A focused XML-only PR could replace the literal values with existing named overlay/duration resources if the visual result stays identical across Light/Dark/Black.
- A narrow drawable cleanup could replace isolated vector-local black/white fill literals with existing `@android:color/*`, `@color/white`, `@color/black`, or theme tints only where the vector is not artwork/launcher/service identity and already receives runtime tint elsewhere.
- A small docs-backed resource cleanup could introduce names for repeated thumbnail overlay alphas, but should avoid changing actual colors or player resources.

Medium-risk candidates that need focused PRs and manual QA:
- Theme/style bridge cleanup: `colorAccent`, `textColorPrimary`, `textColorSecondary`, and `colorControlNormal` are still used in base, dialog, settings, notification, and misc styles to support AppCompat/Preference/Material interop. Any cleanup should be one surface at a time with Light/Dark/Black, dynamic color, App default, and one manual palette QA.
- Legacy YouTube progress drawables and theme aliases (`progress_youtube_horizontal_*`, remaining YouTube primary references in misc themes) should still be audited in exact remaining consumers before renaming or remapping. Stream watched-progress bars no longer use the global alias, but player/download/legacy widgets may still rely on its contrast.
- License/about and preference-template platform text appearances can be polished, but should be handled as a focused style pass because these layouts inherit framework/AppCompat sizing and disabled-state behavior.
- Info-list duration/live badges have named resources but still use old high-contrast overlay colors. Retheming them should be separated from player work and checked on stream lists, grids, cards, mini rows, and live items.
- Notification color setup should stay in a notification-focused PR because Android system rendering and media-session behavior can override or reinterpret app color roles.

High-risk / deferred areas:
- Main player overlay, popup player overlay, queue overlay, seekbar/progress/gesture feedback, brightness/volume/fast-seek feedback, caption/audio/quality/speed popups, loading/error overlays inside the player, and closing overlay remain deferred.
- Download worker/mission behavior, file picker/storage permission paths, extractor/service/network logic, database/migration behavior, import/export serialization, navigation behavior, settings persistence, notification action behavior, and tests remain deferred.
- Broad palette consolidation or dynamic color rewiring across all theme presets remains deferred until the remaining widget bridge attrs are mapped and tested.

Recommended next safe PR order:
1. Documentation-backed XML cleanup for non-player thumbnail/avatar literals only: channel/playlist avatar strokes and video-detail thumbnail badges, preserving visual values or mapping to already-used named overlay resources.
2. Info-list duration/live badge resource audit outside the player, with list/grid/card/mini-row screenshots or manual QA notes.
3. Preference/license/about platform text appearance/style pass, limited to one family of layouts per PR.
4. Theme/style bridge attr cleanup (`colorAccent`, `textColorPrimary`, `textColorSecondary`, `colorControlNormal`) after the above low-risk surfaces are stable.
5. Dedicated player-controls visual pass last, with explicit device QA for fullscreen, embedded, popup, background/audio, queue, speed/quality/audio/captions menus, gestures, rotation, and all theme/color modes.

Remaining risks / follow-ups:
- Some remaining search hits are names rather than visible problems (`youtube` drawable names, `black` theme mode resources, `white` icon artwork), so future PRs should verify actual rendered surfaces rather than mechanically replacing every match.
- Theme attr changes can affect many widgets indirectly; prefer one-surface PRs with manual QA notes instead of global replacements.
- Player overlay contrast cannot be evaluated from resource names alone because it depends on arbitrary video frames and runtime control visibility.

### Non-player thumbnail/avatar XML cleanup (latest)

- Applied the first low-risk follow-up from the remaining visual-gap audit, limited to non-player XML/resource color references and documentation.
- Channel and playlist avatar borders now use the named `thumbnail_avatar_stroke_color` resource instead of inline white literals, preserving the existing subtle white circular border intent for real channel/playlist artwork and placeholders.
- Video-detail thumbnail overlays in portrait and large-land now use named resources for the hold-to-append overlay and duration badge backgrounds. The resource values intentionally match the previous inline values so badge readability and thumbnail contrast remain visually equivalent.
- Video-detail thumbnail badge text, including duration and position badges, now references the app `white` color resource instead of `@android:color/white`, again preserving the previous rendered color while avoiding platform color literals in these non-player badge layouts.
- Deferred/skipped: player overlay resources, queue overlay backgrounds, seek preview, closing overlay, player gesture/seek feedback, speed/quality/audio/caption popups, launcher/icon/splash artwork, placeholder artwork, broad theme attrs, and info-list duration/live badge retheming remain untouched.
- Scope preservation: no IDs, layout structure, click handling, playback behavior, navigation, settings, downloads, extractor/service logic, database/import/export behavior, notification behavior, or tests were changed.

Known risks / QA:
- Manual QA should inspect channel pages, playlist/bookmarked playlist headers, and video-detail thumbnails in portrait and large-land/tablet across Light, Dark, Black, Follow system dynamic color, App default, and one manual color. Confirm avatar borders stay subtle/readable, thumbnail badges stay readable over real thumbnails, and player overlays are unchanged.

### Info-list duration/live badge audit (latest)

- Audited the duration and live badge surfaces used by stream list, mini, grid, card, playlist-row, playlist-grid, playlist-card, playlist-count, channel/search/feed, and related-video row variants. The visible chips sit directly on arbitrary thumbnail artwork, so the existing translucent black duration background, red live background, opaque playlist-count scrim, and off-white text are intentionally high-contrast overlay colors rather than general Material surface roles.
- XML-only cleanup was limited to the static info-list row layouts: stream duration badges and playlist stream-count overlays now reference info-list-specific thumbnail badge color resource names while preserving the previous rendered values exactly. Badge IDs, placement, padding, visibility, adapter binding, duration formatting, live-state handling, progress handling, and row behavior were not changed.
- Skipped/deferred: the programmatic live/duration background assignments in info-list/local holders still use the existing shared duration/live resources, and video-detail/player/queue overlay duration resources remain untouched. Any future split of those programmatic resources should be a narrow non-XML follow-up with explicit player/queue exclusion checks.

Known risks / QA:
- Device QA should inspect home/feed rows, search results, related videos, channel/playlist rows, list/grid/mini/card variants, playlist stream-count overlays, and live rows where available. Check duration/live badge contrast over light and dark thumbnails across Light, Dark, Black, Follow system/dynamic color, App default, and one manual palette such as Orange or Purple. Confirm player overlay time/seek controls and queue overlays are unchanged.

### Dialog bridge attr audit (latest)

- Reviewed the previous bridge-attr audit and the scoped settings bridge cleanup before making any dialog-wide changes. This pass stayed audit-first and only touched dialog theme-color overlay resources.
- Dialog-scoped classification:
  - Already Material-safe: base app/dialog `colorAccent` mappings already point at Material secondary roles, base dialog `colorControlActivated` already points at `colorPrimary`, and dialog/settings `android:textColorPrimary`/`android:textColorSecondary` bridge values already map to `colorOnSurface`/`colorOnSurfaceVariant`.
  - Safe tiny cleanup: manual theme-color dialog overlays accidentally overrode dialog `colorControlActivated` with fixed secondary-palette values. They now resolve through `?attr/colorPrimary`, matching the base dialog active-control role while preserving the existing MaterialAlertDialog theme parents, surfaces, title/body text roles, and button behavior.
  - Needs manual dialog QA: dialog-wide `colorAccent` remains on secondary in base dialog themes and in manual dialog overlays because it can influence AppCompat/custom dialog widgets beyond MaterialAlertDialog buttons.
  - Third-party/file-picker scoped and deferred: `FilePickerThemeLight`, `FilePickerThemeDark`, `FilePickerAlertDialogThemeLight`, and `FilePickerAlertDialogThemeDark` keep their existing `colorAccent` bridges for a separate file-picker pass.
  - Player/download/behavior-sensitive and deferred: player/queue overlay `colorAccent`, playback/speed/quality/audio/caption popup surfaces, `stream_quality_item.xml`, and download/file-picker/storage flows remain untouched.
- Skipped broad replacements for `textColorPrimary`, `textColorSecondary`, `android:textColorPrimary`, `android:textColorSecondary`, and `colorButtonNormal` because the remaining hits are either already role-mapped, toolbar/search/settings scoped, service-button scoped, file-picker scoped, or behavior-sensitive.
- Recommended manual QA: open the Appearance theme-color restart dialog, generic text-input dialogs, playlist/feed-group/subscription dialogs, backup/import confirmation dialogs, and license detail dialog. Check buttons, radio/checkbox controls, text contrast, and active states in Light, Dark, Black, Follow system dynamic color, App default, and one manual palette.

### File-picker bridge attr audit (latest)

- Audited `FilePickerThemeLight`, `FilePickerThemeDark`, `FilePickerAlertDialogThemeLight`, and `FilePickerAlertDialogThemeDark` plus the app entry points that launch the integrated NoNonsense FilePicker helper for legacy file-based paths. The pass was documentation-only because no mapping was clearly safer than the existing third-party bridge contract.
- File-picker bridge attr classification:
  - `colorAccent`: third-party compatibility/deferred. The four file-picker styles still provide the legacy AppCompat accent bridge consumed by NoNonsense FilePicker widgets and alert wrappers. Repointing it to a Material role could change selection/action affordances inside the integrated picker, so it needs device QA before any visual cleanup.
  - `colorControlActivated`: behavior/storage-sensitive by inheritance/deferred. The file-picker styles do not set it directly, and adding or overriding it could affect third-party checked/selected controls without a visible XML-only guarantee.
  - `colorButtonNormal`: third-party compatibility/deferred. The file-picker styles do not set it directly; AppCompat dialog/button defaults remain under the picker/dialog parents.
  - `colorControlNormal`: third-party compatibility/deferred. The file-picker styles do not set it directly; inherited icon/control tint behavior remains untouched.
  - `textColorPrimary` and `textColorSecondary`: third-party compatibility/deferred. The file-picker styles do not set non-android text bridges directly; inherited picker row/breadcrumb/dialog text behavior remains untouched.
  - `android:textColorPrimary` and `android:textColorSecondary`: third-party compatibility/deferred. The file-picker styles do not set platform text bridges directly; adding them could change list rows, path/breadcrumb labels, or alert wrapper text outside this audit's safe scope.
- Already Material-role mapped: none of the file-picker-local bridge attrs are currently mapped to Material roles. The picker still uses its existing light/dark YouTube primary, settings-accent, background, and separator resources to preserve current third-party integration behavior.
- Safe tiny cleanup: none applied. The only direct audited bridge attr is `colorAccent`, and changing it is not clearly safe without manual picker QA.
- Scope preservation: no file-picker launch intents, selected paths, SAF/storage permission handling, import/export flows, download folder selection, settings logic, downloads, playback, extractor/service logic, notifications, player overlay, queue overlay, or dialog behavior outside the file-picker theme audit were changed.
- Recommended manual QA: open Settings > Download folders, launch both video and audio folder pickers, visit backup/export/import flows only up to safe picker entry points, inspect toolbar, list rows, breadcrumbs/path text, action buttons, selection states, and alert wrappers, then cancel and verify selected paths are unchanged across Light, Dark, Black, Follow system dynamic color, App default, and one manual palette.

### Notification color setup audit (latest)

- Audited notification color/icon setup across the player/media notification builder, notification action icon selection, app notification-channel creation, new-stream notifications, feed-loading foreground notifications, error-report notifications, preferred-player-fetcher foreground notification, and download notification call sites. This pass is documentation-only because notification rendering is OS/OEM-controlled and no clearly safe resource-only cleanup was found.
- Notification color/icon classification:
  - Android/system-controlled and should remain: notification channels keep their existing ids, names, descriptions, and importance levels; Android 13+ media notification actions remain delegated to `MediaSessionPlayerUi`; small notification icons remain the existing monochrome/status-bar-compatible resources used by the platform.
  - Notification compatibility requirement: player notifications keep `setSmallIcon(R.drawable.ic_newpipe_triangle_white)`, `setColor(R.color.dark_background_color)`, user-controlled `setColorized(...)`, `MediaStyle`, public visibility, transport category, and thumbnail large-icon behavior because these values affect OS-rendered media notification contrast and compatibility.
  - Notification compatibility requirement: new-stream notifications keep the launcher-background color hint, forced colorized state, status-bar small icon, channel/group behavior, and optional avatar large icon because these are rendered by the notification template and channel subscription UX.
  - Android/system-controlled and should remain: error-report, feed-loading, preferred-player-fetcher, and download notifications keep their existing small icons and default/non-colorized template rendering; download notifications intentionally use Android system download icons and are outside this visual pass.
  - Medium-risk OS/OEM behavior-sensitive: replacing notification color hints with Material theme roles, changing colorized defaults, changing small/large icon resources, or adding notification-specific theme attrs could render differently across Android versions, OEM notification skins, dark/light system modes, and Android 13+ media controls.
  - High-risk playback/service behavior-sensitive: media session token attachment, foreground-service start/stop, notification action lists, compact action slots, delete/content intents, notification channel ids/importance, notification permission/channel checks, and download/feed foreground notifications remain untouched.
- Safe tiny cleanup: none applied. The only obvious color hints are already notification-specific compatibility choices or OS-template hints, and changing them is not clearly safe without device QA.
- Intentional exceptions: existing status-bar-compatible white/monochrome notification icons, launcher-background and dark-background notification color hints, ExoPlayer media action icons, Android system download icons, and thumbnail/avatar large icons remain as compatibility values rather than general Material surface roles.
- Scope preservation: notification actions, media controls, media session behavior, playback, background/popup behavior, download notifications, service lifecycle, foreground-service behavior, Android 13 notification behavior, channel behavior, settings logic, database behavior, extractor/service logic, player overlay, queue overlay, file-picker, `stream_quality_item.xml`, seek/gesture feedback, playback popups, launcher/splash artwork, and brand identity colors were not changed.
- Recommended manual QA: start video playback, inspect the foreground/player notification, test play/pause/previous/next/repeat/shuffle where visible, switch to background playback, inspect Light and Dark system notification rendering, check Android 13+ notification permission/channel behavior where available, verify notification actions still control playback, and spot-check new-stream/error/feed-loading/download notifications if those flows are available.

### Final Material 3 readiness audit (latest)

- Documentation/readiness pass only. Cross-checked this experiment log with the roadmap, base theme resources, miscellaneous theme overlays, color resources, CI/release workflows, README, and Fastlane image inventory; no contradictory Material-readiness guidance was found, and no code/resource visual changes were made.
- Completed low-risk Material 3 areas now documented as ready for release QA: cold-start splash/theme foundation, bottom navigation including 4/5-tab Material navigation and the >5 fallback, theme-color handling with Apply now/Restart/Later prompt, settings rows/switches/search/notification/update/debug surfaces, About and license screens, download dialog plus downloads manager, snackbars, video-detail metadata/thumbnail surfaces, playback-parameter dialog chrome, playlist/subscription/feed-group surfaces, search/list rows, empty/retry/loading states, generic dialogs/pickers, toolbar/app-bar/search/overflow/tab styling, backup/import/export/migration-adjacent prompts, local library/feed/history surfaces, thumbnail/badge resource naming, bridge-attr audits, file-picker audit, and notification color/icon audit.
- Still deferred by design: player overlay, queue overlay, seekbar/gesture/fast-seek feedback, speed/quality/audio/caption popups, notification visual changes, and file-picker visual changes. These areas remain OS/OEM, media-session, storage, arbitrary-video-frame, or third-party-widget sensitive and should not be rethemed without dedicated real-device QA.
- Release/manual QA checklist: verify Light, Dark, Black, Follow system dynamic color, App default, and one manual palette such as Orange or Purple; rotate key screens; cold-start the app and inspect splash; verify bottom navigation with 4/5 tabs and >5 fallback; test Settings > Appearance theme color Apply now, Restart, and Later; inspect About/licenses; exercise download dialog and downloads manager rows; inspect search/list rows, playlists, subscriptions, feed groups, empty/retry states, local library/feed/history, backup/import/export safe paths, generic dialogs/pickers, toolbar/search/overflow/tabs, notifications, file picker launch/cancel paths, and a player/queue overlay smoke test confirming those overlays were not rethemed.
- Release packaging note: CI and signed-release workflows already build debug/release APK artifacts, while Fastlane screenshots/graphics remain existing assets. Refresh store screenshots only after the manual QA matrix passes on real devices.

### Current Material defaults note: bundled preference snapshot

- NewPipe Material now keeps its canonical default SharedPreferences baseline in a bundled raw JSON resource, `app/src/main/res/raw/newpipe_material_default_preferences.json`.
- Fresh installs and cleared app data apply this snapshot before normal preference defaults are read, so Material defaults such as Follow system theme color, automatic/device theme, dark night theme, bottom main tabs, card list mode, the five-tab saved layout, 720p60 video resolution, 480p popup resolution, and 1.2 playback speed are available immediately.
- The Reset settings flow clears only the existing default SharedPreferences file as before, then reapplies the same bundled snapshot before restarting the app. Databases, watch history, subscriptions, downloads, cache, queues, app identity, signing, player behavior, download behavior, navigation behavior, and `shared/` remain outside this reset change.
- No keys from the supplied snapshot are intentionally skipped. Runtime-looking entries such as `is_in_background`, `import_export_data_path`, `kao_last_checked`, `stream_info_selected_tab`, `service`, and `media_tunneling_device_blacklist_version` are applied as part of the canonical baseline; the pre-existing media-tunneling first-run helper may still add device-specific tunneling-disable preferences when required.
- Existing users are protected by the first-run/version flow and are not overwritten on normal app updates. A `newpipe_material_defaults_applied` marker records that the Material baseline was installed.

### Stream thumbnail polish (latest)

- Stream thumbnail polish: rounded preview thumbnails for list/grid/card and card-mode horizontal margins.
- The stream list, mini/related, grid, and card item preview images now use the shared NewPipe Material stream-thumbnail shape overlay with 12dp rounded corners while preserving existing thumbnail IDs, scale types, fixed/grid/list sizing, card 16:9 constraint, image loading calls, progress visibility, click handling, and long-click handling.
- Card-mode stream items now keep a 12dp horizontal inset from the RecyclerView/screen edges so full-width card previews are no longer edge-to-edge.
- Duration and live badges remain constrained to the bottom/end of the existing thumbnail views; playback/loading behavior is unchanged.
