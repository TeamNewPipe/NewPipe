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
