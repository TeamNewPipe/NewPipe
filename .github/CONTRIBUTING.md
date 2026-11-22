# Contributing to WizeStream

Thank you for your interest in contributing to WizeStream.

WizeStream is an independent, NewPipe-based streaming client for Android. It combines Material 3
Expressive design with privacy-friendly playback, local-first features, multiple streaming
platforms, and its own integrated extractor changes while preserving the strengths of the core
NewPipe experience.

This project welcomes focused contributions, but changes must respect the project direction and avoid unnecessary behavior changes.

---

## Project direction

WizeStream focuses on:

* Material 3 visual polish
* Dynamic and manual theme colors
* WizeStream app identity
* Privacy-friendly playback and downloads
* Useful local-first features
* Reliable support for multiple streaming platforms
* Maintained, integrated extractor and service compatibility
* Release-ready signed builds
* Clear fork attribution
* Preserving compatible NewPipe behavior and data migration where practical
* Safe, reviewable, well-scoped changes

WizeStream is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe, or NewPipe e.V.

---

## Maintainer policy

This repository is maintained by Wisso.

All contributions are reviewed according to the goals of WizeStream. The maintainer may accept, request changes, delay, or reject contributions based on project direction, quality, risk, maintainability, or release timing.

Please do not treat an opened issue or pull request as approval to implement broad changes. Large changes should be discussed first.

---

## What contributions are welcome?

Good contribution types include:

* Focused Material 3 UI polish
* Bug fixes
* Playback, download, and service-compatibility fixes
* Focused local-first feature improvements
* Documentation improvements
* Translation improvements
* Release-readiness fixes
* Accessibility improvements
* Build, CI, and signing workflow fixes
* Small refactors that reduce risk or improve maintainability
* QA reports with screenshots, device info, and reproduction steps

---

## Changes that need prior discussion

Please open an issue or discussion before working on:

* Player overlay redesigns
* Queue overlay changes
* Seekbar, gesture, or fast-seek visual changes
* Playback behavior changes
* Download behavior changes
* Extractor or service logic changes
* Database or migration changes
* Import/export behavior changes
* Notification behavior changes
* File-picker behavior changes
* Large theme rewrites
* Large refactors
* New features that affect user workflows

These areas are sensitive and require dedicated QA.

---

## Pull request rules

Please keep pull requests focused.

A good pull request should:

* Solve one clear problem
* Explain the root cause or polish reason
* List the exact files changed
* Preserve behavior unless behavior change is explicitly intended
* Avoid unrelated formatting changes
* Avoid broad rewrites
* Avoid hard-coded colors when Material theme roles can be used
* Include screenshots for UI changes when possible
* Include test results

Do not bundle unrelated changes into one pull request.

Examples of good PR scope:

* “Polish settings row colors”
* “Fix README repository links”
* “Add release signing validation”
* “Retheme one dialog surface safely”

Examples of poor PR scope:

* “Modernize everything”
* “Refactor app UI”
* “Change player, downloads, settings, and README together”
* “Replace all colors globally”

---

## Material 3 UI contribution rules

For UI changes:

* Prefer existing Material theme roles such as `colorSurface`, `colorOnSurface`, `colorOnSurfaceVariant`, `colorPrimary`, `colorPrimaryContainer`, and related role colors.
* Avoid hard-coded colors unless they are intentional overlay, artwork, badge, or compatibility values.
* Keep player overlays and media controls separate from normal app surfaces.
* Preserve readable contrast in Light, Dark, Black, dynamic color, App default, and manual theme palettes.
* Do not change IDs, listeners, adapters, or behavior for visual-only work.
* Document any intentional exceptions.

Player overlays, queue overlays, notification templates, and file-picker surfaces are high-risk and need real-device QA before visual changes.

---

## Behavior-change rule

WizeStream is broader than a visual or identity redesign, but every behavior change must solve a
clear, approved problem and remain focused, testable, and maintainable.

Do not change behavior incidentally. A pull request that changes behavior must identify that
behavior explicitly and explain the intended user impact.

Behavior-sensitive areas include:

* Playback
* Background playback
* Popup playback
* Downloads
* Queue handling
* Extractor/service logic
* Subscriptions
* Import/export
* Database migrations
* Notifications
* File picker and storage flows
* Settings defaults
* Navigation

When behavior changes are necessary, explain why and provide testing details.

---

## Testing requirements

Before opening a pull request, run:

```bash
git diff --check
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
```

For Android/device-sensitive changes, also run or request device QA.

Recommended manual QA for UI changes:

* Light theme
* Dark theme
* Black theme
* Follow system / dynamic color
* App default theme color
* At least one manual color such as Orange or Purple
* Rotation
* Relevant screen navigation
* Relevant dialogs, menus, and empty states

For release-related changes, verify APK identity, signing, artifact upload, install behavior, and About screen version display.

---

## Screenshots and QA reports

For visual changes, include screenshots when possible.

A useful QA report includes:

* Device model
* Android version
* App build type
* Theme mode
* Theme color
* Steps to reproduce
* Expected result
* Actual result
* Screenshot or screen recording

---

## Translations

Translation contributions are welcome.

Please keep translations faithful to the English source and avoid changing technical meaning. New fork-specific strings may be marked with `tools:ignore="MissingTranslation"` temporarily, but proper translations are preferred over time.

---

## Issues

When opening an issue, include:

* Clear title
* App version
* Device and Android version
* Steps to reproduce
* Expected behavior
* Actual behavior
* Screenshots or logs if relevant

Report service and extractor breakages observed in WizeStream to WizeStream first. Reproducing the
problem in official NewPipe is useful for identifying shared upstream defects, but is not a reason
to reject a WizeStream report. WizeStream owns defects caused by its integrated extractor changes.

---

## Respect upstream NewPipe

WizeStream is based on NewPipe and preserves upstream credits and license notices.

Please respect upstream NewPipe, TeamNewPipe, NewPipe e.V., and the NewPipe community. Problems
observed in WizeStream belong in this repository first. Upstream issues should be reported upstream
only when they are reproducible in the official upstream project, are not caused by WizeStream's
changes, and follow the upstream project's reporting rules.

---

## License

By contributing to WizeStream, you agree that your contribution will be distributed under the same license as the project.

WizeStream is free software based on NewPipe and is distributed under the GNU General Public License version 3 or later.

## Maintaining the in-app changelog

- Keep cumulative release notes in `docs/changelogs.md`, newest first. Use
  ``## WizeStream X.Y.Z (`VERSION_CODE`)`` for release headings and `## Unreleased` for work that
  has not shipped yet. Preserve the existing older release entries.
- Keep pending changes under Unreleased even when `buildSrc/src/main/kotlin/ProjectConfig.kt`
  already identifies the next development version. When preparing an actual release, move the
  relevant notes into a numbered section matching that version name and code, and add its
  `fastlane/metadata/android/en-US/changelogs/VERSION_CODE.txt` summary.
- Run `python3 scripts/generate-changelog.py` and commit `app/src/main/assets/changelog.html`
  together with the Markdown changes. CI uses `--check` to reject stale assets. A development
  version newer than all numbered entries can use nonempty Unreleased notes; an existing numbered
  entry or recorded publication date still requires matching numbered notes. The generator uses
  only Python standard-library modules, and its tests run with
  `python3 -B -m unittest discover -s scripts/tests -p 'test_generate_changelog.py' -v`.
- Before publishing, the release workflow runs
  `python3 scripts/generate-changelog.py --check --require-release`
  and requires the Fastlane summary. Unreleased notes cannot satisfy this gate.
- The first-run dialog shows the matching installed release when no previous acknowledgement exists.
  Subsequent upgrades show releases newer than the last acknowledged version, up to the installed
  release. Nightly suffixes use their named base release. A development version without a matching
  numbered entry has no automatic release dialog; Unreleased notes remain in the full history.
