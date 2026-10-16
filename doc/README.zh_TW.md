<p align="center"><a href="https://newpipe.net"><img src="assets/new_pipe_icon_5.png" width="150"></a></p> 
<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">A libre lightweight streaming front-end for Android.</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" width=206/></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub NewPipe releases"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg" ></a>
<a href="https://github.com/TeamNewPipe/NewPipe-nightly/releases" alt="GitHub NewPipe nightly releases"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe-nightly.svg?labelColor=purple&label=dev%20nightly"></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="License: GPLv3"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="Build Status"><img src="https://github.com/TeamNewPipe/NewPipe/actions/workflows/ci.yml/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="Translation Status"><img src="https://hosted.weblate.org/widgets/newpipe/-/svg-badge.svg"></a>
</p>

<p align="center">
<a href="https://web.libera.chat/#newpipe" alt="IRC channel: #newpipe"><img src="https://img.shields.io/badge/IRC%20chat-%23newpipe-brightgreen.svg"></a>
<a href="https://matrix.to/#/#newpipe:matrix.newpipe-ev.de" alt="Matrix channel: #newpipe"><img src="https://img.shields.io/badge/Matrix%20chat-%23newpipe-blue"></a>
</p>

<hr>
<p align="center"><a href="#screenshots">Screenshots</a> &bull; <a href="#supported-services">Supported Services</a> &bull; <a href="#description">Description</a> &bull; <a href="#features">Features</a> &bull; <a href="#installation-and-updates">Installation and updates</a> &bull; <a href="#contribution">Contribution</a> &bull; <a href="#donate">Donate</a> &bull; <a href="#license">License</a></p>
<p align="center"><a href="https://newpipe.net">Website</a> &bull; <a href="https://newpipe.net/blog/">Blog</a> &bull; <a href="https://newpipe.net/FAQ/">FAQ</a> &bull; <a href="https://newpipe.net/press/">Press</a></p>
<hr>

*Read this document in other languages: [Deutsch](doc/README.de.md),
[English](README.md), [Español](doc/README.es.md), [Français](doc/README.fr.md),
[हिन्दी](doc/README.hi.md), [Italiano](doc/README.it.md),
[한국어](doc/README.ko.md), [Português Brasil](doc/README.pt_BR.md),
[Polski](doc/README.pl.md), [ਪੰਜਾਬੀ ](doc/README.pa.md),
[日本語](doc/README.ja.md), [Română](doc/README.ro.md),
[Soomaali](doc/README.so.md), [Türkçe](doc/README.tr.md),
[正體中文](doc/README.zh_TW.md), [অসমীয়া](doc/README.asm.md),
[うちなーぐち](doc/README.sr.md), [Српски](README.ar.md) , [العربية]{19}*

> [!warning]\
> <b>這是測試版本，可能會發生錯誤。如果遇到錯誤，請在我們的 GITHUB REPO 開 ISSUE 回報。</b>
> 
> <b>將 NEWPIPE 或其任何分支上傳至 GOOGLE PLAY 商店違反了他們的使用者合約。</b>
> 
> <b>「NewPipe」為已註冊的文字商標，受[其商標政策](https://newpipe-ev.de/policy/trademark/)
> 規範。絕不容忍任何侵權行為。</b>

## 截圖

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/00.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/00.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/01.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/02.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/03.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/04.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/05.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/06.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/07.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/08.png)
<br/><br/>\
[<img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width=405>](fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png)
[<img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width=405>](fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png)

### 支援的裝置

NewPipe 目前支援這些裝置：

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube（[網站](https://www.youtube.com/)）與 YouTube
  Music（[網站](https://music.youtube.com/)）（[wiki](https://en.wikipedia.org/wiki/YouTube)）
* PeerTube（[網站](https://joinpeertube.org/))與其所有的站台（請開啟網站以了解其含義！）（[wiki](https://en.wikipedia.org/wiki/PeerTube)）
* Bandcamp（[網站](https://bandcamp.com/)）（[wiki](https://en.wikipedia.org/wiki/Bandcamp)）
* SoundCloud（[網站](https://soundcloud.com/)）（[wiki](https://en.wikipedia.org/wiki/SoundCloud)）
* media.ccc.de（[網站](https://media.ccc.de/)）（[wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club)）

如您所見，NewPipe 支援多種影音服務。雖然它最初僅支援 YouTube，但這些年來，其他使用者陸續新增了更多服務，讓 NewPipe 變得越來越多功能！

部分是因為客觀因素，部分則是因其廣受歡迎，YouTube
是這些服務中支援最完善的。如果您使用或熟悉其他任何這類服務，請協助我們改善對這些服務的支援！我們正在尋找 SoundCloud 與 PeerTube 的維護者。

若您打算新增服務，請先聯絡我們！我們的[文件](https://teamnewpipe.github.io/documentation/)提供了更多關於如何將新服務新增至應用程式及
[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) 的相關資訊。

## 說明

NewPipe 不使用任何 Google 架構的函式庫或 Youtube API。因為只解析網站來取得必要資訊，此軟體可以在沒有安裝 Google
服務的裝置上使用。此外，使用 NewPipe 不需要 YouTube 帳號。NewPipe 是個 copyleft 自由軟體。

此外，由於它們是自由且開放原始碼的軟體，無論是應用程式還是 Extractor，均未使用任何專有函式庫或框架，例如 Google Play
Services。這意味著您可以在未安裝 Google 應用程式的裝置或自訂 ROM 上使用 NewPipe。

### 功能

* 搜索影片
* 無須登入
* 顯示影片的基本資訊
* 播放 Youtube 影片
* 收聽 Youtube 影片
* 彈出模式（懸浮模式）
* 選擇串流播放器來播放影片
* 下載影片
* 只下載音訊
* 在 Kodi 開啟影片
* 顯示上/下一部影片
* 搜尋特定語言的影片
* 播放/屏蔽有年齡限的制內容
* 顯示頻道資訊
* 搜索頻道
* 觀看頻道影片
* 支援 Orbot/Tor （目前未直接實裝）
* 支援 1080p/2K/4K
* 觀看歷史
* 訂閱頻道
* 搜尋歷史
* 搜索/播放播放清單

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## 安裝與更新
你可以用以下的任何方法安裝 NewPipe：
 1. 將我們的軟體庫加至 F-Droid
    再從那邊安裝。詳細的說明在此：https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. 從 [Github Releases](https://github.com/TeamNewPipe/NewPipe/releases) 下載 APK
    再安裝。
 3. 自 F-Droid 更新。這是取得更新最慢的方式，因為 F-Droid 要偵測到更新、建置 APK 、簽署後才會將更新推送給使用者。
 4. 自己建置 APK。這是取得更新最快的方法，但因為這也比較複雜，所以我們推薦使用其他方法。
 5. 如果您對此儲存庫中某個拉取請求所提供的特定功能或錯誤修正感興趣，也可以直接從該 PR 內下載其 APK 檔案。請閱讀 PR 的說明以了解操作步驟。PR
    專屬 APK 的優點在於，它們會與官方應用程式並行安裝，因此您無需擔心資料遺失或造成任何問題。

對一般的使用者我們推薦方法一。使用方法一或二安裝的 APK 互相相容，但都不相容於方法三。因為前兩者的簽章使用相同的（我們的）金鑰，與後者（使用 F-Droid
的金鑰）的不同。使用方法四建置除錯 APK 完全避免了金鑰的問題。簽章金鑰能幫助使用者避免安裝惡意的更新。

若你想更換安裝來源（如果 NewPipe 的核心機能壞掉而 F-Droid 又還沒有最新的更新），我們推薦以下的步驟：
1. 移除 NewPipe
2. 解除安裝 NewPipe
3. 從新來源下載 APK 並安裝
4. 透過「設定」→「備份與還原」→「匯入資料庫」，匯入步驟 1 的資料

> [!注意] 當您將資料庫匯入官方應用程式時，請務必確認該資料庫是您從官方應用程式匯出的。若匯入的是從官方應用程式以外的 APK
> 匯出的資料庫，可能會導致系統運作異常。此類操作不受支援，您應僅在完全確定自己清楚操作內容的情況下才進行此操作。

### APK 資訊

這是 NewPipe 簽署金鑰的 SHA 指紋，用於驗證由我們簽署的已下載 APK 檔案。此指紋亦可於 [NewPipe
官網](https://newpipe.net#download)查閱。此資訊與方法 2 相關。
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## 貢獻
若你有任何想法、翻譯、設計、整理原始碼或大範圍的原始碼改動，我們歡迎任何幫助。

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## 捐款
若你喜歡 NewPipe 我們歡迎捐款。你可以使用 bitcoin ，也能在 Bountysource 或 Liberapay 上捐款。
更多關於捐款資訊，請見我們的[網站](https://newpipe.net/donate)。

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## 隱私權政策

NewPipe 專案旨在提供私人與匿名的網路媒體使用體驗。 因此，此軟體不在沒有你的同意下收集任何資料。NewPipe
的隱私權政策說明了送出錯誤報告與在我們的部落格上留言時何種資料會被傳輸或儲存。你可以在[這裡](https://newpipe.net/legal/privacy/)找到此文件。

## 授權條款
[![GNU GPLv3
影像](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe 是自由軟體：可以任意使用、研究、分享或更改。在自由軟體基金會發布的[ GPL 通用公眾授權條款]（第三或更新的版本）下可以重新散佈與/或修改。
