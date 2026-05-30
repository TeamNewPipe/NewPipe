<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="assets/new_pipe_icon_5.png" width="150" alt="NewPipe Material icon"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>A Material 3-focused independent fork of NewPipe for Android.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="Build status"></a>
</p>

<p align="center"><b>Read this in:</b> <a href="README.md">English</a> &bull; <a href="doc/README.de.md">Deutsch</a> &bull; <a href="doc/README.es.md">Español</a> &bull; <a href="doc/README.fr.md">Français</a> &bull; <a href="doc/README.hi.md">हिन्दी</a> &bull; <a href="doc/README.it.md">Italiano</a> &bull; <a href="doc/README.ko.md">한국어</a> &bull; <a href="doc/README.pt_BR.md">Português Brasil</a> &bull; <a href="doc/README.pl.md">Polski</a> &bull; <a href="doc/README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="doc/README.ja.md">日本語</a> &bull; <a href="doc/README.ro.md">Română</a> &bull; <a href="doc/README.ru.md">Русский</a> &bull; <a href="doc/README.so.md">Soomaali</a> &bull; <a href="doc/README.tr.md">Türkçe</a> &bull; <a href="doc/README.zh_TW.md">正體中文</a> &bull; <a href="doc/README.ryu.md">沖縄口</a> &bull; <a href="doc/README.asm.md">অসমীয়া</a> &bull; <a href="doc/README.sr.md">Српски</a> &bull; <a href="doc/README.ar.md">العربية</a></p>

---

## Important fork notice

NewPipe Material is an independently maintained fork of NewPipe focused on Material 3 design, app theming, and product polish.

It is **not affiliated with, sponsored by, or endorsed by** the official NewPipe project, TeamNewPipe, or NewPipe e.V.

NewPipe Material is built from NewPipe and keeps the NewPipe libre software license, upstream credits, and third-party license notices.

---

## What is NewPipe Material?

NewPipe Material keeps the core NewPipe experience while modernizing the app identity and user interface.

Current fork goals:

- Material 3-inspired app surfaces, dialogs, settings, tabs, and navigation
- Dynamic Material You color support where available
- Manual Theme color presets such as App default, Neutral, Green, Blue, Purple, Orange, Pink, and Red
- New app identity: **NewPipe Material**
- Separate application ID: `org.wisso.newpipematerial`
- Debug builds install separately as `org.wisso.newpipematerial.debug`
- Preserve NewPipe behavior, import/export compatibility, and supported services

This fork intentionally avoids risky behavior changes in sensitive areas such as playback, downloads, background playback, popup playback, and extractor logic unless they are handled as dedicated, tested changes.

---

## Screenshots

### Phone

<p align="center">
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="Phone screenshot 1"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="Phone screenshot 2"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="Phone screenshot 3"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="Phone screenshot 4"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="Phone screenshot 5"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="Phone screenshot 6"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="Phone screenshot 7"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="Phone screenshot 8"></a>
  <a href="fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="Phone screenshot 9"></a>
</p>

### Tablet

<p align="center">
  <a href="fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="Tablet screenshot 1"></a>
  <a href="fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="Tablet screenshot 2"></a>
</p>

---

## Supported services

NewPipe Material inherits NewPipe support for these services:

- YouTube and YouTube Music
- PeerTube
- Bandcamp
- SoundCloud
- media.ccc.de

Service support depends on the upstream NewPipe and NewPipe Extractor codebase.

---

## Features

NewPipe Material keeps the familiar NewPipe feature set, including:

- Watch videos and live streams
- Background playback
- Popup player
- Local playlists
- Subscriptions without signing in to a platform account
- Channel groups and feeds
- Search and browse supported services
- View video details, related videos, and comments where supported
- Download video, audio, and captions where supported
- Import/export app data for migration and backup

Material-focused additions include:

- Material 3 theme roles across more app surfaces
- Bottom navigation for five or fewer main tabs, with scrollable TabLayout fallback for larger tab sets
- Default bottom main tab position for new/unset installs
- Dynamic/manual Theme color support
- NewPipe Material fork attribution in the About screen
- Release signing support for fork builds

---

## Installation

### Release APK

Install NewPipe Material from this repository's GitHub releases or signed build artifacts when available.

NewPipe Material uses a different application ID from official NewPipe, so it can install side by side with the official app:

```text
Official NewPipe:  org.schabi.newpipe / net.newpipe.app depending on upstream build
NewPipe Material:  org.wisso.newpipematerial
Debug build:       org.wisso.newpipematerial.debug
```

### Migrating data

NewPipe Material does not automatically share app data with official NewPipe.

To migrate:

1. Open official NewPipe.
2. Export your database from Settings > Backup and Restore.
3. Install NewPipe Material.
4. Import the exported database from Settings > Backup and Restore.

Always keep a backup before importing data between builds.

### Google Play warning

Do not publish NewPipe Material, NewPipe, or forks of NewPipe to Google Play. This project follows the same practical distribution caution as upstream NewPipe.

---

## Building from source

Requirements:

- JDK 21
- Android SDK
- Gradle wrapper from this repository

Useful validation commands:

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
```

Build a debug APK:

```bash
./gradlew assembleDebug -DskipFormatKtlint
```

The debug APK uses the app label **NewPipe Material Debug** and package `org.wisso.newpipematerial.debug`.

---

## Release signing

Release signing is configured through environment variables:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

When all four values are present, the release build uses the configured signing key. If they are missing, the release signing config is not applied.

Recommended release validation:

```bash
./gradlew assembleRelease -DskipFormatKtlint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## Development status

NewPipe Material is a fork in active Material 3 polish and productization work.

Completed or in-progress fork areas include:

- App name and application ID
- Debug/release identity separation
- Material 3 theme colors
- Dynamic/manual Theme color handling
- Bottom navigation and main tab polish
- About screen fork attribution
- Dialog, snackbar, settings, video detail, and download UI polish
- Release signing workflow support

Deferred or high-risk areas:

- Main player overlay retheme
- Seekbar and gesture overlay colors
- Queue overlay controls
- Quality/audio/caption popup behavior
- Broad playback/download behavior changes

Those areas need dedicated QA before visual or behavior changes.

---

## Contributing

Contributions are welcome, especially focused Material 3 polish, bug fixes, QA findings, documentation, and release-readiness work.

Please keep changes focused and testable. For UI work, include before/after screenshots where possible and verify Light, Dark, Black, Follow system, and at least one manual Theme color preset.

Useful checks before opening a pull request:

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
```

---

## Upstream NewPipe

NewPipe Material is based on NewPipe.

Upstream resources:

- NewPipe repository: https://github.com/TeamNewPipe/NewPipe
- NewPipe website: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Please report issues carefully:

- Fork-specific design, identity, release, or Material 3 issues belong in this repository.
- Upstream extractor/service breakages may also need to be checked against official NewPipe.

---

## Donate

If you want to support upstream NewPipe, see the official NewPipe donation page:

https://newpipe.net/donate

NewPipe Material is an independent fork; upstream donations go to the upstream NewPipe project, not automatically to this fork.

---

## License

NewPipe Material is free software based on NewPipe and is distributed under the GNU General Public License version 3 or later.

See the repository license files and in-app license screen for full license and third-party notice details.
