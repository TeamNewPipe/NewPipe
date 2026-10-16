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

*ふかぬぎんぐっしゆむん： [English](doc/README.de.md), [Español](README.md),
[हिन्दी](doc/README.es.md), [한국어](doc/README.fr.md),
[Soomaali](doc/README.hi.md), [Português Brasil](doc/README.it.md),
[Polski](doc/README.ko.md), [日本語](doc/README.pt_BR.md),
[Română](doc/README.pl.md), [Türkçe](doc/README.pa.md),
[正體中文](doc/README.ja.md), [うちなーぐち](doc/README.ro.md), [Српски](doc/README.so.md)
, [العربية](doc/README.tr.md)*

> [!warning]\
> <b>くれーベータばんぬたみ、バグぬはっせいすがのうゆいがあいびーん。むしバグぬはっせいさーあい、GitHubぬリポジトリっしIssueふぃらちくぃみそーれー。</b>
> 
> <b>NewPipeうゆびいじりぬフォークGoogle
> Playストアんかいかんかいしーねー、Googleぬとぅいふぃちじょうきんぬいはんになります。</b>
> 
> <b>"NewPipe" IS A REGISTERED WORD MARK SUBJECT TO [ITS TRADEMARK
> POLICY](https://newpipe-ev.de/policy/trademark/). INFRINGING APPLICATIONS WILL
> NOT BE TOLERATED.</b>

## スクリーンショット

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

### Supported Services

NewPipe currently supports these services:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([website](https://www.youtube.com/)) and YouTube Music
  ([website](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([website](https://joinpeertube.org/)) and all its instances (open
  the website to know what that means!)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([website](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([website](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([website](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

As you can see, NewPipe supports multiple video and audio services. Though it
started off with YouTube, other people have added more services over the years,
making NewPipe more and more versatile!

Partially due to circumstance, and partially due to its popularity, YouTube is
the best supported out of these services. If you use or are familiar with any of
these other services, please help us improve support for them! We're looking for
maintainers for SoundCloud and PeerTube.

If you intend to add a new service, please get in touch with us first! Our
[docs](https://teamnewpipe.github.io/documentation/) provide more information on
how a new service can be added to the app and to the [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## しちめい

じゆうなコピーレフトソフトウェアぬNewPipeーいっさいぬGoogleフレームワークライブラリうゆび、YouTube
APIさしましん。ウェブサイトーふぃちぐとーるじょうほうぬたみてーぬんかいゆみくまりーるたみ、くぬアプレーGoogleぬサービスぬインストールさりてぃうぅらんたんまちっししようぬなやびーん。また、NewPipeぬしようんかいYouTubeアカウントーふぃちようとぅなやびらん。

Also, since they are free and open source software, neither the app nor the
Extractor use any proprietary libraries or frameworks, such as Google Play
Services. This means you can use NewPipe on devices or custom ROMs that do not
have Google apps installed.

### ちぬー

* ちゃーしがぬきんさく
* ちゃーしがどぅちふんじょうほうぬひょうじ
* YouTubeぬちゃーしがぬしちょう
* YouTubeぬちゃーしがぬバックグラウンドさいせい
* ポップアップモード（フローティングプレイヤー）
* ちゃーしがしちょうするストリーミングプレイヤーぬしんたく
* ちゃーしがぬダウンロード
* うんせいぬみぬダウンロード
* Kodiっしぬちゃーしがさいせい
* ちぎぬちゃーしが/かんりんちゃーしがぬひょうじ
* とぅくていぬぎんぐぬYouTubeぬきんさく
* されいゆいぎんぬあるコンテンツぬしちょう/ブロック
* チャンネルぬきふんじょうほうぬひょうじ
* チャンネルぬきんさく
* チャンネルからぬちゃーしがぬしちょう
* Orbot/Torたいおう（ちょくしちてぃきなむぬはみじっそう）
* 1080p/2K/4Kたいおう
* りりきぬひょうじ
* チャンネルぬとぅうるく
* りりきぬきんさく
* さいせいリストぬきんさく/しちょう
* さいせいリストキューんかいちいからちさいせい

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## インストールとぅこうしん
いかぬほうほうぬいじりがんかいしたがいるくとぅにゆってぃNewPipeインストールなやびーん。
 1. カスタムリポジトリF-Droidんかいちいからちリリースぬかんかいされいしだいインストールすん。くぬほうほうぬしちめえーくちら：
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. リリースぬかんかいされいしだい[GitHubぬリリース](https://github.com/TeamNewPipe/NewPipe/releases)からAPKダウンロードしインストールすん。
 3. F-Droidからこうしんすん。くれーこうしんてぃなするういっしむっとぅむにーさるほうほうやいびーん。F-Droidがへいるかんきんちし、APKビルドしー、しょめい、あんしユーザーんかいこうしんとぅどぅきーるふぃちようがあるたみやいびーん。
 4. じぶんっしデバッグAPKビルドすん。くれーみーさるちぬーしようするういっしむっとぅむふぇーさるほうほうやいびーしが、ふかんでぃくらびてぃいっぺーふちゅるざちやくとぅ、ふかぬほうほうぬしようすいしょうさびーん。
 5. りりきやとぅいるるいちゅるチャンネル、さいせいリストたむちゅんでぃ しってい > コンテンツ > データベースエクスポートからデータバックアップ

わったーやふとぅんどぅぬユーザーんかいほうほう1すいしょうさびーん。ほうほう1んでぃ2っしインストールさったん
APKーぐかんせいがあいびーしが、ほうほう3っしインストールさったるむぬんかえーあいびらん。くれーほうほう1んでぃ2っしぇー、いぬしょめいかぎ（わったーがしようすし）がささりやびーしが、ほうほう3っしぇーくとぅなたしょめいかぎ（F-Droidがしようすし）がささりーるたみやいびーん。ほうほう4ちかたるデバッグAPKぬビルドーくでぃぷんてぃきなしょめいかぎぬむんだいをぬずきます。しーょめいかぎはユーザーぬだまさってぃあくいぬあるこうしんぬアプリんかいインストールさりらんくとぅたしきーんでぃあいびーん。

むし、ぬーやいびーがやーぬいゆーいんにゆりソースきりけーたるいばあい（れい：
NewPipeぬコアちぬーがやんでぃてぃねーんしがF-Droidーなーらこうしんしうぅらん）ー、くぬてぃじゅんをすいしょうさびーん。
1. NewPipeアンインストール
2. みーさるソースからAPKダウンロードしインストール
3. しってい > コンテンツ > データベースインポートからステップ1っしちゅくたるデータベースインポート
4. Import the data from step 1 via Settings > Backup and Restore > Import
   Database

> [!Note] When you're importing a database into the official app, always make
> sure that it is the one you exported _from_ the official app. If you import a
> database exported from an APK other than the official app, it may break
> things. Such an action is unsupported, and you should only do so when you're
> absolutely certain you know what you're doing.

### APK Info

This is the SHA fingerprint of NewPipe's signing key to verify downloaded APKs
which are signed by us. The fingerprint is also available on [NewPipe's
website](https://newpipe.net#download). This is relevant for method 2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## こうきん
ふんやちゅん、デザインぬへんかん、コードぬせいり、だいきぶやーコードぬへんかんんでーぬたしきーやいちやてぃんかんぎうぅいさびーん。
ゆりじょーとぅーまじゅんちゅくいとぅらさびら！

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## ちーふ
むし、NewPipeちーがんじいただけいねー、ちーふしいただけいねーうっさいびーん。BitcoinあらんでぃBountysource,Liberapayからちーふするくとぅがなやびーん。NewPipeんかいどぅちふにちいてー、[ウェブサイト](https://newpipe.net/donate)からうにげーさびら。

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## プライバシーポリシー

NewPipeプロジェクトーメディアウェブサービスしようするういっしぬプライベートっしとぅくめいぬたいきんてぃいちょうするくとぅをむくてぃきとぅそーいびーん。
うぬたみ、アプリーうんじゅがちゃーいなしっしいっさいぬデータしゅうしゅうさびらん。NewPipeぬプライバシーポリシーやうんじゅがクラッシュレポートあらんでぃ、わったーがブログっしコメントあんしんさーあいんかいちゃぬぐとーるデータぬあんしんされい、ふずんさりーるぬがしーょうさいにしちめいそーいびーん。うぬドキュメントー[くちら](https://newpipe.net/legal/privacy/)からみーちきーるくとぅがなやびーん。

## ライセンス
[![GNU
GPLv3ぬロゴ](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipeーフリーソフトウェアやくとぅ、うんじょーうんじゅがぬじゅむるぐとぅさ、しゅうとぅく、きょうゆう、かいじんうくねーやびーん。
ぐたちちーがー、フリーソフトウェアじぇーやんんにゆりかんかいさったん [GNU General Public
License](https://www.gnu.org/licenses/gpl.html)
ぬバージョン3ぬライセンスあらんでぃ、（うんじゅがしんたくでぃ）いじりがぬこうけいバージョンぬきやくぬむとぅっしはいふあらんでぃかいんかいんうくないるくとぅがなやびーん。
