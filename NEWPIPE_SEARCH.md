# NewPipe_Search

NewPipe_Search adds title search to four NewPipe screens. It builds on TeamNewPipe/NewPipe `dev` at commit `acb7dfd` (NewPipe 0.29.1). App version: `0.29.1-search.1.0.2`.

| Screen | Search matches | Toolbar label |
|---|---|---|
| Playlists tab (local and saved online playlists) | Playlist name | Search playlists |
| Local playlist | Stream title | Search in playlist |
| Online playlist (for example, a YouTube playlist) | Stream title | Search in playlist |
| Subscriptions | Channel name | Search subscriptions |

Search ignores case and accents and accepts words in any order. Labels appear in English, Spanish, Macedonian, and Serbian.

## Get the APK

1. Open this repository's **Releases** section (right column of the front page) and select the newest release, for example **NewPipe_Search 1.0.2**.
2. Under **Assets**, download the file ending in `.apk`, for example `NewPipe_Search-1.0.2.apk`. No GitHub sign-in is needed.

Every push to the `title-search` branch builds the app and publishes the release. You can also start a build from **Actions > Build NewPipe_Search APK > Run workflow**.

## Install on a Samsung phone

The app supports Android 6.0 and newer. Every One UI version runs Android 9 or newer, so every One UI phone qualifies. To check your version, open **Settings > About phone > Software information > One UI version**.

| One UI | Android | Extra step |
|---|---|---|
| 1 to 5 | 9 to 13 | None |
| 6 to 8 | 14 to 16 | Turn off Auto Blocker during the install |

1. **Get the APK onto the phone.** Open the release on the phone and tap the APK, or copy it from a computer by USB cable, Google Drive, or OneDrive.
2. **One UI 6 and newer:** open **Settings > Security and privacy > Auto Blocker** and turn it off. It blocks installs from files. Turn it back on after step 4.
3. **Open the APK** from the browser's downloads or **My Files**. When Android asks, select **Settings**, turn on **Allow from this source**, and go back.
4. **Select Install.** If Google Play Protect warns about an unknown app, select **More details > Install anyway**.

The app installs as **NewPipe_Search**, next to any official NewPipe, and keeps its own data. To move subscriptions and playlists:

1. In the official NewPipe, open **Settings > Backup and restore > Export database**.
2. In NewPipe_Search, open the same screen and select **Import database**.

## One UI compatibility

These points are confirmed in the code. Phone testing is still pending.

| One UI behavior | How the app handles it |
|---|---|
| Edge-to-edge screens (One UI 7 and 8, Android 15 and 16) | The app keeps its toolbar and lists clear of the status and navigation bars |
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

## Updates and signing

Builds reuse one signing key while GitHub keeps its cache, which expires 7 days after the last build. A new build inside that window installs as an update. After it expires, export the database, uninstall, and install the new build. A permanent key takes one repository secret, `DEBUG_KEYSTORE_BASE64`.

## Undo

Uninstall **NewPipe_Search**. The official NewPipe and its data stay untouched.

## Changelog

### 1.0.2 (2026-11-13)

- Renamed the app to NewPipe_Search. Releases and APK files follow the pattern "NewPipe_Search 1.0.2".
- Removed the release published under the earlier name.
- Builds now run one at a time, so every build signs with the same saved key.

### 1.0.1 (2026-11-13)

- Fixed the build: the desktop module rejected the version name `0.29.1-search.1.0.0`. The shared version name returns to `0.29.1`, and the Android app alone adds `-search.1.0.1`.
- Added this guide, with install steps by One UI version.
- Each successful build now publishes the APK under **Releases**, tagged by version (for example, `v0.29.1-search.1.0.1`).

### 1.0.0 (2026-11-13)

- Added toolbar title search to the Playlists tab, local playlists, online playlists, and Subscriptions.
- Added accent- and case-insensitive matching that requires every query word, with unit tests.
- Filtering changes only what the list shows. Saving order, deletes, and play queues use the full list.
- Drag reordering pauses during a search.
- Online playlists load more pages during a search until 30 matches show or the playlist ends.
- Back closes an open search first.
- Added the GitHub workflow that builds the APK.

## License

NewPipe is free software under the GNU General Public License v3.0 or later. This change uses the same license.
