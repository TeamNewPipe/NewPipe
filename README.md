<p align="center"><img src="assets/merak-icon.png" width="128" alt="Merak icon: a white tapan drum on a red disc"></p>

<h1 align="center">Merak</h1>

<p align="center"><b>A free Android app for music and videos, with search inside your playlists and subscriptions.</b></p>

<p align="center">
  <a href="../../releases/latest">Download</a> ·
  <a href="MERAK.md">Install guide</a> ·
  <a href="MERAK.md#privacy">Privacy</a> ·
  <a href="../../issues">Report a problem</a>
</p>

> **Unofficial app.** Merak is derived from NewPipe and is not affiliated with or endorsed by TeamNewPipe or NewPipe e.V. "NewPipe" is a registered word mark of NewPipe e.V., used here only to credit Merak's origin.

## What Merak is

Merak lets you stream music and videos on Android with no account and no Google services. Its own addition is **title search** on four screens, so long collections stay easy to browse:

| Screen | Search by |
|---|---|
| Playlists tab | Playlist name |
| A local playlist | Song or video title |
| An online playlist | Song or video title, loading more pages until matches appear |
| Subscriptions | Channel name |

Search ignores capital letters and accents and takes words in any order. Its labels appear in English, Spanish, Macedonian, and Serbian.

**The name.** *Merak* is a word shared across the Balkans and Turkey for the joy of savoring life's simple pleasures: good music, good company, time well spent. The icon is an original drawing of a *tapan*, the double-headed drum heard at Balkan celebrations, in the red of [makedonskakafana.mk](https://makedonskakafana.mk).

## Where Merak comes from

Merak is built on **[NewPipe](https://github.com/TeamNewPipe/NewPipe)**, the free streaming app created and maintained by TeamNewPipe and its volunteer contributors. Everything Merak plays, downloads, and stores comes from NewPipe's code. Merak's changes sit on top:

- **Base:** NewPipe 0.29.1, `dev` branch at commit `acb7dfd`.
- **Added:** title search, the Merak name and icon, and Merak's own support links.
- **Updates:** a scheduled check every three months merges each new official NewPipe release into Merak.

To thank the people behind NewPipe, [donate to the NewPipe project](https://newpipe.net/donate/).

## How Merak complies

| Obligation | Source | How Merak meets it |
|---|---|---|
| Keep the license | GNU GPL v3 | Merak stays under GPL v3 or later; the full source is public here, and every change is dated in [the changelog](MERAK.md#changelog) and commit history |
| Use its own name and look | [NewPipe trademark policy](https://newpipe-ev.de/policy/trademark/) | Own name, original icon, and app ID `io.github.ninjaeu.merak`; "NewPipe" appears only to credit the origin |
| State that it is unofficial | NewPipe trademark policy | This README, the guide, the app's About screen, and every release carry the disclaimer |
| Route support to Merak | Courtesy to TeamNewPipe | Crash reports and links lead to this repository; please keep Merak issues out of NewPipe's tracker |
| Distribute responsibly | NewPipe README | Merak ships only through this repository's Releases, and never through Google Play |

## Install

1. Download the newest `.apk` from [Releases](../../releases/latest).
2. Open it on your Android phone (Android 6.0 or newer) and allow the install.

The [install guide](MERAK.md) covers Samsung One UI settings, moving data from another NewPipe-based app, updates, and Google's developer-verification rule.

## License and credits

- Merak is free software under the [GNU General Public License v3.0](LICENSE) or later.
- NewPipe © TeamNewPipe and contributors, GPL v3.
- Merak's search feature, name, and drum icon © 2026 ninjaeu, GPL v3.
