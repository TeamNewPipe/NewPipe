<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/newpip_material_logo.png" width="150" alt="NewPipe Material आइकन"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Android के लिए Material 3 पर केंद्रित NewPipe का स्वतंत्र fork।</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="लाइसेंस: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="Build स्थिति"></a>
</p>

<p align="center"><b>इसे पढ़ें:</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## fork के बारे में महत्वपूर्ण सूचना

NewPipe Material, NewPipe का स्वतंत्र रूप से maintained fork है, जिसका ध्यान Material 3 design, app theming और product polish पर है।

यह project official NewPipe project, TeamNewPipe या NewPipe e.V. से **संबद्ध, प्रायोजित या endorsed नहीं है**।

NewPipe Material, NewPipe पर आधारित है और NewPipe की libre software license, upstream credits और third-party license notices को सुरक्षित रखता है।

---

## NewPipe Material क्या है?

NewPipe Material, NewPipe के मुख्य अनुभव को बनाए रखते हुए app identity और user interface को modern बनाता है।

इस fork के लक्ष्य:

- Material 3 से प्रेरित surfaces, dialogs, settings, tabs और navigation
- उपलब्ध होने पर Material You dynamic colors
- manual Theme color presets: App default, Neutral, Green, Blue, Purple, Orange, Pink और Red
- नया app identity: **NewPipe Material**
- अलग application ID: `org.wisso.newpipematerial`
- debug builds अलग से `org.wisso.newpipematerial.debug` के रूप में install होते हैं
- NewPipe behavior, import/export compatibility और supported services को सुरक्षित रखना

यह fork playback, downloads, background playback, popup playback और Extractor logic जैसे sensitive areas में risky behavior changes से बचता है, जब तक कि उन्हें dedicated और tested changes के रूप में handle न किया जाए।

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

NewPipe Material, NewPipe से YouTube, YouTube Music, PeerTube, Bandcamp, SoundCloud और media.ccc.de support inherit करता है।

Service support upstream NewPipe और NewPipe Extractor code पर निर्भर करता है।

---

## Features

NewPipe Material familiar NewPipe features रखता है: videos और live streams, background playback, popup player, local playlists, platform account के बिना subscriptions, channel groups, search, video details, downloads और data import/export।

Material additions में Material 3 color roles, पांच या उससे कम main tabs के लिए bottom navigation, dynamic/manual Theme colors, About screen में fork attribution और release signing support शामिल हैं।

---

## Installation

NewPipe Material को इस repository की GitHub releases या signed artifacts से install करें जब वे उपलब्ध हों।

```text
Official NewPipe: org.schabi.newpipe / net.newpipe.app upstream build पर निर्भर
NewPipe Material: org.wisso.newpipematerial
Debug:            org.wisso.newpipematerial.debug
```

Data migrate करने के लिए official NewPipe से Settings > Backup and Restore में database export करें, NewPipe Material install करें और backup import करें। हमेशा backup रखें।

NewPipe Material, NewPipe या NewPipe forks को Google Play पर publish न करें।

---

## Source से build करें

Requirements: JDK 21, Android SDK और repository का Gradle wrapper।

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
./gradlew assembleDebug -DskipFormatKtlint
```

Debug build **NewPipe Material Debug** नाम और `org.wisso.newpipematerial.debug` package use करता है।

---

## Release signing

Environment variables:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

Recommended verification:

```bash
./gradlew assembleRelease -DskipFormatKtlint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## Development status

Completed या in progress: app name और ID, debug/release identity separation, Material 3 colors, dynamic/manual colors, bottom navigation, About screen, dialogs, snackbars, settings, video detail, download UI और signing workflow।

Deferred या high-risk: main player overlay, seekbar/gesture colors, queue controls, quality/audio/caption menus और broad playback/download behavior changes।

---

## Contributing

Contributions welcome हैं: bug fixes, QA, documentation, release readiness और focused Material 3 polish। Changes focused और testable रखें।

---

## Upstream NewPipe

- NewPipe repository: https://github.com/TeamNewPipe/NewPipe
- NewPipe website: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Fork-specific issues इस repository में belong करते हैं। Service या Extractor issues को official NewPipe से compare करना पड़ सकता है।

---

## Donate

Upstream NewPipe को support करने के लिए: https://newpipe.net/donate

NewPipe Material independent fork है; upstream donations upstream NewPipe project को जाते हैं, अपने आप इस fork को नहीं।

---

## License

NewPipe Material, NewPipe पर आधारित free software है और GNU General Public License version 3 या बाद की version के तहत distributed है। पूरी details के लिए repository license files और in-app license screen देखें।
