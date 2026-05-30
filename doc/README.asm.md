<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/new_pipe_icon_5.png" width="150" alt="NewPipe Material আইকন"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Android-ৰ বাবে Material 3-কেন্দ্ৰিত NewPipe-ৰ এটা স্বতন্ত্ৰ fork।</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="Build status"></a>
</p>

<p align="center"><b>এই ভাষাত পঢ়ক:</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## fork সম্পৰ্কে গুৰুত্বপূর্ণ জাননী

NewPipe Material হৈছে NewPipe-ৰ এটা স্বতন্ত্ৰভাৱে maintain কৰা fork, যি Material 3 design, app theming আৰু product polish-ৰ ওপৰত কেন্দ্ৰিত।

এই project official NewPipe project, TeamNewPipe বা NewPipe e.V.-ৰ সৈতে **সংযুক্ত, sponsored বা endorsed নহয়**।

NewPipe Material, NewPipe-ৰ ওপৰত নিৰ্মিত আৰু NewPipe-ৰ libre software license, upstream credits আৰু third-party license notices সংৰক্ষণ কৰে।

---

## NewPipe Material কি?

NewPipe Material-এ NewPipe-ৰ core experience বজাই ৰাখি app identity আৰু user interface আধুনিক কৰে।

এই fork-ৰ লক্ষ্য:

- Material 3 inspired surfaces, dialogs, settings, tabs আৰু navigation
- উপলব্ধ হ’লে Material You dynamic colors
- manual Theme color presets: App default, Neutral, Green, Blue, Purple, Orange, Pink আৰু Red
- নতুন app identity: **NewPipe Material**
- পৃথক application ID: `org.wisso.newpipematerial`
- debug builds পৃথককৈ `org.wisso.newpipematerial.debug` হিচাপে install হয়
- NewPipe behavior, import/export compatibility আৰু supported services সংৰক্ষণ কৰা

এই fork-এ playback, downloads, background playback, popup playback আৰু Extractor logic-ৰ দৰে sensitive areas-ত risky behavior changes পৰিহাৰ কৰে, যদি সেইবোৰ dedicated আৰু tested changes হিচাপে কৰা নহয়।

---

## Screenshots

### Phone

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="Phone screenshot 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="Phone screenshot 2"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="Phone screenshot 3"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="Phone screenshot 4"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="Phone screenshot 5"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="Phone screenshot 6"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="Phone screenshot 7"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="Phone screenshot 8"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="Phone screenshot 9"></a>
</p>

### Tablet

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="Tablet screenshot 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="Tablet screenshot 2"></a>
</p>

---

## Supported services

NewPipe Material-এ NewPipe-ৰ YouTube, YouTube Music, PeerTube, Bandcamp, SoundCloud আৰু media.ccc.de support inherit কৰে।

---

## Features

NewPipe Material-এ NewPipe-ৰ চিনাকি features ৰাখে: videos আৰু live streams, background playback, popup player, local playlists, platform account অবিহনে subscriptions, channel groups, search, video details, downloads আৰু data import/export।

Material additions-ত Material 3 color roles, পাঁচ বা তাতকৈ কম main tabs-ৰ বাবে bottom navigation, dynamic/manual Theme colors, About screen-ত fork attribution আৰু release signing support অন্তৰ্ভুক্ত।

---

## Installation

NewPipe Material-ক এই repository-ৰ GitHub releases বা signed artifacts-ৰ পৰা install কৰক যেতিয়া উপলব্ধ।

```text
Official NewPipe: org.schabi.newpipe / net.newpipe.app upstream build অনুসৰি
NewPipe Material: org.wisso.newpipematerial
Debug:            org.wisso.newpipematerial.debug
```

Data migrate কৰিবলৈ official NewPipe-ৰ Settings > Backup and Restore-ৰ পৰা database export কৰক, NewPipe Material install কৰক আৰু backup import কৰক। সদায় backup ৰাখক।

NewPipe Material, NewPipe বা NewPipe forks Google Play-ত publish নকৰিব।

---

## Source-ৰ পৰা build কৰক

Requirements: JDK 21, Android SDK আৰু repository-ৰ Gradle wrapper।

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
./gradlew assembleDebug -DskipFormatKtlint
```

Debug build-এ **NewPipe Material Debug** নাম আৰু `org.wisso.newpipematerial.debug` package ব্যৱহাৰ কৰে।

---

## Release signing

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

---

## Development status

Completed বা in progress: app name আৰু ID, debug/release identity separation, Material 3 colors, dynamic/manual colors, bottom navigation, About screen, dialogs, snackbars, settings, video detail, download UI আৰু signing workflow।

Deferred বা high-risk: main player overlay, seekbar/gesture colors, queue controls, quality/audio/caption menus আৰু broad playback/download behavior changes।

---

## Contributing

Contributions welcome: bug fixes, QA, documentation, release readiness আৰু focused Material 3 polish। Changes focused আৰু testable ৰাখক।

---

## Upstream NewPipe

- NewPipe repository: https://github.com/TeamNewPipe/NewPipe
- NewPipe website: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Fork-specific issues এই repository-ত belong কৰে। Service বা Extractor issues official NewPipe-ৰ সৈতে compare কৰিব লাগিব পাৰে।

---

## Donate

Upstream NewPipe support কৰিবলৈ: https://newpipe.net/donate

NewPipe Material independent fork; upstream donations upstream NewPipe project-লৈ যায়, এই fork-লৈ automatically নহয়।

---

## License

NewPipe Material NewPipe-ৰ ওপৰত ভিত্তি কৰা free software আৰু GNU General Public License version 3 বা পাছৰ version-ৰ অধীনত distributed। সম্পূৰ্ণ details-ৰ বাবে repository license files আৰু in-app license screen চাওক।
