<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/new_pipe_icon_5.png" width="150" alt="NewPipe Material アイコン"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Android 向けの Material 3 に重点を置いた、NewPipe の独立フォークです。</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="Build status"></a>
</p>

<p align="center"><b>ほかの言語:</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## フォークに関する重要なお知らせ

NewPipe Material は、Material 3 デザイン、アプリのテーマ、製品としての仕上げに重点を置いた、NewPipe の独立して保守されているフォークです。

このプロジェクトは、公式 NewPipe プロジェクト、TeamNewPipe、NewPipe e.V. と**提携、スポンサー関係、または承認関係にはありません**。

NewPipe Material は NewPipe を基にしており、NewPipe の自由ソフトウェアライセンス、上流プロジェクトのクレジット、第三者ライセンス表示を保持しています。

---

## NewPipe Material とは？

NewPipe Material は、NewPipe の基本的な体験を保ちながら、アプリのアイデンティティと UI を現代化します。

このフォークの目標:

- Material 3 に着想を得たサーフェス、ダイアログ、設定、タブ、ナビゲーション
- 利用可能な環境での Material You 動的カラー対応
- App default、Neutral、Green、Blue、Purple、Orange、Pink、Red などの手動テーマカラー
- 新しいアプリ名: **NewPipe Material**
- 独立した application ID: `org.wisso.newpipematerial`
- debug build は `org.wisso.newpipematerial.debug` として別にインストール
- NewPipe の動作、import/export 互換性、対応サービスを維持

再生、ダウンロード、バックグラウンド再生、ポップアップ再生、Extractor ロジックなどの慎重な領域では、専用にテストされた変更でない限り、危険な挙動変更を避けます。

---

## スクリーンショット

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

## 対応サービス

NewPipe Material は NewPipe から YouTube、YouTube Music、PeerTube、Bandcamp、SoundCloud、media.ccc.de の対応を継承します。

---

## 機能

NewPipe Material は、動画とライブ配信、バックグラウンド再生、ポップアッププレイヤー、ローカルプレイリスト、アカウント不要の登録、チャンネルグループ、検索、動画詳細、ダウンロード、データの import/export など、NewPipe のおなじみの機能を保ちます。

Material 関連の追加点には、Material 3 カラーロール、5 つ以下のメインタブ向けの bottom navigation、動的/手動テーマカラー、About 画面のフォーク表記、release signing 対応があります。

---

## インストール

GitHub Releases または署名済み artifact が利用可能な場合はそこからインストールしてください。

```text
Official NewPipe: org.schabi.newpipe / net.newpipe.app upstream build による
NewPipe Material: org.wisso.newpipematerial
Debug:            org.wisso.newpipematerial.debug
```

データ移行は、公式 NewPipe の Settings > Backup and Restore から database を export し、NewPipe Material をインストールして backup を import してください。必ず backup を保存してください。

NewPipe Material、NewPipe、または NewPipe の fork を Google Play に公開しないでください。

---

## ソースからビルド

必要なもの: JDK 21、Android SDK、この repository の Gradle wrapper。

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
./gradlew assembleDebug -DskipFormatKtlint
```

Debug build は **NewPipe Material Debug** と `org.wisso.newpipematerial.debug` package を使用します。

---

## Release signing

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

---

## 開発状況

完了または進行中: app name と ID、debug/release identity separation、Material 3 colors、dynamic/manual colors、bottom navigation、About screen、dialogs、snackbars、settings、video detail、download UI、signing workflow。

延期または高リスク: main player overlay、seekbar/gesture colors、queue controls、quality/audio/caption menus、広範な playback/download behavior changes。

---

## コントリビュート

バグ修正、QA、ドキュメント、release readiness、Material 3 polish への貢献を歓迎します。変更は焦点を絞り、テスト可能にしてください。

---

## Upstream NewPipe

- NewPipe repository: https://github.com/TeamNewPipe/NewPipe
- NewPipe website: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

フォーク固有の問題はこの repository に報告してください。service や Extractor の問題は公式 NewPipe との比較が必要な場合があります。

---

## Donate

Upstream NewPipe を支援するには: https://newpipe.net/donate

NewPipe Material は独立した fork です。upstream への寄付は upstream NewPipe project に送られ、この fork に自動的に送られるわけではありません。

---

## License

NewPipe Material は NewPipe を基にした free software であり、GNU General Public License version 3 以降で配布されます。詳細は repository の license files とアプリ内の license screen を参照してください。
