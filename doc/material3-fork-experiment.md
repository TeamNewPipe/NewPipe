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
- Result: only `feed_group_add_new_grid_item` and `feed_group_card_grid_item` still use `CardView`; both are `feed_group_*` and excluded by task rules.
- Migration action in this step: none (no safe new non-feed-group, non-player, non-download `CardView` target found).
- Validation for this audit/doc step:
  - `./gradlew runCheckstyle -DskipFormatKtlint`: PASS
  - `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint`: not run in this container step
- Manual QA for this audit/doc step: not applicable (no new layout migration performed).

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
