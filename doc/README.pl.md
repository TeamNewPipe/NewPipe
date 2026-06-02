<p align="center"><a href="https://github.com/wizdom13/NewPipe_Material"><img src="../assets/newpip_material_logo.png" width="150" alt="Ikona NewPipe Material"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Niezależny fork NewPipe dla Androida skupiony na Material 3.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="Licencja: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe_Material/actions"><img src="https://github.com/wizdom13/NewPipe_Material/actions/workflows/ci.yml/badge.svg?branch=material" alt="Stan kompilacji"></a>
</p>

<p align="center"><b>Czytaj w języku:</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.ru.md">Русский</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.ryu.md">沖縄口</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## Ważna informacja o forku

NewPipe Material to niezależnie utrzymywany fork NewPipe skupiony na wyglądzie Material 3, motywach aplikacji i dopracowaniu produktu.

Projekt **nie jest powiązany, sponsorowany ani zatwierdzony** przez oficjalny projekt NewPipe, TeamNewPipe ani NewPipe e.V.

NewPipe Material bazuje na NewPipe i zachowuje wolną licencję NewPipe, informacje o autorach projektu źródłowego oraz powiadomienia o licencjach stron trzecich.

---

## Czym jest NewPipe Material?

NewPipe Material zachowuje podstawowe doświadczenie NewPipe, jednocześnie modernizując tożsamość i interfejs aplikacji.

Cele forka:

- powierzchnie, okna dialogowe, ustawienia, karty i nawigacja inspirowane Material 3;
- dynamiczne kolory Material You, jeśli są dostępne;
- ręczne kolory motywu: domyślny aplikacji, neutralny, zielony, niebieski, fioletowy, pomarańczowy, różowy i czerwony;
- nowa tożsamość: **NewPipe Material**;
- osobny identyfikator aplikacji: `org.wisso.newpipematerial`;
- wersje debug instalują się oddzielnie jako `org.wisso.newpipematerial.debug`;
- zachowanie NewPipe, zgodność importu/eksportu i obsługiwane usługi pozostają zachowane.

Ten fork unika ryzykownych zmian zachowania w wrażliwych obszarach, takich jak odtwarzanie, pobieranie, odtwarzanie w tle, odtwarzacz pływający i logika Extractora, chyba że są one opracowane jako oddzielne i przetestowane zmiany.

---

## Zrzuty ekranu

### Telefon

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="Zrzut ekranu telefonu 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="Zrzut ekranu telefonu 2"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="Zrzut ekranu telefonu 3"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="Zrzut ekranu telefonu 4"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="Zrzut ekranu telefonu 5"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="Zrzut ekranu telefonu 6"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="Zrzut ekranu telefonu 7"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="Zrzut ekranu telefonu 8"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="Zrzut ekranu telefonu 9"></a>
</p>

### Tablet

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="Zrzut ekranu tabletu 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="Zrzut ekranu tabletu 2"></a>
</p>

---

## Obsługiwane usługi

NewPipe Material dziedziczy obsługę YouTube, YouTube Music, PeerTube, Bandcamp, SoundCloud i media.ccc.de z NewPipe.

Obsługa usług zależy od kodu źródłowego NewPipe i NewPipe Extractor.

---

## Funkcje

NewPipe Material zachowuje znane funkcje NewPipe: oglądanie filmów i transmisji, odtwarzanie w tle, odtwarzacz pływający, lokalne playlisty, subskrypcje bez konta platformy, grupy kanałów, wyszukiwanie, szczegóły filmów, pobieranie oraz import/eksport danych.

Dodatki Material obejmują role kolorów Material 3, dolną nawigację dla pięciu lub mniej głównych kart, dynamiczne i ręczne kolory motywu, atrybucję forka w ekranie O aplikacji oraz obsługę podpisywania wydań.

---

## Instalacja

Zainstaluj NewPipe Material z wydań GitHub tego repozytorium albo z podpisanych artefaktów, gdy są dostępne.
Releases: https://github.com/wizdom13/NewPipe_Material/releases

```text
Oficjalny NewPipe: org.schabi.newpipe / net.newpipe.app zależnie od buildu upstream
NewPipe Material:  org.wisso.newpipematerial
Debug:             org.wisso.newpipematerial.debug
```

Aby przenieść dane, wyeksportuj bazę danych z oficjalnego NewPipe w Settings > Backup and Restore, zainstaluj NewPipe Material i zaimportuj kopię zapasową. Zawsze zachowuj kopię bezpieczeństwa.

Nie publikuj NewPipe Material, NewPipe ani forków NewPipe w Google Play.

---

## Budowanie ze źródeł

Wymagania: JDK 21, Android SDK oraz Gradle wrapper z repozytorium.

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
./gradlew assembleDebug -DskipFormatKtlint
```

Build debug używa nazwy **NewPipe Material Debug** i pakietu `org.wisso.newpipematerial.debug`.

---

## Podpisywanie wydań

Zmienne środowiskowe:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

Zalecana weryfikacja:

```bash
./gradlew assembleRelease -DskipFormatKtlint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## Stan rozwoju

Ukończone lub w toku: nazwa i ID aplikacji, rozdzielenie debug/release, kolory Material 3, dynamiczne i ręczne motywy, dolna nawigacja, ekran O aplikacji, dialogi, snackbary, ustawienia, szczegóły wideo, interfejs pobierania i workflow podpisywania.

Odłożone lub wysokiego ryzyka: główna nakładka odtwarzacza, kolory paska postępu i gestów, kontrolki kolejki, menu jakości/audio/napisów oraz szerokie zmiany odtwarzania lub pobierania.

---

## Wkład

Wkład jest mile widziany: poprawki błędów, QA, dokumentacja, przygotowanie wydań i ukierunkowane dopracowanie Material 3. Zmiany powinny być skupione i łatwe do przetestowania.

---

## Upstream NewPipe

- Repozytorium NewPipe: https://github.com/TeamNewPipe/NewPipe
- Strona NewPipe: https://newpipe.net
- FAQ NewPipe: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Problemy specyficzne dla forka należą do tego repozytorium. Problemy usług lub Extractora mogą wymagać porównania z oficjalnym NewPipe.

---

## Darowizny

Aby wesprzeć upstream NewPipe: https://newpipe.net/donate

NewPipe Material jest niezależnym forkiem; darowizny upstream trafiają do projektu NewPipe, a nie automatycznie do tego forka.

---

## Licencja

NewPipe Material jest wolnym oprogramowaniem opartym na NewPipe i rozpowszechnianym na licencji GNU General Public License w wersji 3 lub nowszej. Szczegóły znajdują się w plikach licencji i ekranie licencji w aplikacji.
