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
> <b>ESTA ES UNA VERSIÓN BETA, POR LO TANTO, PUEDES ENCONTRAR BUGS. SI
> ENCUENTRAS UNO ABRA UN ISSUE A TRAVÉS DE NUESTRO REPOSITORIO DE GITHUB.</b>
> 
> <b>COLOCAR NEWPIPE O CUALQUIER FORK DE NEWPIPE EN LA GOOGLE PLAY STORE VIOLARÁ
> SUS TÉRMINOS Y CONDICIONES.</b>
> 
> <b>"NewPipe" IS A REGISTERED WORD MARK SUBJECT TO [ITS TRADEMARK
> POLICY](https://newpipe-ev.de/policy/trademark/). INFRINGING APPLICATIONS WILL
> NOT BE TOLERATED.</b>

## Capturas de Pantalla

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

### Servicios compatibles

Actualmente, NewPipe es compatible con estos servicios:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([website](https://www.youtube.com/)) y YouTube Music
  ([website](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([website](https://joinpeertube.org/)) y todas sus instancias (¡Entra
  al sitio web para saber qué significa eso!)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([website](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([website](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([website](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Como puedes ver, NewPipe es compatible con múltiples servicios de vídeo y audio.
Aunque comenzó con YouTube, otros usuarios han ido añadiendo más servicios a lo
largo de los años, ¡Lo que ha hecho que NewPipe sea cada vez más versátil!

En parte por las circunstancias y en parte por su popularidad, YouTube es el
servicio más compatible de entre todos estos. Si utilizas o conoces alguno de
estos otros servicios, ¡Ayúdanos a mejorar la compatibilidad con ellos! Buscamos
mantenedores para SoundCloud y PeerTube.

If you intend to add a new service, please get in touch with us first! Our
[docs](https://teamnewpipe.github.io/documentation/) provide more information on
how a new service can be added to the app and to the [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Descripción

NewPipe no usa ninguna librería del framework de Google, ni la API de YouTube.
Los sitios web solamente se analizan para extraer la información requerida, por
lo que esta app se puede usar sin los servicios de Google instalados. Además, no
se necesita una cuenta de YouTube para usar NewPipe, que es un programa libre
{1}copyleft{2}.

Además, al tratarse de software libre y de código abierto, ni la aplicación ni
el Extractor utilizan bibliotecas o marcos de trabajo propietarios, como Google
Play Services. Esto significa que puedes utilizar NewPipe en dispositivos o ROMs
personalizadas que no tengan instaladas las aplicaciones de Google.

### Características

* Buscar videos
* No requiere inicio de sesión
* Mostrar información general sobre videos
* Mirar videos de YouTube
* Modo de solo audio en videos de YouTube
* Modo pop-up (reproductor flotante)
* Elegir un reproductor de video externo para mirar videos
* Descargar videos
* Descargar solo audio
* Abrir videos en Kodi
* Mostrar videos próximos/relacionados
* Buscar a través de YouTube en un idioma específico
* Mirar/Bloquear videos restringidos por edad
* Mostrar información general sobre canales
* Buscador de canales
* Mirar videos de un canal
* Soporte Orbot/Tor (todavía no directamente)
* Soporte para videos en 1080p/2K/4K
* Historial de videos vistos
* Suscripción a canales
* Historial de búsquedas
* Buscar/Mirar listas de reproducción

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Instalación y Actualizaciones
Se puede instalar NewPipe usando uno de los métodos siguientes:
 1. Agregando nuestro repositorio personalizado a F-Droid e instalarlo desde
    allí. Las instrucciones están [aquí]{1}.
 2. Descargando el archivo APK de
    [aquí](https://github.com/TeamNewPipe/NewPipe/releases) y posteriormente
    instalarlo.
 3. Usando el repositorio oficial de F-Droid. Este es el método más lento para
    obtener actualizaciones, ya que F-Droid debe reconocer los cambios,
    construir el APK aparte, firmarlo con una clave, y finalmente publicar la
    actualización.
 4. Construyendo la app usted mismo. Este es el modo más rápido para obtener
    nuevas características en su dispositivo, pero es mucho más complicado, así
    que recomendamos uno de los otros métodos.
 5. Respalde su información a través de Ajustes > Contenido > Exportar base de
    datos; esto guardará su historial (videos vistos y búsquedas),
    suscripciones, listas de reproducción y ajustes.

Recomendamos el método 1 para la mayoría de usuarios. Los APKs instalados usando
método 1 y 2 son compatibles entre sí, pero no lo son con los instalados usando
el método 3. Esto es debido a la clave de firmado, ya que los métodos 1 y 2 usan
la misma clave (la nuestra), pero el método 3 usa una clave diferente (la de
F-Droid). El método 4 excluye totalmente una clave de firmado. Las claves de
firmado aseguran que el usuario no esté siendo engañado para instalar/actualizar
una APK maliciosa.

Además, si quiere cambiar el método de instalación por alguna razón (por
ejemplo: la funcionalidad del núcleo de NewPipe se rompe o F-Droid aún no
publica la actualización), recomendamos el siguiente procedimiento:
1. Desinstale NewPipe.
2. Descargue el APK con un método distinto e instálelo.
3. Importe la información (la base de datos extraída del paso 1) a través de
   Ajustes > Contenido > Importar base de datos. Tenga en cuenta que esta opción
   superpondrá su historial actual (tanto de vídeos como de búsquedas), sus
   listas de reproducción y (opcionalmente) sus configuraciones.
4. Importa los datos del paso 1 a través de Ajustes > Respaldar y restaurar >
   Importar base de datos

> [!Note] When you're importing a database into the official app, always make
> sure that it is the one you exported _from_ the official app. If you import a
> database exported from an APK other than the official app, it may break
> things. Such an action is unsupported, and you should only do so when you're
> absolutely certain you know what you're doing.

### Info. del APK

This is the SHA fingerprint of NewPipe's signing key to verify downloaded APKs
which are signed by us. The fingerprint is also available on [NewPipe's
website](https://newpipe.net#download). This is relevant for method 2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Contribución
Si tiene ideas, traducciones, cambios de diseño, limpieza de código o cambios
grandes de código, su ayuda es siempre bienvenida. ¡Cuanto más hagamos, NewPipe
será mucho mejor!

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Donar
Si te gusta NewPipe, estaremos felices con una donación. Puede enviar bitcoin o
donar a través de Bountysource o Liberapay. Visita nuestro [sitio
web](https://newpipe.net/donate) para más información.

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Política de privacidad

El proyecto NewPipe tiene como objetivo ofrecer una experience privada y anónima
al usar servicios web multimedia. Por lo tanto, la app no recoleta ningún tipo
de información sin tu consentimiento. La politica de privacidad de NewPipe
explica en detalle qué información es enviada y almacenada cuando envía un
informe de error o comenta en [nuestro
blog](https://newpipe.net/legal/privacy/). Puede encontrar el documento
[aquí]{2}.

## Licencia
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe es Software Libre: Puede usarlo, estudiarlo, compartirlo y mejorarlo a
su voluntad. Más específicamente, puede redistribuirlo y/o modificarlo bajo los
términos de la [GNU General Public
License](https://www.gnu.org/licenses/gpl.html) publicada por la Free Software
Foundation tanto si usa la versión 3 o posterior de la licencia.
