<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/newpip_material_logo.png" width="150" alt="NewPipe Material 아이콘"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Android용 NewPipe의 Material 3 중심 독립 포크입니다.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="Build status"></a>
</p>

<p align="center"><b>다른 언어:</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## 중요한 포크 안내

NewPipe Material은 Material 3 디자인, 앱 테마, 제품 완성도에 집중하는 NewPipe의 독립 유지 포크입니다.

이 프로젝트는 공식 NewPipe 프로젝트, TeamNewPipe 또는 NewPipe e.V.와 **제휴, 후원 또는 승인 관계가 아닙니다**.

NewPipe Material은 NewPipe를 기반으로 하며 NewPipe의 자유 소프트웨어 라이선스, 업스트림 크레딧, 서드파티 라이선스 고지를 유지합니다.

---

## NewPipe Material이란?

NewPipe Material은 NewPipe의 핵심 경험을 유지하면서 앱 정체성과 사용자 인터페이스를 현대화합니다.

목표:

- Material 3에서 영감을 받은 화면, 대화상자, 설정, 탭, 내비게이션
- 사용 가능한 경우 Material You 동적 색상 지원
- App default, Neutral, Green, Blue, Purple, Orange, Pink, Red 수동 테마 색상
- 새 앱 정체성: **NewPipe Material**
- 별도 application ID: `org.wisso.newpipematerial`
- debug build는 `org.wisso.newpipematerial.debug`로 별도 설치
- NewPipe 동작, 가져오기/내보내기 호환성, 지원 서비스를 유지

재생, 다운로드, 백그라운드 재생, 팝업 재생, Extractor 로직 같은 민감한 영역은 전용 테스트 변경이 아닌 한 위험한 동작 변경을 피합니다.

---

## 스크린샷

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

## 지원 서비스

NewPipe Material은 NewPipe의 YouTube, YouTube Music, PeerTube, Bandcamp, SoundCloud, media.ccc.de 지원을 계승합니다.

---

## 기능

NewPipe Material은 동영상과 라이브 스트림, 백그라운드 재생, 팝업 플레이어, 로컬 재생목록, 플랫폼 계정 없는 구독, 채널 그룹, 검색, 동영상 세부정보, 다운로드, 데이터 가져오기/내보내기 등 NewPipe의 익숙한 기능을 유지합니다.

Material 추가 사항에는 Material 3 색상 역할, 5개 이하 메인 탭의 하단 내비게이션, 동적/수동 테마 색상, About 화면의 포크 표기, release signing 지원이 포함됩니다.

---

## 설치

이 저장소의 GitHub Releases 또는 서명된 artifact가 제공될 때 설치하세요.

```text
Official NewPipe: org.schabi.newpipe / net.newpipe.app upstream build에 따라 다름
NewPipe Material: org.wisso.newpipematerial
Debug:            org.wisso.newpipematerial.debug
```

데이터 이전은 공식 NewPipe의 Settings > Backup and Restore에서 database를 export한 뒤 NewPipe Material을 설치하고 backup을 import하세요. 항상 백업을 보관하세요.

NewPipe Material, NewPipe 또는 NewPipe 포크를 Google Play에 게시하지 마세요.

---

## 소스에서 빌드

Requirements: JDK 21, Android SDK, repository Gradle wrapper.

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
./gradlew assembleDebug -DskipFormatKtlint
```

Debug build는 **NewPipe Material Debug** 이름과 `org.wisso.newpipematerial.debug` package를 사용합니다.

---

## Release signing

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

---

## 개발 상태

완료 또는 진행 중: 앱 이름과 ID, debug/release identity 분리, Material 3 colors, dynamic/manual colors, bottom navigation, About screen, dialogs, snackbars, settings, video detail, download UI, signing workflow.

보류 또는 고위험: main player overlay, seekbar/gesture colors, queue controls, quality/audio/caption menus, 광범위한 playback/download behavior changes.

---

## 기여

버그 수정, QA, 문서, release readiness, 집중된 Material 3 polish 기여를 환영합니다. 변경은 작고 테스트 가능하게 유지하세요.

---

## Upstream NewPipe

- NewPipe repository: https://github.com/TeamNewPipe/NewPipe
- NewPipe website: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

포크 관련 문제는 이 저장소에 보고하세요. 서비스 또는 Extractor 문제는 공식 NewPipe와 비교가 필요할 수 있습니다.

---

## Donate

Upstream NewPipe 지원: https://newpipe.net/donate

NewPipe Material은 독립 포크입니다. upstream 기부는 upstream NewPipe project로 가며 이 포크에 자동으로 전달되지 않습니다.

---

## License

NewPipe Material은 NewPipe 기반 free software이며 GNU General Public License version 3 이상으로 배포됩니다. 자세한 내용은 repository license files와 앱 내 license screen을 확인하세요.
