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

*Olvassa el ezt a dokumentumot más nyelveken: [Deutsch](doc/README.de.md),
[English](README.md), [Español](doc/README.es.md), [Français](doc/README.fr.md),
[हिन्दी](doc/README.hi.md), [Italiano](doc/README.it.md),
[한국어](doc/README.ko.md), [Português Brasil](doc/README.pt_BR.md),
[Polski](doc/README.pl.md), [ਪੰਜਾਬੀ ](doc/README.pa.md),
[日本語](doc/README.ja.md), [Română](doc/README.ro.md),
[Soomaali](doc/README.so.md), [Türkçe](doc/README.tr.md),
[正體中文](doc/README.zh_TW.md), [অসমীয়া](doc/README.asm.md),
[Српски](doc/README.sr.md), [العربية](README.ar.md)*

> [!warning]\
> <b>EZ AZ ALKALMAZÁS BÉTA VERZIÓBAN VAN, EZÉRT ELŐFORDULHATNAK HIBÁK. HA
> ILYENNEL TALÁLKOZIK, NYISSON EGY HIBAJEGYET A GITHUB-TÁROLÓNKBAN AZ
> ISSUE-SABLON KITÖLTÉSÉVEL.</b>
> 
> <b>A NEWPIPE VAGY ANNAK BÁRMELY VÁLTOZATÁNAK KÖZZÉTÉTELE A GOOGLE PLAY
> ÁRUHÁZBAN SÉRTI A GOOGLE FELHASZNÁLÁSI FELTÉTELEIT.</b>
> 
> <b>A „NewPipe” BEJEGYZETT SZÓVÉDJEGY, AMELYRE \[VÉDJEGYHASZNÁLATI
> SZABÁLYZATA\](https://newpipe-ev.de/policy/trademark/) VONATKOZIK. A
> VÉDJEGYJOGOKAT SÉRTŐ ALKALMAZÁSOKAT NEM TŰRJÜK.</b>

## Képernyőképek

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

### Támogatott szolgáltatások

A NewPipe jelenleg a következő szolgáltatásokat támogatja:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube (\[weboldal\]1) és YouTube Music (\[weboldal\]2) (\[wiki\]3)
* PeerTube (\[weboldal\]1) és annak összes példánya (a fogalom megértéséhez
  nyissa meg a weboldalt!) (\[wiki\]2)
* Bandcamp ([weboldal](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([weboldal](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([weboldal](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Amint láthatja, a NewPipe több videó- és zenei szolgáltatást is támogat. Bár
eredetileg YouTube-hoz készült, az évek során mások további szolgáltatásokkal
bővítették, így a NewPipe egyre sokoldalúbbá vált!

Részben a körülményeknek, részben pedig a népszerűségének köszönhetően a YouTube
rendelkezik a legjobb támogatással az említett szolgáltatások közül. Ha
használja valamelyik másik szolgáltatást, vagy ismeri azt, akkor segítsen a
támogatásának fejlesztésében! Jelenleg a SoundCloud és a PeerTube
karbantartásához keresünk közreműködőket.

Ha új szolgáltatást szeretne hozzáadni, akkor először vegye fel velünk a
kapcsolatot! A \[dokumentációnkban\]1 további információkat talál arról, hogyan
lehet új szolgáltatást hozzáadni az alkalmazáshoz és a \[NewPipe
Extractorhoz\]2.

## Leírás

A NewPipe úgy működik, hogy a használt szolgáltatásból lekéri a szükséges
adatokat annak hivatalos API-ján keresztül (például a PeerTube esetében). Ha a
hivatalos API a céljainkra korlátozottan használható (például a YouTube
esetében), vagy nem nyílt, az alkalmazás ehelyett a weboldal tartalmát dolgozza
fel, vagy belső API-t használ. Ez azt jelenti, hogy a NewPipe használatához
egyik szolgáltatásban sem kell felhasználói fiókkal rendelkeznie.

Mivel az alkalmazás és az Extractor is szabad és nyílt forráskódú szoftver,
egyikük sem használ zárt forráskódú könyvtárakat vagy keretrendszereket, például
a Google Play-szolgáltatásokat. Ez azt jelenti, hogy a NewPipe olyan eszközökön
és egyedi ROM-okon is használható, amelyekre nincsenek telepítve a Google
alkalmazásai.

### Funkciók

* Videók megtekintése akár 4K felbontásban is
* Zenehallgatás a háttérben, kizárólag a hangfolyam betöltésével az adatforgalom
  csökkentése érdekében
* Felugró mód (lebegő lejátszó, más néven kép a képben – Picture-in-Picture)
* Élő közvetítések megtekintése
* Feliratok be- és kikapcsolása (beleértve a hallássérülteknek szánt feliratokat
  is)
* Videók és hanganyagok keresése (a YouTube-on a tartalom nyelvét is megadhatja)
* Videók sorba állítása (és igény szerint mentése helyi lejátszási listaként)
* Videók általános adatainak megjelenítése és elrejtése (például a leírás és a
  címkék)
* Következő és kapcsolódó videók megjelenítése és elrejtése
* Hozzászólások megjelenítése és elrejtése
* Videók, hanganyagok, csatornák, lejátszási listák és albumok keresése
* Videók és hanganyagok böngészése egy csatornán belül
* Feliratkozás csatornákra (igen, mindezt bármilyen fiókba való bejelentkezés
  nélkül!)
* Értesítések fogadása azokra az új videókra, amelyeket a feliratkozott
  csatornák tesznek közzé
* Csatornacsoportok létrehozása és szerkesztése (az egyszerűbb böngészés és
  kezelés érdekében)
* Csatornacsoportokból összeállított videóhírfolyamok böngészése
* Megtekintési előzmények megtekintése és keresése
* Lejátszási listák keresése és megtekintése (ezek távoli lejátszási listák,
  vagyis az éppen böngészett szolgáltatásból tölti le őket az alkalmazás)
* Helyi lejátszási listák létrehozása és szerkesztése (ezek az alkalmazáson
  belül hozhatók létre, továbbá mentehetők, valamint semmilyen szolgáltatáshoz
  nem kapcsolódnak)
* Videók, hanganyagok és feliratok (beleértve a hallássérülteknek szánt
  feliratokat is) letöltése
* Megnyitás a Kodiban
* Korhatáros tartalmak megtekintése és letiltása

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Telepítés és frissítések
A NewPipe-ot az alábbi módszerek valamelyikével telepítheti:
 1. Adja hozzá egyéni tárolónkat az F-Droidhoz, majd telepítse onnan az
    alkalmazást. Az útmutató itt található:
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Töltse le az APK-fájlt a \[GitHub kiadások oldaláról\]1, \[ellenőrizze az
    aláírókulcsot\]2, majd telepítse az alkalmazást.
 3. Frissítés az F-Droidon keresztül. Ez a leglassabb frissítési módszer, mivel
    az F-Droidnak először észlelnie kell a változásokat, majd saját magának le
    kell összeállítania az APK-t, alá kell írnia, végül pedig elérhetővé kell
    tennie a frissítést a felhasználók számára.
 4. Állítson össze magának egy hibakeresési (debug) APK-t. Ezzel juthat hozzá a
    leggyorsabban az új funkciókhoz az eszközén, azonban ez jóval bonyolultabb
    eljárás, ezért inkább a többi módszer valamelyikét javasoljuk.
 5. Ha egy adott funkció vagy hibajavítás érdekli, amelyet ebben a tárolóban egy
    Pull Request (PR) tartalmaz, akkor az ahhoz tartozó APK-t közvetlenül a PR
    oldaláról is letöltheti. Az útmutatást a PR leírásában találja. A PR-ekhez
    tartozó APK-k nagy előnye, hogy a hivatalos alkalmazás mellett, különálló
    alkalmazásként telepíthetők. Így nem kell aggódnia amiatt, hogy elvesznek az
    adatai, vagy bármit elront.

A legtöbb felhasználónak az 1. módszert ajánljuk. Az 1. és a 2. módszerrel
telepített APK-k kompatibilisek egymással, vagyis ha az egyikkel telepítette a
NewPipe-ot, a másikkal is frissítheti. A 3. módszerrel telepített változatokkal
azonban nem kompatibilisek. Ennek oka, hogy az 1. és a 2. módszer esetében
ugyanazt az aláírókulcsot használjuk (a sajátunkat), míg a 3. módszernél egy
másik aláírókulcsot (az F-Droidét) alkalmazzák. A 4. módszerrel készített
hibakeresési (debug) APK egyáltalán nem használ aláírókulcsot. Az aláírókulcsok
segítenek megakadályozni, hogy a felhasználókat megtévesszék, és rosszindulatú
frissítést telepítsenek az alkalmazáshoz. Az 5. módszer esetében minden APK-t
egy másik, véletlenszerűen generált, a GitHub Actions által biztosított kulccsal
írnak alá, ezért még az alkalmazás frissítésére sincs lehetőség. Minden
alkalommal, amikor új APK-t szeretne használni, biztonsági mentést kell
készítenie az alkalmazás adatairól, majd az új verzió telepítése után vissza
kell állítania azokat.

Ha időközben valamilyen okból forrást szeretne váltani (például a NewPipe
alapvető működése meghibásodik, és az F-Droidon még nem érhető el a legújabb
frissítés), az alábbi eljárást javasoljuk:
1. Hozzon létre biztonsági mentést az adatairól a Beállítások \&gt; Biztonsági
   mentés és visszaállítás \&gt; Adatbázis exportálása menüpontban, hogy
   megőrizze a megtekintési előzményeit, feliratkozásait és lejátszási listáit
2. NewPipe eltávolítása
3. Töltse le az APK-fájlt az új forrásból, majd telepítse
4. Importálja az 1. lépésben elmentett adatokat a Beállítások \&gt; Biztonsági
   mentés és visszaállítás \&gt; Adatbázis importálása menüpontban

> [!Note] Amikor adatbázist importál a hivatalos alkalmazásba, mindig győződjön
> meg arról, hogy azt korábban a hivatalos alkalmazásból exportálta. Ha nem a
> hivatalos alkalmazásból, hanem egy másik APK-ból exportált adatbázist
> importál, az működési problémákat okozhat. Az ilyen művelet nem támogatott,
> ezért csak akkor végezze el, ha teljesen biztos abban, hogy mit csinál.

### APK-információk

Ez a NewPipe aláírókulcsának SHA-ujjlenyomata, amellyel ellenőrizheti a tőlünk
letöltött és általunk aláírt APK-fájlok hitelességét. Az ujjlenyomat a \[NewPipe
weboldalán\]1 is megtalálható. Ez a 2. módszer esetében releváns.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Közreműködés
Legyen szó új ötletekről, fordításokról, dizájnbeli változtatásokról,
kódtisztításról vagy akár jelentősebb kódmódosításokról, minden segítséget
örömmel fogadunk. Az alkalmazás minden közreműködéssel egyre jobbá válik, legyen
az bármilyen apró vagy jelentős hozzájárulás! Ha részt kíván venni a
fejlesztésben, olvassa el a [közreműködési
útmutatónkat](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Adakozás
Ha tetszik a NewPipe, örömmel fogadjuk az adományát. Elsősorban a Liberapay
platformot ajánljuk, mivel az nyílt forráskódú és nonprofit módon működik. A
NewPipe támogatásával kapcsolatos további információkért látogasson el a
[weboldalunkra](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Adatvédelmi szabályzat

A NewPipe projekt célja, hogy a webalapú médiaszolgáltatások használata során
biztosítsa a magánszférája védelmét és az anonimitást. Ennek megfelelően az
alkalmazás a beleegyezése nélkül nem gyűjt adatokat. A NewPipe adatvédelmi
szabályzata részletesen ismerteti, hogy milyen adatokat küldünk el és tárolunk,
amikor hibajelentést küld, vagy hozzászólást ír a blogunkban. A dokumentumot
[itt](https://newpipe.net/legal/privacy/) találja.

## Licenc
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

A NewPipe szabad szoftver: szabadon használható, tanulmányozható, megosztható és
továbbfejleszthető. Pontosabban: a [GNU General Public
License](https://www.gnu.org/licenses/gpl.html) feltételei szerint terjesztheő
tovább és/vagy módosítható, a Free Software Foundation által közzétett licenc 3.
verziója vagy – saját választása szerint – bármely későbbi verziója alapján.
