<p align="center"><a href="https://newpipe.net"><img src="assets/new_pipe_icon_5.png" width="150"></a></p> 
<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">Свободный и легковесный клиент потоковых сервисов для Android.</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Скачать на F-Droid" height=80/></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub release"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg"></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="Лицензия: GPLv3"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="Состояние сборки"><img src="https://github.com/TeamNewPipe/NewPipe/workflows/CI/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="Состояние перевода"><img src="https://hosted.weblate.org/widgets/newpipe/-/svg-badge.svg"></a>
<a href="https://web.libera.chat/#newpipe" alt="Канал IRC: #newpipe"><img src="https://img.shields.io/badge/IRC%20chat-%23newpipe-brightgreen.svg"></a>
</p>
<hr>
<p align="center"><a href="#screenshots">Скриншоты</a> &bull; <a href="#supported-services">Поддерживаемые сервисы</a> &bull; <a href="#description">Описание</a> &bull; <a href="#features">Возможности</a> &bull; <a href="#installation-and-updates">Установка и обновления</a> &bull; <a href="#contribution">Участие</a> &bull; <a href="#donate">Пожертвование</a> &bull; <a href="#license">Лицензия</a></p>
<p align="center"><a href="https://newpipe.net">Сайт</a> &bull; <a href="https://newpipe.net/blog/">Блог</a> &bull; <a href="https://newpipe.net/FAQ/">ЧЗВ</a> &bull; <a href="https://newpipe.net/press/">Пресса</a></p>
<hr>

*Read this document in other languages: [Deutsch](README.de.md), [English](../README.md), [Español](README.es.md), [Français](README.fr.md), [हिन्दी](README.hi.md), [Italiano](README.it.md), [한국어](README.ko.md), [Português Brasil](README.pt_BR.md), [Polski](README.pl.md), [ਪੰਜਾਬੀ ](README.pa.md), [日本語](README.ja.md), [Română](README.ro.md), [Soomaali](README.so.md), [Türkçe](README.tr.md), [正體中文](README.zh_TW.md), [অসমীয়া](README.asm.md), [うちなーぐち](README.ryu.md), [Српски](README.sr.md)*

<b>ВНИМАНИЕ: ЭТО ПРИЛОЖЕНИЕ НАХОДИТСЯ В СТАДИИ БЕТА, ПОЭТОМУ ВЫ МОЖЕТЕ ОБНАРУЖИТЬ ОШИБКИ. ЕСЛИ ЭТО СЛУЧИТСЯ, ОТКРОЙТЕ ВОПРОС В НАШЕМ РЕПОЗИТОРИИ GITHUB, ЗАПОЛНЯ ЕГО ПО ШАБЛОНУ.</b>

<b>РАЗМЕЩЕНИЕ NEWPIPE ИЛИ ЕГО ОТВЕТВЛЕНИЙ В МАГАЗИНЕ GOOGLE PLAY НАРУШАЕТ ИХ УСЛОВИЯ И ПОЛОЖЕНИЯ.</b>

## Скриншоты

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


### Поддерживаемые сервисы

NewPipe на данный момент поддерживает эти сервисы:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([сайт](https://www.youtube.com/)) и YouTube Music ([сайт](https://music.youtube.com/)) ([Вики](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([сайт](https://joinpeertube.org/)) и все его вариации (откройте этот сайт, чтобы понять о чём мы!) ([Вики](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([сайт](https://bandcamp.com/)) ([Вики](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([сайт](https://soundcloud.com/)) ([Вики](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([сайт](https://media.ccc.de/)) ([Вики](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Как видите, NewPipe поддерживает несколько видео- и аудиосервисов. Хотя все началось с YouTube, с годами люди добавляли новые сервисы, делая NewPipe все более и более универсальным!

Частично из-за обстоятельств, а частично из-за своей популярности лучше всего из этих сервисов поддерживается YouTube. Если вы используете какие-либо из них или знакомы с ними, помогите нам улучшить их поддержку! Мы ищем сопровождающих для SoundCloud и PeerTube.

Если вы хотите добавить новый сервис, сначала свяжитесь с нами! В нашей [документации](https://teamnewpipe.github.io/documentation/) содержится дополнительная информация о том, как можно добавить новый сервис в приложение и в [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Описание

NewPipe работает, извлекая необходимые данные из официального API (например, PeerTube) сервиса, который вы используете. Если официальный API ограничен (например, YouTube) для наших целей или является проприетарным, приложение анализирует веб-сайт или использует вместо этого внутренний API. Это означает, что вам не нужна учетная запись в этих сервисах для использования NewPipe.

Кроме того, поскольку это бесплатное и открытое программное обеспечение, ни приложение, ни Extractor не используют какие-либо проприетарные библиотеки или платформы, такие как Google Play Services. Это означает, что вы можете использовать NewPipe на устройствах или пользовательских ROM, на которых не установлены приложения Google.

### Возможности

* Смотрите видео с разрешением до 4K
* Слушайте аудио в фоновом режиме, загружая аудиопоток только для сохранения данных
* Режим всплывания (плавающий плеер, он же «Картинка в картинке»)
* Смотрите прямые трансляции
* Показать/скрыть субтитры/скрытые титры
* Поиск видео и аудио (на YouTube вы также можете указать язык контента)
* Очередь видео (и, при желании, сохраняйте их как локальные плейлисты)
* Показать/скрыть общую информацию о видео (например, описание и теги)
* Показать/скрыть следующие/похожие видео
* Показать/скрыть комментарии
* Поиск видео, аудио, каналов, плейлистов и альбомов
* Просматривайте видео и аудио на канале
* Подписывайтесь на каналы (да, без входа в какой-либо аккаунт!)
* Получайте уведомления о новых видео с каналов, на которые вы подписаны
* Создавайте и редактируйте группы каналов (для более удобного просмотра и управления)
* Просматривайте ленты с видео, добавленными из ваших групп каналов
* Обзор и поиск истории просмотра
* Поиск и просмотр списков воспроизведения (это удаленные списки воспроизведения, что означает, что они получены из сервиса, которую вы просматриваете)
* Создавайте и редактируйте локальные списки воспроизведения (они создаются и сохраняются в приложении и не имеют ничего общего с каким-либо сервисом)
* Скачивайте видео/аудио/субтитры (скрытые титры)
* Откройте в Kodi
* Смотрите/блокируйте материалы с возрастным ограничением

## Установка и обновления
Вы можете установить NewPipe одним из следующих способов:
 1. Добавьте наш пользовательский репозиторий в F-Droid и установите его оттуда. Инструкции находятся здесь: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Скачайте APK из [GitHub Releases](https://github.com/TeamNewPipe/NewPipe/releases) и установите его.
 3. Обновите через F-Droid. Это самый медленный способ получения обновлений, так как F-Droid должен распознавать изменения, создавать сам APK, подписывать его, а затем отправлять обновления пользователям.
 4. Создайте отладочный APK самостоятельно. Это самый быстрый способ получить новые функции на вашем устройстве, но он намного сложнее, поэтому мы рекомендуем использовать другие способы.
 5. Если вас интересует конкретная функция или исправление ошибки, предоставленные в пул реквесте в этом репозитории, вы также можете скачать файл APK из PR. Прочтите описание PR для получения инструкций. Отличительной особенностью APK-файлов из PR является то, что они устанавливаются рядом с официальным приложением, поэтому вам не нужно беспокоиться о потере ваших данных или о том, что возникнет путаница.

Мы рекомендуем 1 способ для большинства пользователей. APK, установленные 1 или 2 способом, совместимы друг с другом (это означает, что если вы установили NewPipe с помощью способа 1 или 2, вы также можете обновить NewPipe с помощью другого), но не с теми APK, которые были установлены с помощью способа 3. Это потому, что для 1 и 2 способа используется один и тот же ключ подписи (наш), но для способа 3 используется другой ключ подписи (F-Droid). Создание отладочного APK с использованием 4 способа полностью исключает ключ. Ключи подписи помогают гарантировать, что пользователь не будет обманут установкой вредоносного обновления для приложения. При использовании 5 способа каждый APK подписывается разными случайными ключами, предоставляемыми GitHub Actions, поэтому вы даже не сможете его обновить. Вам придется делать резервную копию и восстанавливать данные приложения каждый раз, когда захотите использовать новый APK.

Между тем, если вы хотите по какой-то причине сменить источник (например, основные функции NewPipe не работают, а для F-Droid ещё нет последнего обновления), мы рекомендуем выполнить следующую процедуру:
1. Создайте резервную копию своих данных через «Настройки» > «Контент» > «Экспортировать базу данных», чтобы сохранить историю, подписки и плейлисты.
2. Удалите NewPipe
3. Загрузите APK из нового источника и установите его.
4. Импортируйте данные из шага 1 через «Настройки» > «Содержимое» > «Импортировать базу данных».

<b>Примечание: когда вы импортируете базу данных в официальное приложение, убедитесь, что это именно та база данных, которую вы экспортировали _из_ официального приложения. Если вы импортируете базу данных, экспортированную из APK, отличного от официального приложения, это может привести к ошибке. Такое действие не поддерживается, и вы должны делать его только тогда, когда абсолютно уверены, что знаете, что делаете.</b>

## Участие
Если у вас есть идеи, переводы, изменения дизайна, очистка кода или даже серьезные изменения кода, помощь всегда приветствуется. Приложение становится всё лучше и лучше с каждым вкладом, независимо от того, большой он или маленький! Если вы хотите принять участие, ознакомьтесь с нашими [заметками об участии](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Состояние перевода">
</a>

## Пожертвование
Если вам нравится NewPipe, вы можете отправить пожертвование. Мы предпочитаем Liberapay, так как это открытая и некоммерческая платформа. Для получения дополнительной информации о пожертвовании NewPipe посетите наш [сайт](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Посетите NewPipe на liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Пожертвование через Liberapay" height="35px"></a></td>
  </tr>
</table>

## Политика конфиденциальности

Целью проекта NewPipe является предоставление конфиденциального анонимного доступа к мультимедийным веб-сервисам. Поэтому приложение не собирает никаких данных без вашего согласия. Политика конфиденциальности NewPipe подробно объясняет, какие данные отправляются и сохраняются, когда вы отправляете отчёт о сбое или оставляете комментарий в нашем блоге. Вы можете найти документ [здесь](https://newpipe.net/legal/privacy/).

## Лицензия
[![Изображение GNU GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.ru.html)  

NewPipe — это бесплатное программное обеспечение: вы можете использовать, изучать, делиться и улучшать его по своему усмотрению. В частности, вы можете распространять и/или изменять ее в соответствии с условиями [Стандартной общественной лицензии GNU](https://www.gnu.org/licenses/gpl.html) (GPL-3.0), опубликованной Free Software Foundation, либо версии 3 Лицензии или (на ваш выбор) любая более поздняя версия.
