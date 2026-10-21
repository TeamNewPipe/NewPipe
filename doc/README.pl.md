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
> <b>TO JEST WERSJA BETA, WIĘC MOŻESZ NAPOTKAĆ BŁĘDY. JEŚLI TAK SIĘ STANIE,
> OTWÓRZ NOWY "ISSUE" NA GITHUBIE.</b>
> 
> <b>PUBLIKOWANIE NEWPIPE LUB JAKIEGOKOLWIEK JEGO FORKU W SKLEPIE PLAY NARUSZA
> ICH WARUNKI UŻYTKOWANIA.</b>
> 
> <b>„NewPipe” JEST ZAREJESTROWANYM ZNAKIEM SŁOWNYM, KTÓRY PODLEGA [POLITYCE
> DOTYCZĄCEJ ZNAKÓW TOWAROWYCH](https://newpipe-ev.de/policy/trademark/). NIE
> BĘDZIE SIĘ TOLEROWAĆ PRZYPADKÓW NARUSZENIA TEGO ZNAKU.</b>

## Screenshoty

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

### Obsługiwane usługi

NewPipe obsługuje obecnie następujące serwisy:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([website](https://www.youtube.com/)) oraz YouTube Music
  ([website](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([website](https://joinpeertube.org/)) oraz wszystkie jego instancje
  (odwiedź stronę, aby dowiedzieć się co to oznacza!)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([website](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([website](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([website](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Jak widać, NewPipe obsługuje wiele serwisów wideo i audio. Chociaż początkowo
skupiało się wyłącznie na YouTube, z biegiem lat inni użytkownicy dodali kolejne
serwisy, dzięki czemu NewPipe staje się coraz bardziej wszechstronne!

Częściowo ze względu na okoliczności, a częściowo ze względu na swoją
popularność, YouTube jest najlepiej obsługiwanym serwisem spośród wymienionych.
Jeśli korzystasz z którejkolwiek z pozostałych platform lub znasz je dobrze,
pomóż nam ulepszyć obsługę tych serwisów! Poszukujemy osób odpowiedzialnych za
utrzymanie serwisów SoundCloud i PeerTube.

Jeśli chcesz dodać nową usługę, skontaktuj się z nami najpierw! W naszej
[docs](https://teamnewpipe.github.io/documentation/) znajdziesz więcej
informacji na temat tego, jak dodać nową usługę do aplikacji oraz do [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Opis

NewPipe nie używa żadnych bibliotek Google ani API YouTube. Strony są jedynie
analizowane aby zdobyć wymagane informacje, dzięki czemu ta aplikacja może
zostać zainstalowana na urządzeniach bez Usług Google Play. Konto YouTube nie
jest wymagane aby używać NewPipe, które jest wolnym oprogramowaniem copyleft.

Ponadto, ponieważ są to programy darmowe i oparte na otwartym kodzie źródłowym,
ani aplikacja, ani narzędzie Extractor nie korzystają z żadnych zastrzeżonych
bibliotek ani frameworków, takich jak Google Play Services. Oznacza to, że z
NewPipe można korzystać na urządzeniach lub niestandardowych ROM-ach, na których
nie zainstalowano aplikacji Google.

### Funkcje

* Wyszukiwanie filmów
* Brak wymogu konta Google/YouTube
* Wyświetlanie generalnych informacji o filmach
* Oglądanie filmów na YouTube
* Słuchanie filmów na YouTube
* Tryb okienkowy (Pływające okno)
* Wybieranie odtwarzacza przez który możemy oglądać film
* Pobieranie filmów
* Pobieranie audio
* Otwieranie filmów w Kodi
* Pokazanie następnych/podobnych filmów
* Wyszukiwanie w YouTube w konkretnym języku
* Oglądanie/Blokowanie materiałów zablokowanych wiekowo.
* Wyświetlanie generalnych informacji o kanałach
* Wyszukiwanie kanałów
* Oglądanie filmów ze strony kanału
* Wsparcie dla Orbot/Tor (Jeszcze nie bezpośrednio)
* Wsparcie dla 1080p/2K/4K
* Historia oglądania
* Subskrybowanie kanałów
* Historia wyszukiwania
* Wyszukiwanie/Oglądanie playlist

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Instalacja i aktualizacje
Możesz zainstalować NewPipe za pomocą jednej z tych metod:
 1. Dodaj nasze repozytorium do F-Droid i z tamtąd pobierz NewPipe. Instrukcje
    znajdują się tutaj:
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Pobierz APK z [Wydań na
    GitHubie](https://github.com/TeamNewPipe/NewPipe/releases) i zainstaluj go.
 3. Zaktualizuj poprzez F-Droid. Jest to najwolniejsza metoda aktualizacji,
    ponieważ F-Droid musi rozpoznać zmiany, zbudować APK, podpisać go i dopiero
    wypuścić aktualizację do użytkowników.
 4. Zbuduj debug APK samemu. Jest to najszybszy sposób na zdobycie najnowszych
    funkcji, ale jest to o wiele, wiele bardziej skomplikowane, dlatego
    rekomendujemy używania jednej z powyższych metod.
 5. Zrób kopię danych wchodząc w Ustawienia > Zawartość > Eksportuj dane, dzięki
    czemu zachowasz swoją historię, subskrypcje i playlisty

Dla większości użytkowników rekomendujemy metodę 1. Pliki APK zainstalowane za
pomocą metod 1 lub 2 są ze sobą kompatybilne, lecz nie są kompatybilne z tymi
zainstalowanymi metodą 3. Dzieje się tak ponieważ ten sam (nasz) klucz jest
używany do podpisania APK z metod 1 i 2, ale APK z metody 3 podpisany jest innym
kluczem (F-Droida). Budowanie debug APK z metody 4 w ogóle nie uwzględnia
klucza. Klucze podpisu zapewniają, że użytkownik omylnie nie zainstaluje
złośliwej aktualizacji żadnej aplikacji.

W międzyczasie, jeśli z jakiegoś powodu chcesz zmienić źródło (np. rdzenna
funkcjonalność NewPipe psuje się a F-Droid nie ma jeszcze najnowszej
aktualizacji), rekomendujemy użyć następującej procedury:
1. Odinstaluj NewPipe
2. Pobierz APK z nowego źródła i zainstaluj go
3. Przywróć dane z kroku 1 wchodząc w Ustawienia > Zawartość > Importuj dane
4. Zaimportuj dane z kroku 1, wybierając opcję Ustawienia > Kopia zapasowa i
   przywracanie > Importuj bazę danych

> [!Note] Podczas importowania bazy danych do oficjalnej aplikacji zawsze
> upewnij się, że jest to baza danych wyeksportowana właśnie _z_ tej oficjalnej
> aplikacji. Jeśli zaimportujesz bazę danych wyeksportowaną z pliku APK innego
> niż oficjalna aplikacja, może to spowodować nieprawidłowe działanie aplikacji.
> Taka operacja nie jest obsługiwana i należy ją wykonywać wyłącznie wtedy, gdy
> masz absolutną pewność, że wiesz, co robisz.

### Informacje o pliku APK

Jest to odcisk SHA klucza podpisu aplikacji NewPipe, służący do weryfikacji
pobranych plików APK podpisanych przez nas. Odcisk ten jest również dostępny na
[NewPipe's website](https://newpipe.net#download). Ma to znaczenie w przypadku
metody 2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Wkład
Jeśli masz jakieś pomysły, tłumaczenia, zmiany designu, oczyszczony kod, lub
wielkie zmiany w kodzie, pomoc jest zawsze mile widziana. Im więcej jest
zrobione, tym lepsza staje się aplikacja!

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Wesprzyj
Jeśli podoba Ci się NewPipe, bardzo ucieszylibyśmy się z dotacji. Możesz wysłać
bitcoin lub przekazać darowiznę przez Bountysource lub Liberapay. Po więcej
informacji o darowiznach dla NewPipe, proszę zobacz naszą
[stronę](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Polityka prywatności

Założeniem projektu NewPipe jest dostarczanie prywatnego, anonimowego dostępu do
zasobów i usług internetowych. Dlatego też, ta aplikacja nie zbiera żadnych
informacji bez twojej zgody. Polityka prywatności NewPipe dokładnie wyjaśnia
jakie dane są wysyłane i przechowywane, gdy wyślesz raport awarii lub komentarz
na naszym blogu. Możesz znaleźć ten dokument
[tutaj](https://newpipe.net/legal/privacy/).

## Licencja
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe to wolne oprogramowanie: Możesz używać, uczyć się, udostępniać i
ulepszać je do woli. Możesz udostępniać i/lub modyfikować je na zasadach
licencji [GNU General Public License](https://www.gnu.org/licenses/gpl.html)
publikowaną przez Free Software Foundation, wersję 3 Licencji, lub (twój wybór)
każdną nowszą wersję.
