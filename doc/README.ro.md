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
> <b>ACEASTA ESTE O VERSIUNE BETA, AŞA CĂ S-AR PUTE SĂ ÎNTÂLNIŢI ERORI. DACĂ SE
> ÎNTÂMPLĂ ACEST LUCRU, DESCHIDEŢI UN ISSUE PRIN REPSITORY-UL NOSTRU GITHUB.</b>
> 
> <b>PUNERA NEWPIPE SAU ORICĂRUI FORK AL ACESTUIA ÎN MAGAZINUL GOOGLE PLAY LE
> ÎNCALCĂ TERMENII ŞI CONDIŢIILE.</b>
> 
> <b>„NewPipe” ESTE O MARCĂ VERBALĂ ÎNREGISTRATĂ, SUBIECTUL [POLITICII SALE
> PRIVIND MĂRCILE COMERCIALE](https://newpipe-ev.de/policy/trademark/).
> UTILIZĂRILE ILICITE NU VOR FI TOLERATE.</b>

## Capturi de ecran

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

### Servicii Acceptate

În prezent, NewPipe acceptă următoarele servicii:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([website](https://www.youtube.com/)) și YouTube Music
  ([website](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([website](https://joinpeertube.org/)) și toate instanțele sale
  (accesează site-ul web pentru a afla ce înseamnă asta!)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([website](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([website](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([website](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

După cum puteți vedea, NewPipe suportă mai multe servicii video și audio. Deși a
început cu YouTube, alți utilizatori au adăugat mai multe servicii de-a lungul
anilor, făcând NewPipe din ce în ce mai versatil!

În parte datorită circumstanțelor, iar în parte datorită popularității sale,
YouTube este cel mai bine susținut dintre aceste servicii. Dacă folosiți sau
sunteți familiarizați cu oricare dintre celelalte servicii, vă rugăm să ne
ajutați să îmbunătățim suportul pentru acestea! Căutăm administratori pentru
SoundCloud și PeerTube.

Dacă intenționați să adăugați un serviciu nou, vă rugăm să ne contactați mai
întâi! Documentația noastră [docs](https://teamnewpipe.github.io/documentation/)
oferă mai multe informații despre modul în care se poate adăuga un serviciu nou
în aplicație și în [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Descriere

NewPipe nu foloseşte nici-o bibliotecă Google framework sau API-ul Youtube.
Website-urile sunt doar analizate pentru a prelua informaţia necesară, aşa că
această aplicaţie poate fi folosită pe telefoane fără Serviciile Google
instalate. De asemenea, nu aveţi nevoie de un cont Youtube pentru a folosi
Newpipe, care este sofware liber şi copylefted.

De asemenea, întrucât sunt programe gratuite și cu sursă deschisă, nici
aplicația, nici Extractor nu utilizează biblioteci sau framework-uri
proprietare, cum ar fi Google Play Services. Acest lucru înseamnă că poți folosi
NewPipe pe dispozitive sau ROM-uri personalizate pe care nu sunt instalate
aplicațiile Google.

### Funcţii

* Căutarea videoclipurilor
* Nu este necesară logarea
* Afişarea informaţiilor generale despre videoclipuri
* Urmărirea videoclipurilor Youtube
* Ascultarea videoclipurilor Youtube
* Modul popup (player plutitor)
* Selectarea playerului de streaming pentru vizionarea videoclipului
* Descărcarea videoclipurilor
* Doar descărcarea sunetului
* Deschiderea videoclipurilor cu Kodi
* Expunerea videoclipurilor următoare/asociate
* Căutarea YouTube într-o limbă specifică
* Vizionarea/Blocarea materialului restricţionat în funcţie de vârstă
* Afişarea informaţiilor generale despre canale
* Căutarea canalelor
* Vizionarea videoclipurilor dintr-un canal
* Suport Orbot/Tor (încă nu direct)
* Suport 1080p/2K/4K
* Vizionarea istoricului
* Abonarea la canale
* Căutarea în istoric
* Căutarea/vizionarea playlisturilor

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Instalare şi actualizări
Puteţi instala NewPipe folosind una dintre următoarele metode:
 1. Adăugaţi depozitul nostru F-droid personalizat. Instrucţiunile sunt aici:
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Descărcaţi APK-ul din [GitHub
    Releases](https://github.com/TeamNewPipe/NewPipe/releases) şi instalaţi-l.
 3. Actualizaţi via F-Droid. Aceasta este cea mai lentă metodă de a obţine
    actualizări, deoarece F-Droid trebuie să recunoască schimbările, să
    constriască APK-ul, să îl semneze, iar apoi să îl trimită utilizatorilor.
 4. Construiţi un APK de depanare. Aceasta este cea mai rapidă metodă de a primi
    funcţii noi, dar este mult mai complicată, aşa că vă recomandăm să folosiţi
    una dintre celelalte metode.
 5. Dacă te interesează o funcție anume sau o remediere a unei erori inclusă
    într-o cerere de preluare (Pull Request) din acest depozit, poți descărca
    fișierul APK corespunzător chiar din cadrul cererii respective. Citește
    descrierea cererii pentru instrucțiuni. Avantajul fișierelor APK specifice
    cererilor de preluare este că acestea se instalează în paralel cu aplicația
    oficială, așa că nu trebuie să-ți faci griji că ți-ai putea pierde datele
    sau că ai putea provoca vreo problemă.

Recomandăm metoda 1 pentru majoritatea utilizatorilor. APK-urile din metodele 1
şi 2 suntcompatibile una cu cealaltă, dar nu cu cele din metoda 3. Acest lucru
se datorează faptului că aceeași cheie de semnare (a noastră) este utilizată
pentru 1 și 2, dar o altă cheie de semnare (F-Droid) este utilizată pentru 3.
Construirea unui APK de depanare folosind metoda 4 exclude o cheie în întregime.
Cheile de semnare vă asigură că un utilizator nu este păcălit să instaleze o
actualizare rău intenționată a unei aplicații.

Între timp, dacă doriți să schimbați sursa dintr-un anumit motiv (de exemplu,
funcționalitatea de bază a NewPipe a fost întreruptă și F-Droid nu are încă
actualizarea), vă recomandăm să folosiţi următoarea procedură:
1. Dezinstalaţi NewPipe
2. Dezinstalați NewPipe
3. Descărcați fișierul APK din noua sursă și instalați-l
4. Importați datele din pasul 1 prin Setări > Copiere de rezervă și restaurare >
   Importare bază de date

> [!Notă] Când importați o bază de date în aplicația oficială, asigurați-vă
> întotdeauna că este cea pe care ați exportat-o _din_ aplicația oficială. Dacă
> importați o bază de date exportată dintr-un fișier APK care nu provine din
> aplicația oficială, este posibil să apară probleme. O astfel de acțiune nu
> este acceptată și ar trebui să o efectuați numai atunci când sunteți absolut
> sigur că știți ce faceți.

### Informații despre APK

Aceasta este amprenta SHA a cheii de semnare a NewPipe, utilizată pentru
verificarea fișierelor APK descărcate care sunt semnate de noi. Amprenta este
disponibilă și pe [NewPipe's website](https://newpipe.net#download).. Acest
lucru este relevant pentru metoda 2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Contribuţie
Fie că aveți idei, traduceri, modificări de design, optimizări ale codului sau
chiar modificări majore ale codului, ajutorul dumneavoastră este întotdeauna
binevenit. Aplicația devine din ce în ce mai bună cu fiecare contribuție,
indiferent cât de mare sau mică ar fi aceasta! Dacă doriți să vă implicați,
consultați [notele noastre privind contribuțiile](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Donaţii
Dacă vă place NewPipe, am fi bucuroşi să primim o donaţie. Puteţi să ne
trimiteţi bitcoin sau să ne donaţi cu Bountysource sau Liberapay. Pentru mai
multe informaţii în legătură cu donaţiile către NewPipe, vă rugăm vizitaţi
[website-ul nostru](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Politica de Confidenţialitate

Proiectul NewPipe îşi propune să furnizeze o experienţă privată şi anonimă
pentru utilizarea serviciilor web media. Prin urmare, aplicaţia nu colectează
niciun fel de informaţii fără acordul dumneavoastră. Politica de
confidențialitate a NewPipe explică în detaliu ce date sunt trimise și stocate
atunci când trimiteți un raport de blocare sau comentați pe blogul nostru.
Puteți găsi documentul [aici](https://newpipe.net/legal/privacy/).

## Licenţă
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe este Software Gratuit: Îl puteţi folosi şi împărtăşi cum doriţi. Mai
exact, îl puteți redistribui și / sau modifica în conformitate cu termenii [GNU
General Public License](https://www.gnu.org/licenses/gpl.html) aşa cum a fost
publicat de Free Software Foundation, fie versiunea 3 a Licenței, fie (la
alegerea dvs.) orice versiune ulterioară.
