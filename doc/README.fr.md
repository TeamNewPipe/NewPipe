<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/new_pipe_icon_5.png" width="150" alt="Icône NewPipe Material"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Un fork indépendant de NewPipe pour Android axé sur Material 3.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="Licence : GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="État de compilation"></a>
</p>

<p align="center"><b>Lire en :</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## Avis important concernant le fork

NewPipe Material est un fork maintenu indépendamment de NewPipe, centré sur le design Material 3, les thèmes de l’application et la finition du produit.

Ce projet **n’est pas affilié, sponsorisé ni approuvé** par le projet officiel NewPipe, TeamNewPipe ou NewPipe e.V.

NewPipe Material est basé sur NewPipe et conserve la licence libre de NewPipe, les crédits du projet amont et les avis de licences tierces.

---

## Qu’est-ce que NewPipe Material ?

NewPipe Material conserve l’expérience principale de NewPipe tout en modernisant l’identité et l’interface de l’application.

Objectifs actuels du fork :

- Surfaces, dialogues, paramètres, onglets et navigation inspirés de Material 3
- Prise en charge des couleurs dynamiques Material You lorsque disponible
- Couleurs de thème manuelles : défaut de l’app, neutre, vert, bleu, violet, orange, rose et rouge
- Nouvelle identité : **NewPipe Material**
- Identifiant d’application séparé : `org.wisso.newpipematerial`
- Les builds de débogage s’installent séparément sous `org.wisso.newpipematerial.debug`
- Préserver le comportement de NewPipe, la compatibilité import/export et les services pris en charge

Ce fork évite les changements risqués dans les zones sensibles comme la lecture, les téléchargements, la lecture en arrière-plan, le lecteur flottant et la logique Extractor, sauf lorsqu’ils sont traités comme des changements dédiés et testés.

---

## Captures d’écran

### Téléphone

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="Capture téléphone 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="Capture téléphone 2"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="Capture téléphone 3"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="Capture téléphone 4"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="Capture téléphone 5"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="Capture téléphone 6"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="Capture téléphone 7"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="Capture téléphone 8"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="Capture téléphone 9"></a>
</p>

### Tablette

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="Capture tablette 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="Capture tablette 2"></a>
</p>

---

## Services pris en charge

NewPipe Material hérite de la prise en charge NewPipe pour YouTube, YouTube Music, PeerTube, Bandcamp, SoundCloud et media.ccc.de.

La prise en charge dépend du code amont de NewPipe et NewPipe Extractor.

---

## Fonctionnalités

NewPipe Material conserve les fonctionnalités familières de NewPipe : vidéos et directs, lecture en arrière-plan, lecteur flottant, playlists locales, abonnements sans compte, groupes de chaînes, recherche, détails vidéo, téléchargements et import/export des données.

Ajouts Material : rôles de couleur Material 3, navigation inférieure pour cinq onglets ou moins, couleurs dynamiques/manuelles, attribution du fork dans À propos et prise en charge de la signature de versions.

---

## Installation

Installez NewPipe Material depuis les releases GitHub de ce dépôt ou depuis les artefacts signés lorsqu’ils sont disponibles.

```text
NewPipe officiel : org.schabi.newpipe / net.newpipe.app selon le build amont
NewPipe Material : org.wisso.newpipematerial
Debug :            org.wisso.newpipematerial.debug
```

Pour migrer les données, exportez la base de données depuis NewPipe officiel via Settings > Backup and Restore, installez NewPipe Material, puis importez cette sauvegarde. Gardez toujours une sauvegarde.

Ne publiez pas NewPipe Material, NewPipe ou ses forks sur Google Play.

---

## Compilation

Exigences : JDK 21, Android SDK et le wrapper Gradle du dépôt.

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
./gradlew assembleDebug -DskipFormatKtlint
```

Le build debug utilise **NewPipe Material Debug** et le paquet `org.wisso.newpipematerial.debug`.

---

## Signature de version

Variables d’environnement :

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

Vérification recommandée :

```bash
./gradlew assembleRelease -DskipFormatKtlint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## État du développement

En cours ou terminé : identité d’application, séparation debug/release, couleurs Material 3, couleurs dynamiques/manuelles, navigation inférieure, écran À propos, dialogues, snackbars, paramètres, détails vidéo, interface de téléchargement et workflow de signature.

Différé ou risqué : overlay principal du lecteur, couleurs de seekbar/gestes, file d’attente, menus qualité/audio/sous-titres et changements larges de lecture/téléchargement.

---

## Contribuer

Les contributions sont bienvenues : corrections, QA, documentation, préparation de release et polissage Material 3 ciblé. Gardez les changements ciblés et testables, avec captures avant/après pour l’UI lorsque possible.

---

## NewPipe amont

- Dépôt NewPipe : https://github.com/TeamNewPipe/NewPipe
- Site NewPipe : https://newpipe.net
- FAQ : https://newpipe.net/FAQ/
- NewPipe Extractor : https://github.com/TeamNewPipe/NewPipeExtractor

Les problèmes propres au fork appartiennent à ce dépôt. Les problèmes de services ou d’Extractor peuvent aussi devoir être comparés à NewPipe officiel.

---

## Dons

Pour soutenir NewPipe amont : https://newpipe.net/donate

NewPipe Material est un fork indépendant ; les dons amont vont au projet NewPipe amont, pas automatiquement à ce fork.

---

## Licence

NewPipe Material est un logiciel libre basé sur NewPipe, distribué sous la GNU General Public License version 3 ou ultérieure. Consultez les fichiers de licence et l’écran de licences intégré pour les détails complets.
