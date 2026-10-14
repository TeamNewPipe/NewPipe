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
> <b>DIESE APP BEFINDET SICH IN EINER BETA-PHASE, DAHER KÖNNTEST DU BUGS
> BEGEGNEN. FALLS DIES PASSIERT, ERSTELLE EIN ISSUE (AUF ENGLISCH) IN UNSEREM
> GITHUB REPOSITORY, INDEM DU DIE VORLAGE DORT AUSFÜLLST.</b>
> 
> <b>NEWPIPE, ODER JEGLICHEN FORK DAVON, IM GOOGLE PLAYSTORE ANZUBIETEN,
> VERLETZT DESSEN GESCHÄFTSBEDINGUNGEN.</b>
> 
> <b>„NewPipe“ IST EINE EINGETRAGENE WORTMARKE UND UNTERLIEGT EINER
> [MARKENRICHTLINIE](https://newpipe-ev.de/policy/trademark/). DAGEGEN
> VERSTOẞENDE APPS WERDEN NICHT TOLERIERT.</b>

## Screenshots

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

### Unterstützte Dienste

NewPipe unterstützt derzeit folgende Dienste:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([website](https://www.youtube.com/)) und YouTube Music
  ([website](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([Website](https://joinpeertube.org/)) und alle seine Instanzen
  (schau dir die Website an, um zu erfahren, was das bedeutet!)
  ([Wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([website](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([website](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([website](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Wie du siehst, unterstützt NewPipe zahlreiche Video- und Audiodienste. Obwohl es
ursprünglich nur mit YouTube begann, haben andere Benutzer im Laufe der Jahre
weitere Dienste hinzugefügt, wodurch NewPipe immer vielseitiger geworden ist!

Teils aufgrund der Umstände, teils aufgrund seiner Beliebtheit wird YouTube von
diesen Diensten am besten unterstützt. Wenn du einen dieser anderen Dienste
nutzt oder damit vertraut bist, hilf uns bitte dabei, die Unterstützung dafür zu
verbessern! Wir suchen Maintainer für SoundCloud und PeerTube.

Wenn du einen neuen Dienst hinzufügen möchtest, setze dich bitte zunächst mit
uns in Verbindung! In unserer
[Dokumentation](https://teamnewpipe.github.io/documentation/) findest du weitere
Informationen dazu, wie ein neuer Dienst zur App und zum [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) hinzugefügt werden
kann.

## Beschreibung

NewPipe ruft die benötigten Daten über die offizielle API (z. B. PeerTube) des
von dir genutzten Dienstes ab. Sollte die offizielle API für unsere Zwecke
eingeschränkt (z. B. YouTube) oder proprietär sein, analysiert die App
stattdessen die Website oder nutzt eine interne API. Das bedeutet, dass du kein
Konto bei einem Dienst benötigst, um NewPipe zu nutzen.

Da es sich zudem um freie und quelloffene Software handelt, verwenden weder die
App noch der Extractor proprietäre Bibliotheken oder Frameworks wie
beispielsweise Google Play Services. Das bedeutet, dass du NewPipe auf Geräten
oder mit benutzerdefinierten ROMs nutzen kannst, auf denen keine Google-Apps
installiert sind.

### Funktionen

* Videos in Auflösungen bis zu 4K ansehen
* Audio im Hintergrund abspielen, wobei nur der Audiostream geladen wird, um
  Daten zu sparen
* Pop-up-Modus (schwebender Player, aka Bild-in-Bild)
* Livestreams anschauen
* Untertitel/Closed Caption anzeigen/ausblenden
* Videos und Audioinhalte suchen (auf YouTube kannst du auch die Sprache des
  Inhalts angeben)
* Videos in Wiedergabe einreihen (und optional als lokale Wiedergabelisten
  speichern)
* Allgemeine Informationen zu Videos anzeigen/ausblenden (z. B. Beschreibung und
  Schlagwörter)
* Nächste/verwandte Videos anzeigen/ausblenden
* Kommentare anzeigen/ausblenden
* Nach Videos, Audioinhalte, Kanäle, Wiedergabelisten und Alben suchen
* Videos und Audioinhalte innerhalb eines Kanals durchsuchen
* Kanäle abonnieren (ja, ganz ohne sich irgendwo anzumelden!)
* Benachrichtigungen über neue Videos von Kanälen erhalten, die du abonniert
  hast
* Kanalgruppen erstellen und bearbeiten (zum einfacheren Durchstöbern und
  Verwalten)
* Aus deinen Kanalgruppen automatisch erzeugte Videofeeds durchsuchen
* Deinen Wiedergabeverlauf anzeigen und durchsuchen
* Wiedergabelisten suchen und ansehen (es handelt sich hierbei um
  Remote-Wiedergabelisten, d. h., sie werden von dem Dienst abgerufen, den du
  gerade nutzt)
* Lokale Wiedergabelisten erstellen und bearbeiten (diese werden innerhalb der
  App erstellt und gespeichert, und sind von keinem Dienst abhängig)
* Videos/Audioinhalte/Untertitel (Closed Captions) herunterladen
* In Kodi öffnen
* Inhalte mit Altersbeschränkung ansehen/sperren

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Beschreibung
Du kannst NewPipe auf eine der folgenden Arten installieren:
 1. Füge unser benutzerdefiniertes Repo zu F-Droid hinzu und installiere von
    dort aus. Die Anleitung findest du hier:
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Lade die APK-Datei von [GitHub
    Releases](https://github.com/TeamNewPipe/NewPipe/releases) herunter,
    [vergleiche den Signaturschlüssel](#apk-info) und installiere sie.
 3. Über F-Droid aktualisieren. Dies ist die langsamste Methode, um
    Aktualisierungen zu erhalten, da F-Droid die Änderungen erkennen, die APK
    selbst erstellen, sie signieren und das Update anschließend an die Benutzer
    ausliefern muss.
 4. Erstelle selbst eine Debug-APK. Dies ist der schnellste Weg, um neue
    Funktionen auf deinem Gerät zu erhalten, jedoch ist dies wesentlich
    komplizierter. Wir empfehlen daher, eine der anderen Methoden zu verwenden.
 5. Wenn du an einer bestimmten Funktion oder Fehlerbehebung interessiert bist,
    die in einem Pull Request in diesem Repository enthalten ist, kannst du die
    entsprechende APK auch direkt aus dem PR herunterladen. Eine Anleitung dazu
    findest du in der Beschreibung des PRs. Das Tolle an PR-spezifischen APKs
    ist, dass sie parallel zur offiziellen App installiert werden, sodass du dir
    keine Sorgen machen musst, dass deine Daten verlorengehen oder etwas
    durcheinandergerät.

Wir empfehlen den meisten Benutzern Methode 1. Mit Methode 1 oder 2 installierte
APKs sind untereinander kompatibel (das heißt, wenn du NewPipe entweder mit
Methode 1 oder 2 installiert hast, kannst du NewPipe auch mit der jeweils
anderen Methode aktualisieren), jedoch nicht mit denen, die mit Methode 3
installiert wurden. Dies liegt daran, dass für die Methoden 1 und 2 derselbe
Signaturschlüssel (unserer) verwendet wird, für Methode 3 jedoch ein anderer
Signaturschlüssel (der von F-Droid). Beim Erstellen einer Debug-APK mit Methode
4 entfällt ein Schlüssel vollständig. Signaturschlüssel tragen dazu bei,
sicherzustellen, dass ein Benutzer nicht dazu verleitet wird, ein bösartiges
Update für eine App zu installieren. Beim Verwenden von Methode 5 wird jede APK
mit einem anderen zufälligen Schlüssel signiert, der von GitHub Actions
bereitgestellt wird, sodass eine Aktualisierung nicht möglich ist. Du musst die
App-Daten jedes Mal sichern und wiederherstellen, wenn du eine neue APK
verwenden möchtest.

Solltest du in der Zwischenzeit aus irgendeinem Grund die Quelle wechseln wollen
(z. B. weil eine wichtige Funktion von NewPipe fehlerhaft ist und F-Droid die
neueste Aktualisierung noch nicht bereitstellt), empfehlen wir dir, wie folgt
vorzugehen:
1. Sichere deine Daten über Einstellungen > Sichern und Wiederherstellen >
   Datenbank exportieren, damit dein Verlauf, deine Abonnements und
   Wiedergabelisten erhalten bleiben
2. Deinstalliere NewPipe
3. Lade die APK-Datei von der neuen Quelle herunter und installiere sie
4. Importiere die Daten aus Schritt 1 über Einstellungen > Sichern und
   Wiederherstellen > Datenbank importieren

> [!Note] Wenn du eine Datenbank in die offizielle App importierst, stelle stets
> sicher, dass es sich um die Datenbank handelt, die du _aus_ der offiziellen
> App exportiert hast. Wenn du eine Datenbank importierst, die aus einer anderen
> APK als der offiziellen App exportiert wurde, kann dies zu Fehlfunktionen
> führen. Ein solcher Vorgang wird nicht unterstützt, und du solltest ihn nur
> durchführen, wenn du dir absolut sicher bist, was du tust.

### APK-Info

Dies ist der SHA-Fingerabdruck von NewPipes Signaturschlüssel zum Überprüfen der
heruntergeladenen und von uns signierten APKs. Der Fingerabdruck ist auch auf
[NewPipes Website](https://newpipe.net#download) verfügbar. Dies ist für Methode
2 relevant.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Beteiligung
Egal ob du neue Ideen, Übersetzungen, Designvorschläge, kleine
Code-Bereinigungen, oder sogar große Code-Verbesserungen hast, jegliche
Unterstützung ist immer gern gesehen. Die App wird mit jedem Beitrag besser,
egal wie groß oder klein er ist! Wenn du dich einbringen willst, sieh dir die
[Beitragshinweise](.github/CONTRIBUTING.md) an.

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Spenden
Wenn dir NewPipe gefällt, kannst du uns gerne durch eine Spende unterstützen.
Wir bevorzugen Liberapay, da es sowohl quelloffen als auch nicht
gewinnorientiert ist. Für weitere Informationen über Spenden an NewPipe, besuche
unsere [Website](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Datenschutzerklärung

Das NewPipe-Projekt zielt darauf ab, eine private und anonyme Nutzung
webbasierter Mediendienste zu ermöglichen. Daher sammelt die App keinerlei Daten
ohne deine Zustimmung. NewPipe Datenschutzerklärung erläutert ausführlich,
welche Daten übermittelt und gespeichert werden, wenn du uns einen
Absturzbericht sendest oder einen Kommentar in unserem Blog hinterlässt. Das
Dokument findest du [hier](https://newpipe.net/legal/privacy/).

## Lizenz
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe ist Freie Software: Du kannst sie nach Belieben nutzen, untersuchen,
weitergeben und verbessern. Insbesondere darfst du sie gemäß den Bedingungen der
[GNU General Public License](https://www.gnu.org/licenses/gpl.html), wie sie von
der Free Software Foundation veröffentlicht wurde, weitergeben und/oder
verändern, und zwar entweder gemäß Version 3 der Lizenz oder (nach deiner Wahl)
gemäß einer späteren Version.
