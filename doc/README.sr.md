<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/newpip_material_logo.png" width="150" alt="Икона NewPipe Material"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Независни форк NewPipe-а за Android, усмерен на Material 3.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="Лиценца: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="Статус изградње"></a>
</p>

<p align="center"><b>Прочитај на:</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## Важно обавештење о форку

NewPipe Material је независно одржаван форк NewPipe-а, усмерен на Material 3 дизајн, теме апликације и полирање производа.

Овај пројекат **није повезан, спонзорисан нити одобрен** од званичног NewPipe пројекта, TeamNewPipe-а или NewPipe e.V.

NewPipe Material је изграђен на NewPipe-у и задржава слободну лиценцу NewPipe-а, заслуге узводног пројекта и обавештења о лиценцама трећих страна.

---

## Шта је NewPipe Material?

NewPipe Material задржава основно NewPipe искуство, док модернизује идентитет и кориснички интерфејс апликације.

Циљеви форка:

- површине, дијалози, подешавања, картице и навигација инспирисани Material 3 дизајном;
- подршка за динамичке Material You боје када је доступна;
- ручне боје теме: подразумевана апликације, неутрална, зелена, плава, љубичаста, наранџаста, ружичаста и црвена;
- нови идентитет апликације: **NewPipe Material**;
- посебан ID апликације: `org.wisso.newpipematerial`;
- debug верзије се инсталирају одвојено као `org.wisso.newpipematerial.debug`;
- задржавају се NewPipe понашање, import/export компатибилност и подржане услуге.

Овај форк избегава ризичне промене понашања у осетљивим областима као што су репродукција, преузимања, репродукција у позадини, popup плејер и Extractor логика, осим када су то посебне и тестиране промене.

---

## Снимци екрана

### Телефон

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="Снимак телефона 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="Снимак телефона 2"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="Снимак телефона 3"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="Снимак телефона 4"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="Снимак телефона 5"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="Снимак телефона 6"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="Снимак телефона 7"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="Снимак телефона 8"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="Снимак телефона 9"></a>
</p>

### Таблет

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="Снимак таблета 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="Снимак таблета 2"></a>
</p>

---

## Подржане услуге

NewPipe Material наслеђује NewPipe подршку за YouTube, YouTube Music, PeerTube, Bandcamp, SoundCloud и media.ccc.de.

Подршка услуга зависи од узводног кода NewPipe-а и NewPipe Extractor-а.

---

## Функције

NewPipe Material задржава познате NewPipe функције: видео и пренос уживо, репродукцију у позадини, popup плејер, локалне плејлисте, претплате без налога платформе, групе канала, претрагу, детаље видеа, преузимања и import/export података.

Material додаци укључују Material 3 улоге боја, доњу навигацију за пет или мање главних картица, динамичке/ручне боје теме, обавештење о форку у About екрану и подршку за потписивање издања.

---

## Инсталација

Инсталирај NewPipe Material из GitHub издања овог репозиторијума или из потписаних артефаката када су доступни.

```text
Званични NewPipe: org.schabi.newpipe / net.newpipe.app у зависности од узводног build-а
NewPipe Material: org.wisso.newpipematerial
Debug:            org.wisso.newpipematerial.debug
```

За миграцију података, извези базу из званичног NewPipe-а преко Settings > Backup and Restore, инсталирај NewPipe Material и увези резервну копију. Увек задржи backup.

Не објављуј NewPipe Material, NewPipe или NewPipe форкове на Google Play.

---

## Изградња из изворног кода

Захтеви: JDK 21, Android SDK и Gradle wrapper из репозиторијума.

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
./gradlew assembleDebug -DskipFormatKtlint
```

Debug build користи назив **NewPipe Material Debug** и пакет `org.wisso.newpipematerial.debug`.

---

## Потписивање издања

Променљиве окружења:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

Препоручена провера:

```bash
./gradlew assembleRelease -DskipFormatKtlint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## Статус развоја

Завршено или у току: име и ID апликације, раздвајање debug/release идентитета, Material 3 боје, динамичке/ручне боје, доња навигација, About екран, дијалози, snackbar-ови, подешавања, детаљи видеа, интерфејс преузимања и workflow потписивања.

Одложено или високог ризика: главни overlay плејера, боје seekbar-а и гестова, контроле реда, менији квалитета/звука/титлова и широке промене репродукције или преузимања.

---

## Допринос

Доприноси су добродошли: исправке, QA, документација, припрема издања и усмерено Material 3 полирање. Држи промене фокусираним и тестабилним.

---

## Узводни NewPipe

- NewPipe репозиторијум: https://github.com/TeamNewPipe/NewPipe
- NewPipe веб-сајт: https://newpipe.net
- NewPipe FAQ: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Проблеми специфични за форк припадају овом репозиторијуму. Проблеми услуга или Extractor-а могу захтевати поређење са званичним NewPipe-ом.

---

## Донације

За подршку узводном NewPipe-у: https://newpipe.net/donate

NewPipe Material је независни форк; узводне донације иду узводном NewPipe пројекту, не аутоматски овом форку.

---

## Лиценца

NewPipe Material је слободан софтвер заснован на NewPipe-у и дистрибуира се под GNU General Public License верзијом 3 или новијом. За детаље погледај лиценцне датотеке и екран лиценци у апликацији.
