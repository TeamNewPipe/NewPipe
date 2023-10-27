<h3 align="center">Wir planen große Teile des Quellcodes <i>neu zu schreiben</i>, um NewPipe <a href="https://github.com/TeamNewPipe/NewPipe/discussions/10118">neu, modern und stabiler</a> zu machen!</h3>
<h4 align="center">Öffne <b>keine</b> neuen Pull Requests für <i>neue Features</i>, es werden nur Fehlerbehebungen akzeptiert.</h4>

<p align="center"><a href="https://newpipe.net"><img src="../assets/new_pipe_icon_5.png" width="150"></a></p> 
<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">Eine freie, offene und leichtgewichtige Streaming App für Android.</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-de.svg" alt="Hole es dir auf F-Droid" height=80/></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub Release"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg" ></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="Lizenz: GPLv3"><img src="https://img.shields.io/badge/Lizenz-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="Status des Builds"><img src="https://github.com/TeamNewPipe/NewPipe/workflows/CI/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="Übersetzt"><img src="https://hosted.weblate.org/widgets/newpipe/de/svg-badge.svg"></a>
<a href="https://web.libera.chat/#newpipe" alt="IRC Chat: #newpipe"><img src="https://img.shields.io/badge/IRC%20Chat-%23newpipe-brightgreen.svg"></a>
</p>
<hr>
<p align="center"><a href="#screenshots">Screenshots</a> &bull; <a href="#unterstützte-dienste">Unterstützte Dienste</a> &bull; <a href="#beschreibung">Beschreibung</a> &bull; <a href="#features">Features</a> &bull; <a href="#installation-und-updates">Installation und Updates</a> &bull; <a href="#beitrag">Beitrag</a> &bull; <a href="#spenden">Spenden</a> &bull; <a href="#lizenz">Lizenz</a></p>
<p align="center"><a href="https://newpipe.net">Website</a> &bull; <a href="https://newpipe.net/blog/">Blog</a> &bull; <a href="https://newpipe.net/FAQ/">FAQ</a> &bull; <a href="https://newpipe.net/press/">Über NewPipe</a></p>
<hr>

*Read this document in other languages: [Deutsch](README.de.md), [English](../README.md), [Español](README.es.md), [Français](README.fr.md), [हिन्दी](README.hi.md), [Italiano](README.it.md), [한국어](README.ko.md), [Português Brasil](README.pt_BR.md), [Polski](README.pl.md), [ਪੰਜਾਬੀ ](README.pa.md), [日本語](README.ja.md), [Română](README.ro.md), [Soomaali](README.so.md), [Türkçe](README.tr.md), [正體中文](README.zh_TW.md), [অসমীয়া](README.asm.md), [うちなーぐち](README.ryu.md), [Српски](README.sr.md)*

<b>WARNUNG: DIESE APP BEFINDET SICH IN EINER BETA-PHASE, DAHER KÖNNTEST DU BUGS BEGEGNEN. FALLS DIES PASSIERT, ERSTELLE EIN ISSUE (AUF ENGLISCH) IN UNSEREM GITHUB REPOSITORY, INDEM DU DIE VORLAGE DORT AUSFÜLLST.</b>

<b>NEWPIPE, ODER JEGLICHEN FORK DAVON, IM GOOGLE PLAYSTORE ANZUBIETEN, VERLETZT DESSEN GESCHÄFTSBEDINGUNGEN.</b>

## Screenshots

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

## Unterstützte Dienste

Aktuell unterstützt NewPipe die folgenden Dienste:

<!-- Um das versehentliche Öffnen einer Webseite zu verhindern, werden diese separat verlinkt. -->
* YouTube ([Webseite](https://www.youtube.com/)) und YouTube Music ([Webseite](https://music.youtube.com/)) ([Wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([Webseite](https://joinpeertube.org/)) sowie all seine *Instanzen* (öffne die Webseite um herauszufinden was das bedeutet!) ([Wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([Webseite](https://bandcamp.com/)) ([Wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([Webseite](https://soundcloud.com/)) ([Wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([Webseite](https://media.ccc.de/)) ([Wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

NewPipe unterstützt offensichtlich mehrere Video- und Audiodienste. Anfänglich war es nur YouTube, über die Jahre haben verschiedene Menschen immer mehr Dienste hinzugefügt - was zu einem zunehmend vielfältigeren NewPipe führte!

Teils den Umständen geschuldet, teils aufgrund der Bekanntheit, ist von allen Diensten YouTube der am besten Unterstützte. Wenn du einen der anderen Dienste nutzt und/oder dich mit ihm gut auskennst, hilf uns bitte, ihn besser zu machen! Wir suchen nach Betreuern für SoundCloud und PeerTube.

Falls du vorhaben solltest, einen neuen Dienst hinzuzügen, kontaktiere uns bitte zuerst! In unserer [Dokumentation](https://teamnewpipe.github.io/documentation/) findest du mehr Informationen, wie man der App und dem [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) einen neuen Dienst hinzufügen kann.

## Beschreibung

NewPipe holt sich die benötigten Daten über die offizielle API des jeweiligen Dienstes (z.B. PeerTube). Falls die offizielle API für unsere Zwecke zu eingeschränkt ist (z.B. YouTube), oder weil sie proprietär ist, wird die Webseite entweder *geparst* oder stattdessen eine interne API benutzt. Das heißt also, dass für keinen Dienst ein Account gebraucht wird, um NewPipe zu benutzen.

Da außerdem sowohl die App als auch der Extractor frei und quelloffen sind, wird keine proprietäre Bibliothek und kein proprietäres Framework benutzt, wie z.B. Google Pay Services.
NewPipe kann also unproblematisch auf Geräten und Custom ROMs benutzt werden, die keine Google Apps installiert haben.

### Features

* Sieh dir Videos mit einer Auflösung von bis zu 4K an
* Höre Audio im Hintergrund, wodurch nur der Audio Stream geladen wird, um Daten zu sparen
* Popup Modus ("schwebender Player", auch bekannt als Picture-in-Picture)
* Schaue Live Streams
* Ein- und Ausblenden von Untertitel
* Suche nach Videos und Musik (bei YouTube kann zusätzlich die Sprache des Inhalts festgelegt werden)
* Warteschlangen für Videos (speichere sie optional als lokale Playlists)
* Ein- und Ausblenden von allgemeinen Information über die Videos (z.B. Beschreibung und Tags)
* Ein- und Ausblenden von nächsten/ähnlichen Videos
* Ein- und Ausblenden der Kommentarsektion
* Suche Videos, Musik, Kanäle, Playlists und Alben
* Durchsuche Videos und Musik innerhalb eines Kanals
* Abonniere Kanäle (ja, ohne sich mit einem Account anzumelden!)
* Bekomme Benachrichtigungen für neue Videos von Kanälen, die du abonniert hast
* Erstelle und bearbeite Gruppen von Kanälen (um das Durchsuchen und Organisieren zu vereinfachen)
* Durchsuche Video Feeds, die aus deinen Kanalgruppen entstehen
* Schaue dir deinen Verlauf an und durchsuche ihn
* Suche und schaue dir Playlists an (Es handelt sich um „Remote Playlists“, die also vom Dienst abgerufen werden, den du gerade durchsuchst)
* Erstelle und bearbeite lokale Playlists (diese werden lediglich in der App gespeichert, also nicht im jeweiligen Dienst!)
* Nutze Videos/Audio/Untertitel offline
* Mit Kodi Medien öffnen
* Schaue oder blockiere altergeschützten Inhalt

## Installation und Updates
NewPipe kann über einer der folgenden Methoden installiert werden:
 1. Füge unser Custom Repo in F-Droid hinzu und installiere es von dort. Die Anleitung dafür findest du hier: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Lade die APK direkt von den [GitHub Releases](https://github.com/TeamNewPipe/NewPipe/releases) herunter und installiere es manuell.
 3. Updates via F-Droid.
 Das ist dia langsamste Methode, Updates zu fahren. Da F-Droid selbst Veränderungen erkennen, die APK erstellen, signieren, und dann das Update den Benutzern bereitstellen muss.
 4. Erstelle selbst die APK.
 Das ist die beste Methode, um neue Features schnellstmöglich zu bekommen, aber auch sehr kompliziert und umständlich.
 Wir raten dir daher zu einer der anderen Methoden.
 5. Falls du an einem konkreten Feature oder Bugfix interessiert bist, welches/r in einem Pull Request in dieser Repo adressiert wird, kannst du auch die APK aus dem PR direkt beziehen.
 Lies dir die PR Beschreibung für entsprechende Anweisungen durch. Das Gute an PR-spezifischen APKs ist, dass sie parallel zu der offiziellen App installiert werden.
 Du musst also nicht fürchten, dass deine Daten verloren gehen oder irgendetwas kaputt geht.

Für die meisten Benutzer empfehlen wir die erste Methode.
APKs, die mit Methode 1 oder 2 installiert wurden, sind untereinander kompatibel (d.h. wenn NewPipe mit Methode 1 installiert wurde, kann es trotzdem mit Methode 2 aktualisiert werden und vice versa) - aber nicht mit Methode 3.
Das liegt daran, dass Methode 1 und 2 den gleichen Signierschlüssel benutzen (unseren), während Methode 3 einen anderen benutzt (den von F-Droid).
Eine mit Methode 4 gebaute Debug APK benutzt überhaupt gar keinen Schlüssel.
Das Signieren mit Schlüssel hilft, zu verhindern, dass Benutzer dazu verleitet werden, bösartige Updates zu installieren.
Bei Methode 5 wird für jede APK ein zufälliger Signierschlüssel von GitHub Actions generiert, d.h. Updates sind unmöglich.
Bei jeder neuen APK Installation muss ein Backup gemacht werden und die Daten wiederhergestellt werden.

Falls du aus welchem Grund auch immer die Quelle (Methode) ändern willst (z.B. weil irgendetwas in NewPipe nicht mehr funktioniert und F-Droid nicht das neuste Update mit dem Fix hat), empfehlen wir folgende Herangehensweise:
1. Mache ein Back-up über _Einstellungen > Inhalt > Datenbank exportieren_, damit dein Verlauf, deine Abos und Playlists nicht verloren gehen
2. Deinstalliere NewPipe
3. Lade die APK von der neuen Quelle herunter und installiere sie
4. Importiere die Daten aus Schritt 1 über _Einstellungen > Inhalt > Datenbank importieren_

<!-- What is an unsupported app anyway? -->
<b>Hinweis: Wenn du eine Datenbank in die offizielle App importierst, stelle sicher, dass sie _von_ der offiziellen App exportiert wurde.
Wenn du eine Datenbank aus einer nicht offiziellen APK importierst, können Dinge kaputt gehen.
So eine Aktion wird nicht unterstützt und du solltest sie nur in Erwägung ziehen, wenn du weißt, was du tust.</b>


## Beitrag
Egal ob du neue Ideen, Übersetzungen, Designvorschläge, kleine Code-Bereinigungen, oder sogar große Code-Verbesserungen hast, jegliche Unterstützung ist immer gern gesehen.
Die App wird mit _jedem_ Beitrag besser und besser - egal wie viel Arbeit in ihn gesteckt wird!
Wenn du dich einbringen willst, sehe dir die [Beitragshinweise](.github/CONTRIBUTING.md) an.

<a href="https://hosted.weblate.org/engage/newpipe/de/">
<img src="https://hosted.weblate.org/widgets/newpipe/de/287x66-grey.png" alt="Übersetzt" />
</a>

## Spenden
Wenn dir NewPipe gefällt, kannst du uns gerne durch eine Spende unterstützen.
Wir bevorzugen Liberapay, da es sowohl quelloffen als auch nicht gewinnorientiert ist.
Für weitere Informationen über Spenden an NewPipe, besuche unsere [Website](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="../assets/liberapay_qr_code.png" alt="Gehe zu NewPipe auf liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="../assets/liberapay_donate_button.svg" alt="Spenden mit Liberapay" height="35px"></a></td>
  </tr>
</table>

## Privacy Policy

Das NewPipe Projekt hat zum Ziel, eine private und anonyme Benutzung von Web-basierten Medien-Dienste zu ermöglichen.
Daher sammelt die App keinerlei Daten ohne deine Zustimmung.
NewPipe's Datenschutzbestimmungen erklären im Detail, welche Daten gesendet und gespeichert werden, wenn du einen _Crash Report_ einreichst, oder ein Kommentar auf unserem Blog hinterlässt.
Du findest das Dokument [hier](https://newpipe.net/legal/privacy/).

## Lizenz
[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.de.html)  

NewPipe ist freie Software: Du kannst es nach Belieben benutzen, studieren, teilen, und verbessern.
Du kannst es insbesondere unter den Bedingungen der [GNU General Public License](https://www.gnu.org/licenses/gpl-3.0.de.html), wie von der Free Software Foundation veröffentlicht, entweder Version 3 der Lizenz oder (nach deiner Wahl) jede spätere Version, weiter verbreiten und/oder verändern.
