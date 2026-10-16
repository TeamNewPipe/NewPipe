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
> <b>これはベータ版のため、バグが発生する可能性があります。もしバグが発生した場合、GitHub のリポジトリで Issue を開いてください。</b>
> 
> <b>NewPipe 及びいずれのフォークを Google Play ストアに公開すると、Google の取引条件の違反になります。</b>
> 
> <b>「NewPipe」は、[商標ポリシー](https://newpipe-ev.de/policy/trademark/)の対象となる登録商標です。商標権を侵害するアプリケーションは容認されません。</b>

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

### 対応サービス

現在、NewPipeは以下のサービスに対応しています:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([ウェブサイト](https://www.youtube.com/)) と YouTube Music
  ([ウェブサイト](https://music.youtube.com/))
  ([Wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([ウェブサイト](https://joinpeertube.org/))
  とそのすべてのインスタンス（意味についてはウェブサイトをご覧ください！）([Wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([ウェブサイト](https://bandcamp.com/))
  ([Wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([ウェブサイト](https://soundcloud.com/))
  ([Wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([ウェブサイト](https://media.ccc.de/))
  ([Wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

このように、NewPipeは複数の動画・音声サービスに対応しています。当初はYouTubeへの対応から始まりましたが、その後さまざまな人が長年にわたってサービスを追加し、NewPipeはますます多用途になっています！

事情による部分もありますが、その人気もあって、これらのサービスの中ではYouTubeへの対応が最も充実しています。ほかのサービスを利用している方や詳しい方は、対応の改善にぜひご協力ください！現在、SoundCloudとPeerTubeのメンテナーを募集しています。

新しいサービスを追加する場合は、まず私たちにご連絡ください！新しいサービスをアプリと[NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)に追加する方法については、[ドキュメント](https://teamnewpipe.github.io/documentation/)をご覧ください。

## 説明

自由なコピーレフトソフトウェアの NewPipe は一切のGoogleフレームワークライブラリ及び、YouTube API
を使用しません。ウェブサイトは必要な情報のためだけに読み込まれるため、このアプリはGoogleのサービスがインストールされていない端末でも使用できます。また、NewPipeの使用にYouTubeアカウントは必要ありません。

また、アプリとExtractorはいずれも自由でオープンソースのソフトウェアであるため、Google
Play開発者サービスなどのプロプライエタリなライブラリやフレームワークを一切使用していません。そのため、Googleアプリがインストールされていない端末やカスタムROMでもNewPipeを使用できます。

### 機能

* 動画の検索
* 動画の基本情報の表示
* YouTube の動画の視聴
* YouTube の動画のバックグラウンド再生
* ポップアップモード (フローティングプレイヤー)
* 動画を視聴するストリーミングプレイヤーの選択
* 動画のダウンロード
* 音声のみのダウンロード
* Kodiでの動画再生
* 次の動画/関連動画の表示
* 特定の言語の YouTube の検索
* 年齢制限のあるコンテンツの視聴/ブロック
* チャンネルの基本情報の表示
* チャンネルの検索
* チャンネルからの動画の視聴
* Orbot/Tor 対応 (直接的なものは未実装)
* 1080p/2K/4K 対応
* 履歴の表示
* チャンネルの登録
* 履歴の検索
* 再生リストの検索/視聴
* 再生リストをキューに追加して再生

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## インストールと更新
以下の方法のいずれかに従うことによって NewPipe をインストールできます。
 1. カスタムリポジトリを F-Droid に追加してリリースが公開され次第インストールする。この方法の説明はこちら：
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. リリースが公開され次第[GitHub のリリース](https://github.com/TeamNewPipe/NewPipe/releases)から
    APK をダウンロードしてインストールする。
 3. F-Droid から更新する。これは更新を手にする上で最も遅い方法です。F-Droid が変更を検知して、APK
    をビルドし、署名、そしてユーザーに更新を届ける必要があるためです。
 4. 自分でデバッグ APK をビルドする。これは新しい機能を使用する上で最も早い方法ですが、他と比べてとても複雑なので、他の方法の使用を推奨します。
 5. このリポジトリのプルリクエストで提供されている特定の機能やバグ修正に関心がある場合は、そのPR内からAPKをダウンロードすることもできます。手順についてはPRの説明をお読みください。PR専用APKの優れた点は、公式アプリと並行してインストールされるため、データを失ったり、何かを壊したりする心配がないことです。

私たちはほとんどのユーザーに方法1を推奨します。方法1と2でインストールされた APK
は互換性がありますが、方法3でインストールされたものにはありません。これは方法1と2では、同じ署名鍵
(私たちが使用するもの)が使用されますが、方法3では異なった署名鍵 (F-Droidが使用するもの)が使用されるためです。方法4を使ったデバッグ APK
のビルドは根本的に署名鍵の問題を除きます。署名鍵はユーザーが騙されて悪意のある更新がアプリにインストールされないことを助けるためにあります。

もし、何かしらの理由によりソースを切り替えたい場合 (例: NewPipe のコア機能が壊れてしまったが F-Droid はまだ更新をしていない)
は、この手順を推奨します。
1. NewPipe をアンインストール
2. NewPipeをアンインストール
3. 新しい配布元からAPKをダウンロードしてインストールします
4. 手順1のデータを、設定 > バックアップと復元 > データベースをインポートからインポートします

> [!Note] 公式アプリにデータベースをインポートする場合は、必ず公式アプリ _から_
> エクスポートしたデータベースを使用してください。公式アプリ以外のAPKからエクスポートしたデータベースをインポートすると、問題が発生する可能性があります。この操作はサポート対象外です。何をしているのかを十分に理解していると確信できる場合にのみ行ってください。

### APK情報

これは、NewPipeが署名したダウンロード済みAPKを検証するための、NewPipeの署名鍵のSHAフィンガープリントです。このフィンガープリントは[NewPipeのウェブサイト](https://newpipe.net#download)でも確認できます。これは方法2に関係します。
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## 貢献
アイデア、翻訳、デザインの変更、コードの整理、さらには大規模なコード変更まで、どのような協力もいつでも歓迎します。貢献の大小にかかわらず、参加するたびにアプリはより良くなっていきます！参加したい方は、[貢献に関する注意事項](.github/CONTRIBUTING.md)をご確認ください。

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## 寄付
もし、NewPipe を気に入っていただけたら、寄付をしていただけると嬉しいです。Bitcoin または Bountysource, Liberapay
から寄付をすることができます。NewPipe への寄付については、[ウェブサイト](https://newpipe.net/donate)からお願いします。

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## プライバシーポリシー

NewPipe プロジェクトはメディアウェブサービスを使用する上でのプライベートで匿名の体験を提供することを目的としています。
そのため、アプリはあなたの同意なしで一切のデータを収集しません。NewPipe
のプライバシーポリシーはあなたがクラッシュレポートまたは、私たちのブログでコメントを送信した場合にどのようなデータが送信され、保存されるのかを詳細に説明しています。そのドキュメントは[こちら](https://newpipe.net/legal/privacy/)から見つけることができます。

## ライセンス
[![GNU GPLv3
のロゴ](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe はフリーソフトウェアなので、あなたはあなたの望むように使用、習得、共有、改善を行えます。 具体的には、フリーソフトウェア財団により公開された
[GNU General Public License](https://www.gnu.org/licenses/gpl.html)
のバージョン3のライセンスもしくは、(あなたの選択で) いずれかの後継バージョンの規約の元で配布または改変を行うことができます。
