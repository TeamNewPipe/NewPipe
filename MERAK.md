# Merak

> **Unofficial app.** Merak is based on [NewPipe](https://github.com/TeamNewPipe/NewPipe) and modified by ninjaeu. Merak is not affiliated with or endorsed by TeamNewPipe or NewPipe e.V., and TeamNewPipe does not build or support it. Merak is a separate app from official NewPipe releases: it installs alongside them and keeps its own data. "NewPipe" is a registered word mark of NewPipe e.V.

Merak adds title search to four screens. *Merak* is a Balkan word for the joy of savoring life's simple pleasures. App version: `0.29.1-merak.2.0.1` (based on NewPipe 0.29.1).

| Screen | Search matches | Toolbar label |
|---|---|---|
| Playlists tab (local and saved online playlists) | Playlist name | Search playlists |
| Local playlist | Stream title | Search in playlist |
| Online playlist (for example, a YouTube playlist) | Stream title | Search in playlist |
| Subscriptions | Channel name | Search subscriptions |

Search ignores case and accents and accepts words in any order. Labels appear in English, Spanish, Macedonian, and Serbian.

## Get the APK

1. Open this repository's **Releases** section and select the newest **Merak** release.
2. Under **Assets**, download the `.apk` file, for example `Merak-2.0.1.apk`. No GitHub sign-in is needed.

Every push to the `title-search` branch builds the app and publishes the release.

## Install on a Samsung phone

Merak supports Android 6.0 and newer. Every One UI version runs Android 9 or newer, so every One UI phone qualifies. To check your version, open **Settings > About phone > Software information > One UI version**.

| One UI | Android | Extra step |
|---|---|---|
| 1 to 5 | 9 to 13 | None |
| 6 to 8 | 14 to 16 | Turn off Auto Blocker during the install |

1. **Get the APK onto the phone.** Open the release on the phone and tap the APK, or copy it from a computer.
2. **One UI 6 and newer:** open **Settings > Security and privacy > Auto Blocker** and turn it off. Turn it back on after step 4.
3. **Open the APK** from the browser's downloads or **My Files**. When Android asks, select **Settings**, turn on **Allow from this source**, and go back.
4. **Select Install.** If Google Play Protect warns about an unknown app, select **More details > Install anyway**. If the install stalls, pause **Play Store > profile picture > Play Protect > gear icon > Scan apps with Play Protect**, install, and turn it back on.

## Move your data into Merak

Merak 2.0.0 has a new app ID, `io.github.ninjaeu.merak`, so it installs as a new app.

1. In your current app (NewPipe_Search or NewPipe), open **Settings > Backup and restore > Export database**.
2. Install Merak, open the same screen, and select **Import database**.
3. Check your subscriptions and playlists in Merak, then uninstall NewPipe_Search.

## Updates

- Every Merak release uses the same permanent signing key, so new releases install on top and keep your data.
- [Obtainium](https://github.com/ImranR98/Obtainium) can watch this repository and offer each new release.
- A scheduled task checks every three months for a new official NewPipe release, merges it, and publishes a new Merak release.

**Google developer verification:** Google now requires developer verification for apps installed outside the Play Store on certified devices. It began in Brazil, Indonesia, Singapore, and Thailand on September 30, 2026, and Google plans to extend it worldwide in 2027. On affected devices, installing or updating Merak may require Google's "advanced" install steps, including a 24-hour wait.

## Privacy

- Merak sends no data to its developer.
- Merak talks directly to the services you use (for example YouTube, SoundCloud, or PeerTube), just as NewPipe does. Those services see your requests under their own privacy policies.
- After a crash, Merak offers to open a GitHub issue in this repository. A report leaves your phone only when you write and submit that issue yourself; review it before posting, because GitHub issues are public.
- Merak keeps your subscriptions, playlists, history, and settings on your phone. **Export database** writes them to a file you control.

## Report a problem

Open an issue in this repository's **Issues** tab. Please keep Merak reports out of TeamNewPipe's tracker; the NewPipe team supports official NewPipe only.

## One UI compatibility

These points are confirmed in the code. Phone testing covers the search feature on One UI.

| One UI behavior | How the app handles it |
|---|---|
| Edge-to-edge screens (One UI 7 and 8, Android 15 and 16) | The toolbar and lists stay clear of the status and navigation bars |
| Back gesture and Back button | Back closes an open search first, then leaves the screen |
| Samsung Keyboard in landscape | The search field keeps the list visible behind the keyboard |
| Light, dark, and black themes | The search text follows the toolbar colors |
| Pop-up view, split screen, and DeX | The app resizes to any window size |

## Test steps

1. **Playlists:** open the Playlists tab, tap the filter icon, and type part of a playlist name. Close the search; every playlist returns in its saved order.
2. **Local playlist:** search a word from one title, then tap **Play all**. The whole playlist plays, in order.
3. **Online playlist:** search a word near the end of a long YouTube playlist. More pages load until matches appear, or "No results" shows.
4. **Subscriptions:** search part of a channel name, then a nonsense word. "No results" appears for the second.
5. **Reorder lock:** during a search, drag handles do nothing.
6. **Rotation:** rotate the phone during a search. The text and results stay.
7. **Back:** with the search open, press Back once. The search closes and the screen stays.

## Signing

Every release is signed with one permanent key, stored as the encrypted repository secret `DEBUG_KEYSTORE_BASE64`. Its certificate SHA-256 fingerprint is `cfabd9cd07e7e0e3fa14f35ce7d2c6137402d40c0bccdbb3a20c67df02d4763c`; each build log prints it for comparison.

## Undo

Uninstall **Merak**. Official NewPipe and its data stay untouched.

## Credits and license

- Merak builds on NewPipe by TeamNewPipe and its contributors. To support them, donate at https://newpipe.net/donate/.
- Merak's search feature and drum icon (an original design) are by ninjaeu. The icon red (#BA1200) follows makedonskakafana.mk.
- Merak is free software under the GNU General Public License v3.0 or later, the same license as NewPipe. The full source is in this repository.

## Changelog

### 2.0.1 (2026-11-14)

- Round red icon: the drum sits on a red disc (#BA1200, the accent red of makedonskakafana.mk), so every launcher shape shows a circle.
- Added a drum-only monochrome layer for Android themed icons.
- Splash screen background uses the same red.

### 2.0.0 (2026-11-14)

- Renamed the app **Merak**, with its own app ID `io.github.ninjaeu.merak`, to comply with NewPipe's [trademark policy](https://newpipe-ev.de/policy/trademark/). Install once and import your data; later releases install on top.
- New original icon: a Balkan tapan drum with crossed drumsticks.
- Replaced "NewPipe" with "Merak" wherever the app names itself, in every language. The About screen credits NewPipe and states that Merak is unofficial.
- Crash reports open this repository's GitHub issues; the e-mail option to the NewPipe team is gone.
- Website, FAQ, and privacy links point to this repository. The donation link still supports the NewPipe project.
- New download folder name `Merak` and backup file name `MerakData-<date>.zip`.
- The Google developer-verification notice now states the rule's regions and dates.
- Releases and APK files follow "Merak 2.0.0"; releases under earlier NewPipe-based names are removed.

### 1.0.3 (2026-11-13)

- Signed with a permanent key stored as the repository secret `DEBUG_KEYSTORE_BASE64`.
- The build log prints the signing certificate fingerprint.

### 1.0.2 (2026-11-13)

- Renamed the app NewPipe_Search (replaced in 2.0.0).
- Builds run one at a time, so every build signs with the same saved key.

### 1.0.1 (2026-11-13)

- Fixed the build: the desktop module rejected a non-numeric version name.
- Added this guide and publishing under Releases.

### 1.0.0 (2026-11-13)

- Added toolbar title search to the Playlists tab, local playlists, online playlists, and Subscriptions.
- Added accent- and case-insensitive matching that requires every query word, with unit tests.
- Filtering changes only what the list shows. Saving order, deletes, and play queues use the full list.
- Drag reordering pauses during a search.
- Online playlists load more pages during a search until 30 matches show or the playlist ends.
- Back closes an open search first.
