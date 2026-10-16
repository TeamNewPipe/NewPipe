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
> <b>MIDKAN, NOOCA APP-KA EE HADDA WALI TIJAABO AYUU KU JIRAA, SIDAA DARTEED
> CILLADO AYAAD LA KULMI KARTAA. HADAAD LA KULANTO, KA FUR ARIN SHARAXAYA
> QAYBTANADA ARRIMAHA EE GITHUB-KA.</b>
> 
> <b>NEWPIPE AMA KUWA KU SALAYSAN IN PLAYSTORE-KA LA GALIYO WAXAY KA HOR
> IMANAYSAA SHARCIGA IYO SHURUUDAHA AY LEEYIHIIN.</b>
> 
> <b>"NewPipe" IS A REGISTERED WORD MARK SUBJECT TO [ITS TRADEMARK
> POLICY](https://newpipe-ev.de/policy/trademark/). INFRINGING APPLICATIONS WILL
> NOT BE TOLERATED.</b>

## Sawir-shaashadeed

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

## Faahfaahin

NewPipe ma isticmalo nidaamka wada shaqaynta Google, ama API-ga YouTube. Kaliya
website-yada ayaa la furaa si xogta loo baahanyahay loogala soo dhex baxo,
App-kan waxaa lagu isticmaali karaa aaladaha aysa ku jirin Adeegyada Google.
Sidoo kale, uma baahnid akoon YouTube ah si aad u isticmaasho NewPipe, kaasoo ah
barnaamij bilaash ah.

Also, since they are free and open source software, neither the app nor the
Extractor use any proprietary libraries or frameworks, such as Google Play
Services. This means you can use NewPipe on devices or custom ROMs that do not
have Google apps installed.

### Waxqabadka

* Raadi muuqaalo
* Soo bandhiga faahfaahin guud oo muuqaalada ku saabsan
* Ku daawo muuqaalada YouTube
* Dhagayso muuqaalada YouTube
* Qaab daaqad ah (muuqaal daare yar oo application-nada dul fuula)
* Dooro muuqaal daareha aad rabto inaad wax ku daawato
* Daji muuqaalada
* Daji dhagaysiga kaliya (cod)
* Ku fur muuqaal Kodi
* Tus muuqaalada ka xiga/kuwa lamidka ah
* Inaad luuqada aad rabto wax kaga dhex raadiso YouTube
* Daawo/xanib muuqaalada da'da ku xidhan
* Soo bandhig xog guud oo ku saabsan kanaalada
* Raadi kanaalo
* Daawo muuqaalada kanaal
* Taageerida Orbot/Tor (wali toos ma aha)
* Taageerida muuqaalada 1080p/2K/4K
* Kaydka wixii hore [aad u daawatay]
* Inaad rukumato kanaalada
* Kaydinta waxaad raadisay
* Raadi/daawo xulalka
* U daawo sidii xulal la horay

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Kushubida iyo cusboonaysiinta
Marka koodhka NewPipe isbadal ku dhaco (wax cusub oo lagusoo kordhiyay ama cilad
bixin), ugu dambayn waxaa lasii daayaa mid cusub (Siidayn). Siidaynta qaabkeedu
waa x.xx.x . Si aad midka cusub u hesho, waxaad samayn kartaa:
 1. Inaad mid cusub (APK) adigu dhisato. Tani waa mida ugu dagdag badan eed
    waxyaabaha cusub ku heli karto, laakiin way adagtahay, sidaa darteed waxaan
    soojeedinaynaa inaad isticmaasho qababka kale.
 2. Ku dar qayb gaar ah xaganaga F-Droid oo xagaas kaga shub isla markay siidayn
    soobaxdo. Hagitaanka xagan ka eeg:
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 3. Kasoo dajiso APK-ga xaga [Siidaynta GitHub]{1} oo ku shubo isla markay
    siidayn soobaxdo.
 4. Ka cusboonaysii xaga F-Droid. Tani waa mida ugu daahitaanka badan, sababtoo
    ah F-Droid waxay fiirin isbadalka waxayna iyadu dhisi mid (app), sixiixi,
    kadibna ay cusboonaysiinta usiidayn isticmaalayaasha.
 5. Xogtaada koobi ka samee adoo raacaya Fadhiga > Luuqada & Fadhiga Kale >
    Gudbi Xog Diyaaran si aysa kaaga bixin kaydka wixii hore, rukunka, iyo
    xulalka

Waxaan usoojeedinaynaa isticmaaalka qaabka 2 dadka badankood. APK-yada loogu
shubo qaabka 2 ama 3 way isqaadan karaan, laakiin isma qaadan karaan kuwa loogu
shubay qaabka 4. Sababtuna waxaa weeye furaha sixiixa oo iskumid ah (kaanaga
weeye) oo loo isticmaalay 2 iyo 3, laakiin furo sixiixeed ka duwan (midka
F-Droid) oo loo isticmaalay 4. Dhisida APK ayadoo la isticmaalayo qaabka 1 waxay
gabi ahaanba ka reebtaa wax fure ah. Furayaasha sixiixa waxay xaqiijiyaan in
isticmaalaha aan lagu khaldin inuu ku shubto cusboonaysiin khalad ah (wax lasoo
dhexraaciyay) app-ka.

Waxaa kale, hadaad rabto inaad tixraacayada kala badasho sabab jirta awgeed
(tusaale shaqaynta aasaasiga ah ee NewPipe ayaa khalkhashay F-Droid-na wali
cusboonysiin ma hayo), waxaan soojeedinaynaa isticmaalka qaabkan:
1. Saar NewPipe
2. Kasoo daji APK-ga tixraaca cusub oo ku shub
3. Kasoo gali xogta talaabada 1 xaga Fadhiga > Luuqada & Fadhiga Kale > Soo Gali
   Xog Kaydsan
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

## Kusoo Kordhin
Hadaad hayso fikrado; rogid, qaab badal, nadiifin koodh, ama koodhka ood si wayn
wax oga badashaa—caawinta marwalba waa lasoo dhawaynayaa. Waxbadan hadii la
qabto waxbadan ayaa fiicnaan!

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Ugu Deeq
Hadaad jeceshahay NewPipe waan ku faraxsanaan lahayn deeq. Waxaad soo diri
kartaa bitcoin ama sidoo kale waxaad deeqda kusoo diri kartaa xaga Bountysource
ama Liberapay. Faahfaahin dheeraad ah oo kusaabsan ugu deeqida NewPipe, fadlan
booqo [website-kanaga](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Siyaasada Sirdhawrka

Mashruuca NewPipe waxay ujeedadiisu tahay inuu bixiyo wax kuu gaar ah, oo adoon
shaqsi ahaan laguu aqoonsan aad isticmaasho website-yada wax laga
daawado/dhagaysto. Sidaa darteed, app-ku wax xog ah ma uruuriyo fasaxaaga
la'aantii. Siyaasada Sirdhawrka NewPipe ayaa si faahfaahsan u sharaxda waxii xog
ah ee la diro markaad cillad wariso, ama aad bogganaga faallo ka dhiibato.
Warqada waxaad ka heli kartaa [halkan](https://newpipe.net/legal/privacy/).

## Laysinka
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe waa barnaamij bilaash ah oon lahayn xuquuqda daabacaada: Waad isticmaali
kartaa, waad wadaagi kartaa waadna hormarin kartaa hadaad rabto. Gaar ahaan waad
sii daabici kartaa ama wax baad ka badali kartaa ayadoo la raacayo shuruudaha
sharciga guud ee [GNU](https://www.gnu.org/licenses/gpl.html) sida ay soosaareen
Ururka Barnaamijyada Bilaashka ah, soosaarista 3aad ee laysinka, ama (hadaad
doonto) nooc walba oo kasii dambeeyay laysinkii 3aad.
