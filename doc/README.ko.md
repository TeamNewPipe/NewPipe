<p align="center"><a href="https://newpipe.net"><img src="../assets/new_pipe_icon_5.png" width="150"></a></p> 
<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">A libre lightweight streaming frontend for Android.</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-ko.svg" alt="Get it on F-Droid" height=80/></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg" ></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="License: GPLv3"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="Build Status"><img src="https://github.com/TeamNewPipe/NewPipe/workflows/CI/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="Translation Status"><img src="https://hosted.weblate.org/widgets/newpipe/-/svg-badge.svg"></a>
<a href="https://web.libera.chat/#newpipe" alt="IRC channel: #newpipe"><img src="https://img.shields.io/badge/IRC%20chat-%23newpipe-brightgreen.svg"></a>
</p>
<hr>
<p align="center"><a href="#screenshots">Screenshots</a> &bull; <a href="#description">Description</a> &bull; <a href="#features">Features</a> &bull; <a href="#updates">Updates</a> &bull; <a href="#contribution">Contribution</a> &bull; <a href="#donate">Donate</a> &bull; <a href="#license">License</a></p>
<p align="center"><a href="https://newpipe.net">Website</a> &bull; <a href="https://newpipe.net/blog/">Blog</a> &bull; <a href="https://newpipe.net/FAQ/">FAQ</a> &bull; <a href="https://newpipe.net/press/">Press</a></p>
<hr>

*Read this document in other languages: [Deutsch](README.de.md), [English](../README.md), [Español](README.es.md), [Français](README.fr.md), [हिन्दी](README.hi.md), [Italiano](README.it.md), [한국어](README.ko.md), [Português Brasil](README.pt_BR.md), [Polski](README.pl.md), [ਪੰਜਾਬੀ ](README.pa.md), [日本語](README.ja.md), [Română](README.ro.md), [Soomaali](README.so.md), [Türkçe](README.tr.md), [正體中文](README.zh_TW.md), [অসমীয়া](README.asm.md), [うちなーぐち](README.ryu.md), [Српски](README.sr.md) , [العربية](README.ar.md)*

> [!warning]
> <b>이 버전은 베타 버전이므로, 버그가 발생할 수도 있습니다. 만약 버그가 발생하였다면, 우리의 GitHub 저장소에서 Issue를 열람하여 주십시오.</b>
>
> <b>NewPipe 또는 NewPipe 포크를 구글 플레이스토어에 올리는 것은 그들의 이용약관을 위반합니다.</b>

## 스크린샷

[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/00.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/00.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png)
<br/><br/>
[<img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width=405>](../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png)
[<img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width=405>](../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png)

### 지원되는 서비스

NewPipe는 현재 이 서비스들을 지원합니다: 

* YouTube ([웹사이트](https://www.youtube.com/)) 와 YouTube Music ([웹사이트](https://music.youtube.com/)) ([위키](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([웹사이트](https://joinpeertube.org/)) 와 모든 인스턴스 (인스턴스가 무엇인지 아려면 웹사이트를 참조하세요.) ([위키](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([웹사이트](https://bandcamp.com/)) ([위키](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([웹사이트](https://soundcloud.com/)) ([위키](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([웹사이트](https://media.ccc.de/)) ([위키](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

NewPipe는 여러 영상·오디오 서비스를 지원합니다. YouTube부터 시작해서, 다른 사람들이 몇 년간 여러 서비스들을 추가해주어 NewPipe의 기능을 풍부하게 해 주었습니다.

현재 상황과, YouTube의 인기로 인해 현재 서비스 중에서 YouTube가 가장 잘 지원됩니다. 다른 서비스를 사용하시거나, 잘 알고 계시다면 지원을 개선할 수 있도록 도와주세요! SoundCloud와 PeerTube의 관리자를 찾고 있습니다.

새로운 서비스를 추가하고 싶으시다면, 먼저 저희에게 연락해 주세요! 저희의 [문서](https://teamnewpipe.github.io/documentation/)가 앱과 [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)에 서비스를 추가하는 법에 대한 정보를 제공합니다.

## 설명

NewPipe는 서비스의 공식 API를 이용하여 정보를 받아오는 방식으로 작동합니다. 공식 API가 저희의 목적을 제한하는 경우 (예: YouTube), 또는 독점적인 경우 NewPipe는 웹사이트의 구문을 분석하거나 내부 API를 사용합니다. 즉, NewPipe를 사용할 때 계정은 필요하지 않습니다.

### 기능

* 최대 4K 화질로 영상 보기
* 백그라운드에서 노래 듣기 (노래 데이터만을 가져오므로 데이터 절약)
* 팝업 모드 (floating player, 또는 Picture-in-Picture)
* 실시간 영상 보기
* 부제/자막 표시/숨기기
* 영상과 오디오 검색하기 (YouTube에서는 콘텐츠 언어를 지정할 수 있습니다)
* 영상 대기열 추가 (로컬 플레이리스트에 추가 가능)
* 영상에 대한 기본 정보 표시/숨기기 (설명이나 태그 등)
* 다음/관련 영상 표시/숨기기
* 댓글 표시/숨기기
* 영상, 오디오, 채널, 플레이리스트, 앨범 검색하기
* 채널 내부에서 영상, 오디오 찾기
* 채널 구독하기 (계정에 로그인 불필요!)
* 구독한 채널의 영상 알림 받기
* 채널 그룹 생성, 수정 (쉬운 탐색과 관리를 위해)
* 채널 그룹에서 생성된 영상 피드 탐색
* 시청 기록 보기, 검색
* 플레이리스트 검색, 시청 (서비스에서 원격으로 받아옴)
* 로컬 플레이리스트 만들기/수정 (다른 서비스에서 할 필요 없이 NewPipe 내부에 저장)
* 영상/오디오/자막 다운로드
* Kodi에서 열기
* 나이 제한 영상 시청/차단

## 설치 및 업데이트
당신은 NewPipe를 설치하기 위해 이 방법 중 하나를 사용할 수 있습니다:
 1. 우리의 커스텀 저장소를 F-Droid에 추가하고 우리가 릴리즈를 게시하는 대로 F-Droid에서 릴리즈를 설치할 수 있습니다. 지침은 여기 있습니다: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. 우리가 릴리즈를 게시하는 대로 [GitHub Releases](https://github.com/TeamNewPipe/NewPipe/releases)에서 APK를 받고 이것을 설치할 수 있습니다.
 3. F-Droid를 통해 업데이트 할 수 있습니다. F-Droid는 변화를 인식하고, 스스로 APK를 생성하고, 이것에 서명하고, 사용자들에서 업데이트를 전달해야만 하기 때문에,
 이것은 업데이트를 받는 가장 느린 방법입니다.
 4. 스스로 디버그 APK 빌드하기. 이것은 새 기능을 기기에 추가하는 가장 빠른 방법이지만, 매우 복잡하므로, 다른 방법 중 하나를 사용하는 것을 권장합니다.
 5. 이 저장소의 PR에 제공된 기능 또는 버그 픽스에 관심이 있다면, PR의 APK를 받을 수 있습니다. 지침을 위해선 PR 설명을 따르십시오. PR APK는 공식 APK와 같이 설치되기 때문에, 데이터를 잃거나 무언가 잘못될 걱정을 하지 않으도 됩니다.

우리는 대부분의 사용자에게 1번째 방법을 추천합니다. 방법 1 또는 2를 사용하여 설치된 APK는 서로 호환되지만 (NewPipe를 방법 1로 설치한 후 방법 2로 업데이트할 수 있음을 의미합니다), 방법 3을 사용하여 설치된 것들과는 호환되지 않습니다. 이것은 방법 1 또는 2에서는 같은 (우리의)서명 키가 사용되지만, 방법 3에서는 다른 (F-Droid의)서명 키가 사용되기 때문입니다. 방법 4를 사용하여 디버그 APK를 생성하는 것에서는 키가 완전히 제외됩니다. 서명 키는 사용자가 앱에 악의적인 업데이트를 설치하는 것에 대해 속지 않도록 보장하는 것을 도와줍니다.

한편, 만약 어떠한 이유(예. NewPipe의 핵심 기능이 손상되었고 F-Droid에 아직 업데이트가 없는 경우) 때문에 소스를 바꾸길 원한다면, 
우리는 다음과 같은 절차를 따르는 것을 권장합니다:
1. 당신의 기록, 구독, 그리고 재생목록을 유지할 수 있도록 Settings > Content > Export Database 를 통해 데이터를 백업하십시오.
2. NewPipe를 삭제하십시오.
3. 새로운 소스에서 APK를 다운로드하고 이것을 설치하십시오.
4. Step 1의 Settings > Content > Import Database 을 통해 데이터를 불러오십시오.

## 기여
당신이 아이디어, 번역, 디자인 변경, 코드 정리, 또는 정말 큰 코드 수정에 대한 의견이 있다면, 도움은 항상 환영합니다.
더 많이 수행될수록 더 많이 발전할 수 있습니다! 

만약 참여하고 싶다면, 우리의 [기여 공지](../.github/CONTRIBUTING.md)를 참고하십시오.

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## 기부
만약 NewPipe가 마음에 들었다면, 우리는 기부에 대해 기꺼이 환영합니다. bitcoin을 보내거나, Bountysource 또는 Liberapay를 통해 기부할 수 있습니다. NewPipe에 기부하는 것에 대한 자세한 정보를 원한다면, 우리의 [웹사이트](https://newpipe.net/donate)를 방문하여 주십시오.

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="../assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="../assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## 개인정보 보호 정책

NewPipe 프로젝트는 미디어 웹 서비스를 사용하는 것에 대한 사적의, 익명의 경험을 제공하는 것을 목표로 하고 있습니다.
그러므로, 앱은 당신의 동의 없이 어떤 데이터도 수집하지 않습니다. NewPipe의 개인정보보호정책은 당신이 충돌 리포트를 보내거나, 또는 우리의 블로그에 글을 남길 때 어떤 데이터가 보내지고 저장되는지에 대해 상세히 설명합니다. 이 문서는 [여기](https://newpipe.net/legal/privacy/)에서 확인할 수 있습니다.

## 라이선스
[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)  

NewPipe는 자유 소프트웨어입니다: 당신의 마음대로 이것을 사용하고, 연구하고, 공유하고, 개선할 수 있습니다. 
구체적으로 당신은 자유 소프트웨어 재단에서 발행되는, 버전 3 또는 (당신의 선택에 따라)이후 버전의,
[GNU General Public License](https://www.gnu.org/licenses/gpl.html) 하에서 이것을 재배포 및/또는 수정할 수 있습니다. 
