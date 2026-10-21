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

*Leggi questo documento in altre lingue: [Deutsch](doc/README.de.md),
[English](README.md), [Español](doc/README.es.md), [Français](doc/README.fr.md),
[हिन्दी](doc/README.hi.md), [Italiano](doc/README.it.md),
[한국어](doc/README.ko.md), [Português Brasil](doc/README.pt_BR.md),
[Polski](doc/README.pl.md), [ਪੰਜਾਬੀ ](doc/README.pa.md),
[日本語](doc/README.ja.md), [Română](doc/README.ro.md),
[Soomaali](doc/README.so.md), [Türkçe](doc/README.tr.md),
[正體中文](doc/README.zh_TW.md), [অসমীয়া](doc/README.asm.md),
[うちなーぐち](doc/README.sr.md), [Српски](README.ar.md) , [العربية]{19}*

> [!warning]\
> <b>QUEST'APP È IN BETA, QUINDI POTRESTI IMBATTERTI IN BUG. SE COSÌ FOSSE, APRI
> UNA SEGNALAZIONE SUL NOSTRO REPOSITORY SU GITHUB COMPILANDO IL TEMPLATE DEGLI
> ERRORI.</b>
> 
> <b>METTERE NEWPIPE O QUALSIASI SUA {1}FORK SU GOOGLE PLAY STORE VIOLA I LORO
> TERMINI E CONDIZIONI.</b>
> 
> <b>"NewPipe" È UN MARCHIO REGISTRATO SOGGETTO ALLA [SUA POLITICA SUI
> MARCHI](https://newpipe-ev.de/policy/trademark/). LE APPLICAZIONI CHE
> INFRANGONO TALI REGOLE NON SARANNO TOLLERATE.</b>

## Screenshot

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

### Servizi supportati

NewPipe supporta, al momento, questi servizi:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([sito](https://www.youtube.com/)) e YouTube Music
  ([sito](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([sito](https://joinpeertube.org/)) e tutte le sue istanze (apri il
  sito per capire cosa significa!)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([sito](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([sito](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([sito](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Come puoi vedere, NewPipe supporta molteplici servizi audio e video. Sebbene
tutto ciò sia iniziato con YouTube, altre persone hanno aggiunto più siti col
passare degli anni, rendendo NewPipe sempre più versatile!

Parzialmente per via delle circostanze e parzialmente per via della sua
popolarità, YouTube è servizio maggiormente supportato. Se usi o hai familiarità
con qualcuno di questi altri servizi, per favore aiutaci a migliorare il loro
supporto! Stiamo cercando addetti a SoundCloud e PeerTube.

Se vuoi aggiungere un nuovo servizio, per favore, contattaci prima! La nostra
[documentazione in lingua
inglese]](https://teamnewpipe.github.io/documentation/) fornisce più
informazioni su come un nuovo servizio può essere aggiunto all'app e a [NewPipe
Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Descrizione

NewPipe funziona prendendo i dati dalle API ufficiali (ad es. PeerTube) del
servizio che si sta usando. Se le API ufficiali hanno delle restrinzioni (ad es.
YouTube) per i nostri scopi, o sono proprietarie, l'app invece analizza il sito
o usa delle API interne. Questo significa che non hai bisogno di un account su
nessun servizio per usare NewPipe.

Inoltre, siccome sono programmi liberi e open source, né l'app né l'estrattore
usano alcuna libreria o framework proprietario, come Google Play Services.
Questo vuol dire che puoi usare NewPipe su dispositivi o ROM personalizzate che
non hanno le app Google installate.

### Funzioni

* Guarda i video a risoluzioni fino a 4K
* Ascolta audio in sottofondo, caricando solo l'audio per risparmiare dati
* Modalità popup (lettore fluttuante, anche noto come Picture-in-Picture)
* Guarda dirette
* Mostra/nascondi sottotitoli
* Cerca video e audio (su YouTube puoi anche specificare la lingua dei
  contenuti)
* Accoda video (e salvali facoltativamente come playlist locali)
* Mostra/nascondi informazioni sui video (come descrizione e tag)
* Mostra/nascondi prossimi video o video correlati
* Mostra/nascondi commenti
* Cerca video, audio, canali, playlist e album
* Esplora video e audio in un canale
* Iscriviti a canali (sì, senza accedere ad alcun account!)
* Ricevi notifiche su video nuovi di canali a cui sei iscritto
* Crea e modifica gruppi di canali (per navigazione e gestione facili)
* Esplora feed di video generati dai tuoi gruppi di canali
* Visualizza e ricerca nella tua cronologia
* Cerca e guarda playlist (queste sono playlist remote, ovvero sono prese dal
  servizio che stai esplorando)
* Crea e modifica playlist locali (queste sono create e salvate nell'app, e non
  hanno niente a che fare con alcun servizio)
* Scarica video/audio/sottotitoli (closed caption)
* Apri in Kodi
* Guarda o blocca materiale soggetto a limiti di età

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Installazione e aggiornamenti
Puoi installare NewPipe usando uno dei seguenti metodi:
 1. Aggiungi il nostro repo a F-Droid e installalo da lì. Le istruzioni sono
    qui: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. Scarica l'APK da [GitHub
    Releases](https://github.com/TeamNewPipe/NewPipe/releases) e installalo.
 3. Aggiorna tramite F-Droid. Questo è il metodo più lento di ricevere
    aggiornamenti, perché F-Droid deve riconoscere i cambiamenti, assemblare
    l'APK da solo, firmarlo, e poi distribuire l'aggiornamento agli utenti.
 4. Assemblare un APK di debug da solo. Questo è il metodo più veloce di
    ottenere nuove funzioni sul tuo dispositivo, ma è molto più complicato,
    quindi consigliamo usare uno degli altri metodi.
 5. Se sei interessato in una specifica funzione o un bugfix fornito in una Pull
    Request in questo repo, puoi anche scaricare il suo APK da dentro la PR.
    Leggi la descrizione della PR per le istruzioni. La cosa bella degli APK
    specifici delle PR è che sono installati affianco all'app ufficiale, quindi
    non devi preoccuparti di perdere dati o fare qualche pasticcio.

Consigliamo il metodo 1 per la maggior parte degli utenti. Gli APK installati
usando il metodo 1 o 2 sono compatibili gli uni con gli altri (quindi, se hai
installato NewPipe usando o il metodo 1 o il metodo 2, puoi anche aggiornare
NewPipe usando l'altro), ma non con quelli installati usando il metodo 3. Questo
è per via del fatto che la stessa chiave di firma (la nostra) è usata sia per il
1° che per il 2°, ma un'altra chiave di firma (quella di F-Droid) è usata per il
3°. Assemblare un APK di debug usando il metodo 4 esclude una chiave
interamente. Le chiavi di firma aiutano ad assicurarsi che l'utente non sia
spinto a installare un aggiornamento malevolo ad un'app. Quando si usa il metodo
5, ogni APK è firmato con una diversa chiave random fornita da GitHub Actions,
quindi non puoi neanche aggiornarlo. Dovrai fare il backup e recuperare i dati
dell'app ogni volta che desideri usare un nuovo APK.

Nel frattempo, se vuoi cambiare fonte per la stessa ragione (ad es. la
funzionalità essenziale di NewPipe si rompe e F-Droid non ha ancora l'ultimo
aggiornamento), consigliamo di seguire questa procedura:
1. Fai il backup dei tuoi dati tramite Impostazioni > Contenuti > Esporta
   Database così puoi mantenere la tua cronologia, le tue iscrizioni, e le tue
   playlist
2. Disinstalla NewPipe
3. Scarica l'APK dalla nuova fonte e installalo
4. Importa i dati del passo 1 da Impostazioni > Contenuti > Importa Database

> [!Note] Quando stai importando un database nell'app ufficiale, assicurati
> sempre che sia quello che hai esportato _ dall_'app ufficiale. Se importi un
> database esportato da un APK diverso dall'app ufficiale, può funzionare male.
> Tale azione non è supportata, e dovresti farla solo quando assolutamente
> sicuro di sapere che cosa stai facendo.

### Info APK

Questa è l'impronta SHA della chiave di firma di NewPipe per verificare APK
scaricati che sono firmati da noi. L'impronta digitale è disponibile anche sul
[sito di NewPipe](https://newpipe.net#download). Ciò è rilevante per il metodo
2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Contribuire
Se hai idee, traduzioni, cambiamenti di design, pulizia di codice, o addirittura
grossi cambiamenti di codice, l'aiuto è sempre apprezzato. L'app diventa sempre
meglio con ogni contribuzione, non importa quanto grande o piccola essa sia! Se
ti piacerebbe essere parte del progetto, vedi le nostre [note di
contribuzione](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Donare
Se ti piace NewPipe, le donazioni sono benvenute. Preferiamo Liberapay, siccome
è sia open-source che senza fini di lucro. Per ulteriori informazioni sulle
donazioni a NewPipe, per favore, visita il nostro
[sito](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Informativa sulla privacy

Il progetto NewPipe mira a fornire un'esperienza privata e anonima per usare
servizi multimediali basati sul web. Di conseguenza, l'app non raccoglie alcun
dato senza il tuo consenso. L'informativa sulla privacy di NewPipe spiega nel
dettaglio quali dati sono inviati e memorizzati quando invii una segnalazione di
crash, o lasci un commento nel nostro blog. Puoi trovare il documento
[qui](https://newpipe.net/legal/privacy/).

## Licenza
[![Immagine GNU
GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe è software libero: puoi usarlo, studiarlo, condividerlo e migliorarlo a
tuo piacimento. In particolare, puoi ridistribuirlo e/o modificarlo sotto i
termini della [GNU General Public
License](https://www.gnu.org/licenses/gpl.html) come pubblicata dalla Free
Software Foundation o la versione 3 della licenza o, a tua scelta, qualsiasi
versione successiva.
