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

*Consultez ce document dans d'autres langues : [Deutsch](doc/README.de.md),
[English](README.md), [Español](doc/README.es.md), [Français](doc/README.fr.md),
[हिन्दी](doc/README.hi.md), [Italiano](doc/README.it.md),
[한국어](doc/README.ko.md), [Português Brasil](doc/README.pt_BR.md),
[Polski](doc/README.pl.md), [ਪੰਜਾਬੀ ](doc/README.pa.md),
[日本語](doc/README.ja.md), [Română](doc/README.ro.md),
[Soomaali](doc/README.so.md), [Türkçe](doc/README.tr.md), [Chinois
traditionnel](doc/README.zh_TW.md), [Assamais](doc/README.asm.md),
[Serbe](doc/README.sr.md), [Arabe](README.ar.md)*

> [!warning]\
> <b>CETTE APP EST EN BETA, VOUS POUVEZ DONC RENCONTRER DES BUGS. SI C'EST LE
> CAS, OUVREZ UNE ISSUE DANS NOTRE DÉPÔT GITHUB EN REMPLISSANT LA CATEGORIE
> ISSUE.</b>
> 
> <b>LA PUBLICATION DE NEWPIPE, OU DE TOUTE VERSION DÉRIVÉE DE CETTE
> APPLICATION, SUR LE GOOGLE PLAY STORE CONSTITUE UNE VIOLATION DE LEURS
> CONDITIONS GÉNÉRALES.</b>
> 
> <b>« NewPipe » EST UNE MARQUE VERBALE ENREGISTRÉE SOUMISE À [SA POLITIQUE EN
> MATIÈRE DE MARQUES](https://newpipe-ev.de/policy/trademark/). TOUTE
> UTILISATION CONTREFAITE NE SERA PAS TOLÉRÉE.</b>

## Captures d'écran

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

### Services supportés

NewPipe prend actuellement en charge les services suivants :

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([site web](https://www.youtube.com/)) et YouTube Music ([site
  web](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([site web](https://joinpeertube.org/)) et toutes ses instances
  (regardez le site web pour savoir ce que cela signifie !)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([site web](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([site web](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([site web](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Comme vous pouvez le constater, NewPipe supporte de multiples services vidéo et
audio. Bien que cela ait commencé avec YouTube, d'autres personnes ont ajouté de
nouveaux services au fil des années, rendant NewPipe de plus en plus polyvalent
!

Grace aux circonstances et en raison de sa popularité, YouTube est le service le
mieux pris en charge. Si vous utilisez ou connaissez l'un de ces autres
services, aidez-nous à améliorer leur prise en charge ! Nous recherchons des
mainteneurs pour SoundCloud et PeerTube.

Si vous avez l'intention d'ajouter un nouveau service, veuillez d'abord nous
contacter ! Notre [documentation](https://teamnewpipe.github.io/documentation/)
fournit plus d'informations sur la façon dont un nouveau service peut être
ajouté à l'application et au [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Description

NewPipe fonctionne en récupérant les données requises à partir des APIs
officielles (par exemple PeerTube) du service que vous utilisez. Si l'API
officielle est limitée (par exemple YouTube) pour nos besoins, ou si elle est
propriétaire, l'application analyse le site Web ou utilise une API interne à la
place. Cela signifie que vous n'avez pas besoin de compte sur n'importe lequel
des services proposés pour utiliser NewPipe.

De plus, puisqu'il s'agit de logiciels libres et gratuits, ni l'application ni
l'Extracteur n'utilisent de bibliothèques ou de frameworks propriétaires, comme
les services Google Play. Cela signifie que vous pouvez utiliser NewPipe sur des
appareils ou des ROM personnalisées qui n'ont pas d'applications Google
installées.

### Fonctionnalités

* Regarder des vidéos à des résolutions allant jusqu'à la 4K
* Écouter de l'audio en arrière-plan, en ne chargant que le flux audio pour
  économiser des données
* Mode Popup (lecteur floattant, aka Picture-in-Picture)
* Regarder des diffusions en direct
* Afficher/masquer les sous-titres
* Chercher des vidéos et des audios (sur Youtube, vous pouvez également
  spécifier la langue du contenu)
* Mettre en file d'attente les vidéos (et éventuellement les enregistrer en tant
  que listes de lecture locales)
* Afficher/cacher les informations générales à propos des vidéos (comme la
  description et les tags)
* Afficher/cacher les vidéos suivantes ou en relation
* Afficher/cacher les commentaires
* Chercher des vidéos, des audios, des chaînes, des listes de lecture et des
  albums
* Parcourir les vidéos et les audios d'une chaîne
* S'abonner à des chaînes (oui, sans avoir besoin de se connecter à un compte !)
* Recevez des notifications concernant les nouvelles vidéos des chaînes
  auxquelles vous êtes abonné
* Créer et modifier des groupes de chaînes (pour une gestion et une navigation
  plus simples)
* Parcourir des fils de vidéos générés depuis vos groupes de chaînes
* Voir et rechercher dans votre historique de visionnage
* Voir et chercher les listes de lectures (il s'agit de listes de lecture à
  distance, ce qui signifie qu'elles sont extraites du service sur lequel vous
  naviguez)
* Créer et éditer des listes de lecture locales (elles sont créées et
  sauvegardées dans l'application, et n'ont rien à voir avec n'importe lequel
  des services)
* Télécharger des vidéos/de l'audio/des sous-titres
* Lire dans Kodi
* Regarder/bloquer du contenu restreint en fonction de l'âge

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Installation et mises à jour
Vous pouvez installer NewPipe en suivant l'une des méthodes suivantes :
 1. Ajoutez notre dépôt personnalisé à F-Droid et installez-le à partir de là.
    Vous trouverez les instructions ici :
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Téléchargez l'APK depuis les [GitHub
    Releases](https://github.com/TeamNewPipe/NewPipe/releases) et l'installer.
 3. Mettez à jour via F-Droid. C'est la méthode la plus lente pour avoir des
    mises à jour, car F-Droid doit détecter les changements, construire l'APK
    lui-même, le signer puis enfin publier la mise à jour.
 4. Construisez un APK de débug par vous-même. C'est la façon la plus rapide
    d'avoir des nouvelles fonctionnalités sur votre appareil, mais c'est bien
    plus compliqué, c'est pour cela que nous vous recommandons d'utiliser une
    des autres méthodes.
 5. Si vous êtes intéréssé par une fonctionnalité ou une résolution de bug
    spécifique à une PR de ce dépôt, vous pouvez également télécharger l'APK à
    partir de la PR. Vous trouverez les instructions dans la description de la
    PR. Ce qui est super avec les APK spécifiques à des PR est qu'ils sont
    installés à côté de l'application officielle, vous n'avez donc pas à vous
    inquiéter de perdre vos données ou de foirer quoi que ce soit.

Nous recommandons la méthode 1 à la plupart des utilisateurs. Les APK installés
à l'aide des méthodes 1 ou 2 sont compatibles entre eux (ce qui signifie que si
vous avez installé NewPipe à l'aide de la méthode 1 ou 2, vous pouvez également
mettre à jour NewPipe à l'aide de l'autre méthode), mais pas avec ceux installés
à l'aide de la méthode 3. Cela s'explique par le fait que la même clé de
signature (la nôtre) est utilisée pour les méthodes 1 et 2, tandis qu'une clé de
signature différente (celle de F-Droid) est utilisée pour la méthode 3. La
création d’un APK de débogage à l’aide de la méthode 4 exclut totalement toute
clé. Les clés de signature permettent de s’assurer qu’un utilisateur ne soit pas
amené par ruse à installer une mise à jour malveillante d’une application.
Lorsque vous utilisez la méthode 5, chaque APK est signé avec une clé aléatoire
différente fournie par GitHub Actions, vous ne pouvez donc même pas le mettre à
jour. Vous devrez sauvegarder et restaurer les données de l’application chaque
fois que vous souhaiterez utiliser un nouvel APK.

Entre temps, si vous voulez changer de source pour une raison quelconque (par
exemple, la fonctionnalité de base de NewPipe est cassée et F-Droid n'a pas
encore la dernière mise à jour), nous vous recommandons de suivre cette
procédure :
1. Sauvegardez vos données via Paramètres > Sauvegarde et restauration >
   Exporter la base de données afin de conserver votre historique, vos
   abonnements et vos listes de lecture
2. Désinstallez NewPipe
3. Téléchargez le fichier APK à partir de la nouvelle source et installez-le
4. Importez les données de l'étape 1 via Paramètres > Sauvegarde et restauration
   > Importer la base de données

> [!Note] Lorsque vous importez une base de données dans l'application
> officielle, assurez-vous toujours qu'il s'agit bien de celle que vous avez
> exportée _à partir_ de l'application officielle. Si vous importez une base de
> données exportée à partir d'un APK autre que celui de l'application
> officielle, cela risque de provoquer des dysfonctionnements. Cette opération
> n'est pas prise en charge, et vous ne devriez la réaliser que si vous êtes
> absolument certain de savoir ce que vous faites.

### Informations sur l'APK

Il s'agit de l'empreinte SHA de la clé de signature de NewPipe, qui permet de
vérifier les fichiers APK téléchargés et signés par nos soins. Cette empreinte
est également disponible sur [le site web de
NewPipe](https://newpipe.net#download). Cette information est utile pour la
méthode 2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Contribuer
Que vous ayez des idées, des traductions, des changements de design, du
nettoyage de code, ou encore un changement de code majeur, toute aide est la
bienvenue. L'app s'améliore un peu plus à chaque contribution, peu importe
qu'elle soit grosse ou petite ! Si vous aimeriez être impliqué, jetez un coup
d'oeil à nos [notes pour contribuer](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Dons
Si vous appréciez NewPipe, n'hésitez pas à faire un don. Nous privilégions
Liberapay, car il s'agit d'une plateforme open source et à but non lucratif.
Pour plus d'informations sur les dons à NewPipe, rendez-vous sur notre [site
web](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Politique de confidentialité

Le projet NewPipe vise à offrir une expérience privée et anonyme pour
l'utilisation de services médiatiques basés sur le Web. Par conséquent,
l'application ne recueille aucune donnée sans votre consentement. La politique
de confidentialité de NewPipe explique en détail quelles données sont envoyées
et stockées lorsque vous envoyez un rapport d'incident ou laissez un commentaire
sur notre blog. Vous pouvez trouver le document
[ici](https://newpipe.net/legal/privacy/).

## Licence
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe est un logiciel libre : vous pouvez l'utiliser, l'étudier, le partager
et l'améliorer à volonté. Plus précisément, vous pouvez le redistribuer et/ou le
modifier selon les conditions de la [GNU General Public
License](https://www.gnu.org/licenses/gpl.html) telle que publiée par la Free
Software Foundation, soit la version 3 de la Licence, soit (à votre choix) toute
version ultérieure.
