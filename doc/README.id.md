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

*Baca dokumen ini dalam bahasa lain: [Deutsch](doc/README.de.md),
[English](README.md), [Español](doc/README.es.md), [Français](doc/README.fr.md),
[हिन्दी](doc/README.hi.md), [Italiano](doc/README.it.md),
[한국어](doc/README.ko.md), [Português Brasil](doc/README.pt_BR.md),
[Polski](doc/README.pl.md), [ਪੰਜਾਬੀ ](doc/README.pa.md),
[日本語](doc/README.ja.md), [Română](doc/README.ro.md),
[Soomaali](doc/README.so.md), [Türkçe](doc/README.tr.md),
[正體中文](doc/README.zh_TW.md), [অসমীয়া](doc/README.asm.md),
[Српски](doc/README.sr.md), [العربية](README.ar.md)*

> [!warning]\
> <b>APLIKASI INI MASIH DALAM FASE BETA, SEHINGGA ANDA MUNGKIN MENEMUI BUG. JIKA
> HAL ITU TERJADI, SILAHKAN BUAT LAPORAN MASALAH DI REPOSITORI GITHUB KAMI
> DENGAN MENGISI TEMPLATE LAPORAN MASALAH.</b>
> 
> <b>MENEMPATKAN NEWPIPE, ATAU FORK APAPUN, KE GOOGLE PLAY STORE MELANGGAR
> SYARAT DAN KETENTUANNYA.</b>
> 
> <b>“NewPipe” ADALAH MEREK KATA YANG TERDAFTAR DAN TUNDUK PADA [KEBIJAKAN MEREK
> DAGANGNYA](https://newpipe-ev.de/policy/trademark/). PENGGUNAAN YANG MELANGGAR
> TIDAK AKAN DITOLERANSI.</b>

## Tangkapan Layar

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

### Layanan yang Didukung

NewPipe saat ini mendukung layanan ini:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([situs web](https://www.youtube.com/)) dan YouTube Music ([situs
  web](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* ([Situs web](https://joinpeertube.org/)) PeerTube dan semua instansinya (buka
  situs web untuk mengetahui apa maksudnya!)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([situs web](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([situs web](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([situs web](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Seperti yang bisa Anda lihat, NewPipe mendukung berbagai layanan video dan
audio. Meskipun awalnya hanya dimulai dengan YouTube, orang lain telah
menambahkan lebih banyak layanan selama bertahun-tahun, membuat NewPipe menjadi
semakin serbaguna!

Sebagian karena keadaan, dan sebagian karena popularitasnya, YouTube adalah
layanan yang paling didukung di antara layanan-layanan ini. Jika Anda
menggunakan atau kenal dengan salah satu layanan lainnya ini, tolong bantu kami
meningkatkan dukungan untuk layanan-layanan tersebut! Kami sedang mencari
pengelola untuk SoundCloud dan PeerTube.

Jika Anda berencana menambahkan layanan baru, silahkan hubungi kami terlebih
dahulu! [Dokumen](https://teamnewpipe.github.io/documentation/) kami menyediakan
informasi lebih lanjut tentang bagaimana layanan baru dapat ditambahkan ke
aplikasi dan ke [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Deskripsi

NewPipe bekerja dengan mengambil data yang diperlukan dari API resmi (misalnya
PeerTube) dari layanan yang Anda gunakan. Jika API resminya dibatasi (misalnya
YouTube) untuk tujuan kami, atau merupakan hak milik, aplikasi akan mengurai
situs web atau menggunakan API internal sebagai gantinya. Artinya Anda tidak
memerlukan akun di layanan manapun untuk menggunakan NewPipe.

Selain itu, karena mereka merupakan perangkat lunak gratis dan bersumber
terbuka, baik aplikasi maupun Extractor tidak menggunakan pustaka atau kerangka
kerja berpemilik apapun, seperti Layanan Google Play. Ini berarti Anda dapat
menggunakan NewPipe pada perangkat atau ROM kustom yang tidak menginstal
aplikasi Google.

### Fitur

* Tonton video dengan resolusi hingga 4K
* Dengarkan audio di latar belakang, dengan hanya memuat aliran audio untuk
  menghemat kuota data
* Mode popup (pemutar mengambang, alias Picture-in-Picture)
* Tonton siaran langsung
* Tampilkan/sembunyikan subtitel/teks tertutup
* Telusuri video dan audio (di YouTube, Anda juga bisa menentukan bahasa
  kontennya)
* Masukkan video ke antrean (dan secara opsional menyimpannya sebagai playlist
  lokal)
* Tampilkan/sembunyikan informasi umum tentang video (seperti deskripsi dan tag)
* Tampilkan/sembunyikan video berikutnya/terkait
* Tampilkan/sembunyikan komentar
* Telusuri video, audio, saluran, daftar putar, dan album
* Jelajahi video dan audio dalam saluran
* Berlangganan saluran (ya, tanpa perlu masuk ke akun apapun!)
* Dapatkan notifikasi tentang video baru dari saluran langganan Anda
* Buat dan edit grup saluran (agar lebih mudah dijelajahi dan dikelola)
* Jelajahi umpan video yang dihasilkan dari grup saluran Anda
* Lihat dan telusuri riwayat tontonan Anda
* Telusuri dan tonton daftar putar (ini adalah daftar putar jarak jauh, yang
  berarti diambil dari layanan yang sedang Anda jelajahi)
* Buat dan edit daftar putar lokal (ini dibuat dan disimpan dalam aplikasi, dan
  tidak ada hubungannya dengan layanan apapun)
* Unduh video/audio/subtitel (teks tertutup)
* Buka di Kodi
* Tonton/Blokir materi yang dibatasi usia

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Instalasi dan pembaruan
Anda dapat menginstal NewPipe menggunakan salah satu metode berikut:
 1. Tambahkan repo khusus kami ke F-Droid dan instal dari sana. Petunjuknya ada
    di sini: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Unduh APK dari [GitHub
    Releases](https://github.com/TeamNewPipe/NewPipe/releases), [bandingkan
    kunci penandatanganannya](#apk-info) dan instal.
 3. Perbarui melalui F-Droid. Ini adalah metode paling lambat untuk mendapatkan
    pembaruan, karena F-Droid harus mengenali perubahan, membuat APK sendiri,
    menandatanganinya, dan kemudian mengirimkan pembaruan kepada pengguna.
 4. Buat APK debug sendiri. Ini adalah cara tercepat untuk mendapatkan fitur
    baru di perangkat Anda, tetapi prosesnya jauh lebih rumit, jadi kami
    menyarankan Anda untuk menggunakan salah satu metode lainnya.
 5. Jika Anda tertarik dengan fitur tertentu atau perbaikan bug yang disediakan
    dalam Pull Request di repo ini, Anda juga dapat mengunduh APK-nya dari dalam
    Pull Request. Baca deskripsi Pull Request untuk mendapatkan petunjuk. Hal
    hebat tentang APK khusus Pull Request adalah bahwa APK tersebut diinstal
    secara berdampingan dengan aplikasi resmi, jadi Anda tidak perlu khawatir
    kehilangan data atau mengacaukan apapun.

Kami merekomendasikan metode 1 untuk sebagian besar pengguna. APK yang diinstal
menggunakan metode 1 atau 2 kompatibel satu sama lain (artinya jika Anda
menginstal NewPipe menggunakan metode 1 atau 2, Anda juga dapat memperbarui
NewPipe menggunakan metode lainnya), namun tidak dengan yang diinstal
menggunakan metode 3. Hal ini disebabkan oleh kunci penandatanganan yang sama
(milik kami) yang digunakan untuk 1 dan 2, namun kunci penandatanganan yang
berbeda (F-Droid) digunakan untuk 3. Membuat APK debug menggunakan metode 4
mengecualikan kunci sepenuhnya. Kunci penandatanganan membantu memastikan bahwa
pengguna tidak tertipu untuk menginstal pembaruan berbahaya pada suatu aplikasi.
Saat menggunakan metode 5, setiap APK ditandatangani dengan kunci acak berbeda
yang disediakan oleh GitHub Actions, sehingga Anda bahkan tidak dapat
memperbaruinya. Anda harus mencadangkan dan memulihkan data aplikasi setiap kali
Anda ingin menggunakan APK baru.

Sementara itu, jika Anda ingin beralih sumber karena suatu alasan (misalnya
fungsi inti NewPipe rusak dan F-Droid belum memiliki pembaruan terkini), kami
sarankan untuk mengikuti prosedur ini:
1. Cadangkan data Anda melalui Pengaturan > Cadangkan dan Pulihkan > Ekspor
   Basis Data agar riwayat, langganan, dan daftar putar Anda tetap tersimpan
2. Hapus Instalasi NewPipe
3. Unduh APK dari sumber baru dan instal
4. Impor data dari langkah 1 melalui Pengaturan > Cadangkan dan Pulihkan > Impor
   Basis Data

> [!Note] Saat Anda mengimpor basis data ke dalam aplikasi resmi, selalu
> pastikan bahwa basis data tersebut adalah yang Anda ekspor _dari_ aplikasi
> resmi. Jika Anda mengimpor basis data yang diekspor dari APK selain aplikasi
> resmi, hal itu dapat menyebabkan masalah. Tindakan semacam itu tidak didukung,
> dan Anda hanya boleh melakukannya jika Anda benar-benar yakin tau apa yang
> sedang Anda lakukan.

### Info APK

Ini adalah sidik jari SHA dari kunci penandatanganan NewPipe untuk memverifikasi
APK unduhan yang ditandatangani oleh kami. Sidik jari juga tersedia di [situs
web NewPipe](https://newpipe.net#download). Hal ini relevan untuk metode 2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Kontribusi
Baik Anda memiliki ide, terjemahan, perubahan desain, pembersihan kode, atau
bahkan perubahan kode besar-besaran, bantuan selalu diterima. Aplikasi ini
menjadi lebih baik dan lebih baik lagi berkat setiap kontribusi, tidak peduli
seberapa besar atau kecilnya! Jika Anda ingin terlibat, periksa [catatan
kontribusi](.github/CONTRIBUTING.md) kami.

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Donasi
Jika Anda menyukai NewPipe, Anda dipersilahkan untuk mengirimkan donasi. Kami
lebih memilih Liberapay, karena bersifat open-source dan nirlaba. Untuk info
lebih lanjut mengenai cara berdonasi ke NewPipe, silahkan kunjungi [situs
web](https://newpipe.net/donate) kami.

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Kebijakan Privasi

Proyek NewPipe bertujuan untuk memberikan pengalaman yang privat dan anonim
dalam menggunakan layanan media berbasis web. Oleh karena itu, aplikasi ini
tidak mengumpulkan data apapun tanpa persetujuan Anda. Kebijakan privasi NewPipe
menjelaskan secara rinci data apa saja yang dikirim dan disimpan saat Anda
mengirimkan laporan kerusakan, atau meninggalkan komentar di blog kami. Anda
dapat menemukan dokumennya [di sini](https://newpipe.net/legal/privacy/).

## Lisensi
[![Gambar GNU
GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe adalah Perangkat Lunak Gratis: Anda dapat menggunakan, mempelajari,
berbagi, dan memperbaikinya sesuka hati. Secara khusus, Anda dapat
mendistribusikan ulang dan/atau memodifikasinya berdasarkan ketentuan [Lisensi
Publik Umum GNU](https://www.gnu.org/licenses/gpl.html) sebagaimana diterbitkan
oleh Free Software Foundation, baik versi 3 dari Lisensi tersebut, atau (sesuai
pilihan Anda) versi yang lebih baru.
