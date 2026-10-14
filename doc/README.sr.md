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

*Прочитајте овај документ на другим језицима: [Deutsch](doc/README.de.md),
[English](README.md), [Español](doc/README.es.md), [Français](doc/README.fr.md),
[हिन्दी](doc/README.hi.md), [Italiano](doc/README.it.md),
[한국어](doc/README.ko.md), [Português Brasil](doc/README.pt_BR.md),
[Polski](doc/README.pl.md), [ਪੰਜਾਬੀ ](doc/README.pa.md),
[日本語](doc/README.ja.md), [Română](doc/README.ro.md),
[Soomaali](doc/README.so.md), [Türkçe](doc/README.tr.md),
[正體中文](doc/README.zh_TW.md), [অসমীয়া](doc/README.asm.md),
[Српски](doc/README.sr.md) , [العربية](README.ar.md)*

> [!warning]\
> <b>ОВА АПЛИКАЦИЈА ЈЕ У БЕТА ВЕРЗИЈИ, ТАКО ДА МОЖЕТЕ НАИЋИ НА ГРЕШКЕ. АКО
> НАИЂЕТЕ, НАПИШИТЕ ИЗВЕШТАЈ У НАШЕМ GITHUB РЕПОЗИТОРИЈУМУ ПОПУЊАВАЊЕМ ШАБЛОНА
> ИЗВЕШТАЈА.</b>
> 
> <b>ПОСТАВЉАЊЕ АПЛИКАЦИЈЕ NEWPIPE ИЛИ БИЛО КОГ ЊЕНОГ ФОРКА, У GOOGLE PLAY
> ПРОДАВНИЦУ КРШИ ЊИХОВЕ УСЛОВЕ И ОДРЕДБЕ.</b>
> 
> <b>"NewPipe" IS A REGISTERED WORD MARK SUBJECT TO [ITS TRADEMARK
> POLICY](https://newpipe-ev.de/policy/trademark/). INFRINGING APPLICATIONS WILL
> NOT BE TOLERATED.</b>

## Снимци екрана

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

### Подржане услуге

NewPipe тренутно подржава ове услуге:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([веб-сајт](https://www.youtube.com/)) и YouTube Music
  ([веб-сајт](https://music.youtube.com/))
  ([вики](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([веб-сајт](https://joinpeertube.org/)) и све његове инстанце
  (отворите веб-сајт да бисте сазнали шта то значи!)
  ([вики](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([веб-сајт](https://bandcamp.com/))
  ([вики](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([веб-сајт](https://soundcloud.com/))
  ([вики](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([веб-сајт](https://media.ccc.de/))
  ([вики](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Као што видите, NewPipe подржава више видео и аудио услуга. Иако је почело са
YouTube-ом, други људи су додали више услуга током година, чинећи NewPipe све
разноврснијим!

Делимично због околности, а делимично због своје популарности, YouTube је
најбоље подржан од ових услуга. Ако користите или сте упознати са било којом од
ових других услуга, помозите нам да побољшамо подршку за њих! Тражимо одржаваоце
за SoundCloud и PeerTube.

Ако намеравате да додате нову услугу, прво нас контактирајте! Наши
[документи](https://teamnewpipe.github.io/documentation/) пружају више
информација о томе како се нова услуга може додати у апликацију и у [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Опис

NewPipe ради тако што преузима потребне податке из званичног API-ја (нпр.
PeerTube) услуге коју користите. Ако је званични API ограничен (нпр. YouTube) за
наше сврхе или је власнички, апликација анализира веб-сајт или уместо тога
користи унутрашњи API. То значи да вам није потребан налог ни на једној услузи
да бисте користили NewPipe.

Такође, пошто су бесплатни и отвореног кода, ни апликација, ни Extractor не
користе никакве власничке библиотеке или радне оквире, као што су Google Play
услуге. То значи да можете да користите NewPipe на уређајима или прилагођеним
ROM-овима који немају инсталиране Google апликације.

### Карактеристике

* Гледајте видео снимке у резолуцијама до 4К
* Слушајте аудио снимке у позадини, само учитавајући аудио стрим да бисте
  сачували податке
* Искачући режим (плутајући плејер, познат као „Слика у слици”)
* Гледајте стримове уживо
* Прикажите/сакријте титлове/скривене титлове
* Претражујте видео и аудио снимке (на YouTube-у можете одредити и језик
  садржаја)
* Ставите видео снимке у ред чекања (и опционо их сачувајте као локалне
  плејлисте)
* Прикажите/сакријте опште информације о видео снимцима (као што су опис и
  ознаке)
* Прикажите/сакријте следеће/сродне видео снимке
* Прикажите/сакријте коментаре
* Претражујте видео снимке, аудио снимке, канале, плејлисте и албуме
* Прегледајте видео и аудио снимке унутар канала
* Претплатите се на канале (да, без пријављивања на било који налог!)
* Добијајте обавештења о новим видео снимцима са канала на које сте претплаћени
* Креирајте и уређујте групе канала (за лакше прегледање и управљање)
* Прегледајте фидове видео снимака генерисане из ваших група канала
* Прегледајте и претражујте историју гледања
* Претражујте и гледајте плејлисте (ово су даљинске плејлисте, што значи да се
  преузимају са услуге коју прегледате)
* Направите и уређујте локалне плејлисте (оне се креирају и чувају у апликацији
  и немају никакве везе са било којом услугом)
* Преузмите видео снимке/аудио снимке/титлове (скривене титлове)
* Отварајте у Kodi-ју
* Гледајте/блокирајте старосно ограничени материјал

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Инсталација и ажурирања
Можете да инсталирате NewPipe користећи један од следећих начина:
 1. Додајте наш прилагођени репозиторијум на F-Droid и инсталирајте га одатле.
    Упутства су овде: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Преузмите APK са [GitHub
    издања](https://github.com/TeamNewPipe/NewPipe/releases) и инсталирајте га.
 3. Ажурирајте преко F-Droid-а. Ово је најспорији начин добијања ажурирања, јер
    F-Droid мора да препозна промене, да направи сам APK, да га потпише, а затим
    да ажурирање пошаље корисницима.
 4. Сами направите APK за отклањање грешака. Ово је најбржи начин да добијете
    нове функције на свом уређају, али је много компликованији, па препоручујемо
    да користите неки од других начина.
 5. Ако сте заинтересовани за одређену функцију или исправку грешке која се
    налази у захтеву за повлачење у овом репозиторијуму, такође можете преузети
    његов APK из захтева за повлачење. Прочитајте опис захтева за повлачење за
    упутства. Одлична ствар у вези са APK-овима специфичним за захтев за
    повлачење је то што су инсталирани упоредо са званичном апликацијом, тако да
    не морате да бринете да ћете изгубити своје податке или нешто забрљати.

Препоручујемо 1. начин за већину корисника. APK-ови инсталирани коришћењем 1.
или 2. начина су компатибилни један са другим (што значи да ако сте инсталирали
NewPipe користећи било који 1. или 2. начин, можете, такође, да ажурирате
NewPipe користећи други начин), али не и са онима инсталираним коришћењем 3.
начина. То је због истог кључа за потписивање (нашег) који се користи за 1. и 2.
начин, али се други кључ за потписивање (F-Droid-ови) користи за 3. начин.
Израда APK-а за отклањање грешака коришћењем 4. начина искључује кључ у
потпуности. Кључеви за потписивање помажу да се осигура да корисник не буде
преварен да инсталира злонамерно ажурирање за апликацију. Када користите 5.
начин, сваки APK је потписан различитим, насумичним кључем који обезбеђује
GitHub Actions, тако да не можете ни да га ажурирате. Мораћете да направите
резервну копију и вратите податке апликације сваки пут када желите да користите
нови APK.

У међувремену, ако желите да промените изворе из неког разлога (нпр. основна
функционалност NewPipe-а се прекида, а F-Droid још увек нема најновије
ажурирање), препоручујемо вам да следите ову процедуру: 1. Направите резервну
копију података преко Подешавања > Садржај > Извези базу података, да бисте
задржали своју историју, претплате и листе песама 2. Деинсталирајте NewPipe 3.
Преузмите APK са новог извора и инсталирајте га 4. Увезите податке из 1. корака
преко Подешавања > Садржај > Увези базу података
1. Back up your data via Settings > Backup and Restore > Export Database so you
   keep your history, subscriptions, and playlists
2. Uninstall NewPipe
3. Download the APK from the new source and install it
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

## Допринос
Без обзира да ли имате идеје, преводе, промене дизајна, чишћење кода или чак
велике промене кода, помоћ је увек добродошла. Апликација постаје све боља и
боља са сваким доприносом, без обзира колико је он велики или мали! Ако желите
да се укључите, погледајте наше [напомене о
доприносима](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Донација
Ако вам се свиђа NewPipe, можете послати донацију. Више волимо Liberapay, јер је
отвореног кода и непрофитан. За више информација о донирању апликацији NewPipe,
посетите наш [веб-сајт](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Политика приватности

Пројекат NewPipe има за циљ да обезбеди приватно, анонимно искуство за коришћење
медијских услуга заснованих на вебу. Стога, апликација не прикупља никакве
податке без вашег пристанка. Политика приватности апликације NewPipe детаљно
објашњава који се подаци шаљу и чувају када пошаљете извештај о отказивању или
оставите коментар на нашем блогу. Документ можете пронаћи
[овде](https://newpipe.net/legal/privacy/).

## Лиценца
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe је бесплатан софтвер: можете га користити, проучавати, делити и
побољшавати по жељи. Конкретно, можете га редистрибуирати и/или модификовати под
условима [GNU General Public License](https://www.gnu.org/licenses/gpl.html),
коју је објавила Фондација за слободни софтвер, било верзију 3 лиценце или (по
вашем избору) било коју каснију верзију.
