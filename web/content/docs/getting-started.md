WizeStream is an independent Android streaming client based on NewPipe. You can search, watch, listen, subscribe and build playlists without signing in to a media-platform account. Your subscriptions and library live in the app rather than in a YouTube account.

## Choose your installation

WizeStream requires **Android 6.0 or newer**. The normal release APK supports ARM64 and ARMv7 phones and tablets. The separate **x86_64** APK is intended for 64-bit Intel or AMD Android environments, including Waydroid and Android-x86.

| Channel | Best suited to | Important detail |
| --- | --- | --- |
| GitHub releases | Installing a signed stable APK directly | Choose the APK for your Android architecture. |
| IzzyOnDroid | Updates through an F-Droid-compatible client | Enable the IzzyOnDroid repository in your chosen client. |
| GitHub Store | Browsing GitHub-distributed apps | Use the WizeStream project listing linked from Downloads. |
| Nightly | Testing recent development changes | Separate installation; changes can be unstable. |

Use the [download page](../downloads.html) for project links. There is no native Linux build inside the Android x86_64 package: Waydroid runs the Android app. A separate WizeStream Desktop project is linked there.

## Install and open

1. Obtain the APK from the project’s release page or install through its IzzyOnDroid listing.
2. If installing an APK directly, Android may ask you to allow installations from the browser or file manager handling that APK.
3. Open WizeStream. Select the service you want to browse and search for a public video, channel or playlist.
4. Open an item to inspect its details and choose normal, background or popup playback where available.
5. Subscribe to channels you want to follow, then open **What’s New** to build your local feed.

No Google sign-in or Google Play Services is required for the app’s normal account-free functions. Service availability and supported formats can change independently of WizeStream.

## Set up your library

Start with a few subscriptions and a local playlist. Channel groups help separate interests without creating more accounts. Local profiles provide a stronger separation between libraries, such as Personal and Study.

Before downloading, choose destination folders under **Settings → Download**. Grant access only to the folders or media the feature needs. Downloaded media occupies device storage and is separate from your subscription database.

If you already use NewPipe, keep an exported backup from that app. Import compatible data through **Settings → Backup and restore**, then check subscriptions and playlists before discarding the original. The [migration guide](backups-migration.html) explains the supported formats.

## Stable, development and nightly

See the [latest stable release](https://github.com/wizdom13/WizeStream/releases/latest) for the current APKs and release notes. The handbook may cover changes newer than your installed build.

Stable, debug and nightly builds use separate application identities. Normal WizeStream uses `org.wisso.newpipematerial`; keeping that historical package name preserves updates and existing data after the rename from NewPipe Material.

## Keep updates straightforward

Prefer the same update source you used for installation. A signature mismatch usually means the installed app and the incoming APK were signed differently. Do not remove an installation containing important local data just to bypass an update error: export a backup and investigate the source first.

Continue with [playback](playback.html), [subscriptions](subscriptions.html) or [downloads](downloads.html). For a problem, use the [troubleshooting guide](troubleshooting.html).
