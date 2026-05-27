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

### Cards
- `feed_group_card_item` root migrated to `MaterialCardView`.
- `feed_group_add_new_item` root migrated to `MaterialCardView`.
- Conservative Material card styling normalized for feed-group cards:
  - elevation
  - ripple
  - stroke

### Dialogs
- Playlist creation flow migrated to `MaterialAlertDialogBuilder`.
- History delete playback-states confirmation flow migrated to `MaterialAlertDialogBuilder`.
- History delete search-history confirmation flow migrated to `MaterialAlertDialogBuilder`.
- History delete watch-history confirmation flow migrated to `MaterialAlertDialogBuilder`.

### Checkstyle cleanup
- Fixed known `LineLength` violation in `PlaylistCreationDialog`.
- `./gradlew runCheckstyle -DskipFormatKtlint` passes.

## Validation status

- ✅ Checkstyle: `./gradlew runCheckstyle -DskipFormatKtlint` passes.
- ⚠️ `./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint` fails
  in this container due to missing Android SDK configuration (`ANDROID_HOME`/`sdk.dir`).

## Manual QA checklist

- [ ] App launch
- [ ] Drawer
- [ ] Settings
- [ ] Feed group cards
- [ ] Playlist creation dialog
- [ ] History delete dialogs
- [ ] Download dialog
- [ ] Video detail
- [ ] Light / Dark / Black themes
- [ ] Rotation

## Known risks

- Material 3 theme parent compatibility regressions in edge screens.
- `MaterialToolbar` tint/title/elevation nuance differences versus prior toolbar behavior.
- Visual/ripple/elevation differences between `CardView` and `MaterialCardView`.
- `MaterialAlertDialogBuilder` rendering differences versus `AlertDialog.Builder`.
- Dialog width behavior variance on tablet/foldable form factors.

## Future commit rule (branch discipline)

- Migrate **one component** or **one dialog flow** per commit.
- Run Checkstyle and build validation after each change.
- Smoke-test exactly the flow touched by the commit.
