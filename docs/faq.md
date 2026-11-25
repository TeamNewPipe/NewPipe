# WizeStream FAQ

This FAQ was reviewed against **WizeStream 1.52.2 (25 November 2026)** and `pipe` at
[`983d8acb`](https://github.com/wizdom13/WizeStream/tree/983d8acb0cdd934a2a0fbe747020bb988b2b52b0),
the commit tagged for that release. See the [changelog](changelogs.md) for version-specific availability.

## What is WizeStream?

WizeStream is an independent, NewPipe-based streaming client for Android. It combines a modern
Material 3 Expressive interface with privacy-friendly playback, useful local-first features, and
support for multiple streaming platforms. Material 3 is an important design goal, not the
project's only purpose.

## Is WizeStream an official NewPipe release?

No. WizeStream is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe, or NewPipe e.V. Upstream attribution and license notices are preserved in the app and repository.

## Why does the package ID still contain `newpipematerial`?

The stable application ID remains `org.wisso.newpipematerial` so existing WizeStream installations can receive updates without losing app data. Changing it would create a separate Android application. Internal upstream namespaces, database identities, preference keys, and notification-channel IDs are also retained where changing them would break compatibility.

## Can WizeStream and official NewPipe be installed together?

Yes. They use different Android application IDs. WizeStream Nightly also has a separate application ID and can be installed alongside the stable build.

## Can I import an existing NewPipe backup?

WizeStream supports compatible NewPipe backup data. Export from the source app, open WizeStream's **Settings > Backup and restore**, and select the exported ZIP. Keep a copy of the original backup until you have verified the imported data.

## Can I like videos without a service account?

Builds containing local likes show **Like locally** on video details. Tap it to save the video in
that profile's **Liked videos** playlist, or tap **Liked locally** to remove it. The adjacent
**Liked videos** button opens the playlist; it is also available with your other local playlists.
Removing a video from the playlist removes its local like. Local likes do not submit a vote or
change the service's public like count.

Each profile has its own liked collection. Use a full WizeStream backup and restore its database
and settings together to retain both the videos and the profile's playlist link. Renaming the
playlist keeps the link; deleting it clears that collection, and a later like creates a new one.
A synced or imported ordinary playlist does not automatically become another device's local liked
collection.

## How does device synchronization work?

Open **Settings > Device synchronization** on two WizeStream devices, show the one-time QR code on
one device, and scan it with the other. Paired devices can synchronize directly over the same Wi-Fi
or Ethernet network, either manually or approximately once per hour in the background.

**Copy pairing code** and **Enter pairing code** offer a camera-free path.
For Waydroid, copy the phone's complete code into Waydroid while the phone's pairing screen stays
open, then start **Sync now** in Waydroid. The code still expires after five minutes and can be used
only once. This does not bypass local-network reachability requirements. See the
[device synchronization guide](device-synchronization.md) for the steps and diagnostic controls.

## What data is synchronized?

Device synchronization supports subscriptions, feed groups, playlists, watch history, playback
progress, home tabs, content-filter selections, per-channel playback profiles, allowlisted settings,
and completed-download metadata. Search history is excluded by default and is exchanged only when
it is enabled on both devices.

## Are downloaded media files transferred between devices?

No. WizeStream synchronizes completed-download metadata, not video or audio files. A missing item is
shown as **Not local** and can be fetched from its original source with **Download on this device**.

## How should I update WizeStream?

Use the same source from which you installed it whenever possible. IzzyOnDroid users should update through their F-Droid-compatible client. Direct installations can use signed WizeStream GitHub releases or the in-app GitHub release checker.

## Which services are supported?

Service support comes from the extractor source integrated into each WizeStream build. See the
main [README](../README.md#supported-services) for the current list.

## Can I add a service plugin?

Since 1.52.0, **Settings > Plugins** supports signed native WizeStream API 2.0 packages and
compatible PipePipe API 1.0/1.1 packages. External plugins need Android 7/API 24 or newer;
WizeStream and its built-in services still support Android 6/API 23. Enable plugins only for
publishers you trust: their code runs in the app process with its permissions.

The WizeStream Plugins catalog is included by default. Packages and repositories belong to the
device; plugin preferences and optional login cookies are separated by profile. Backups and
Device Sync do not transfer plugin packages or configuration. See the [plugin guide](plugins.md)
for installation, restarts, compatibility and the native Kotlin template.

## How do I use Vimeo?

The Vimeo features described here are included in the reviewed **1.52.2** release.

Select Vimeo, then browse **Staff Picks** or choose **Open link** to paste a Vimeo video, creator,
channel, showcase/album or live-event URL. Android's Share action can also send a supported Vimeo
link to WizeStream. Public creator uploads and thematic channels load additional pages as you scroll.
Public showcases, including legacy album links and custom showcase names, open as playlists.

Public videos and shared unlisted video links play when Vimeo exposes an unencrypted stream.
Keep the complete unlisted URL, including its access hash. Available captions use the native
player. Downloads appear only when Vimeo provides a progressive video file; videos offering
only adaptive streams can be played but cannot be downloaded by this integration.

Public live-event links open the broadcast or recording that Vimeo currently selects. Upcoming
events have no playable stream until the broadcaster starts. Recurring event URLs retain their
event identity; explicit archive links open only the recording advertised for that event.

**Optional global search and comments:** Vimeo requires an API token even for public API data.
The standard **Search Vimeo** action stays available while filtering tabs such as **What's New**.
If API access is not configured, it offers **Open Vimeo settings** and keeps your query while you
set up access. Canceling setup leaves the local filter intact. **Open link** in the same prompt
lets you enter a supported public URL without setting up API access.

To enable keyword search and reading public video comments/replies:

1. Follow [Vimeo's official token instructions](https://help.vimeo.com/hc/en-us/articles/12427789081745-How-to-generate-a-personal-access-token)
   to create an API app in your Vimeo developer account.
2. Generate an **Unauthenticated** token for public data only. WizeStream accepts only the `public`
   scope and rejects tokens granting private data, editing, uploading or other account permissions.
3. Open **Settings > Content > Vimeo > Set public API token**, paste the token and choose **Verify and save**.
4. Return to Vimeo. A search started before setup continues with its original query, and public
   videos have a comments tab. Search uses relevance order; comments use Vimeo's chronological
   order. Replies open from their parent comment.

The token stays encrypted on that device and is excluded from backups and Device Sync. Configure
each device separately; **Remove API token** disables the optional API features. Public playback,
showcases and events still work without a token. Large showcases whose public embed omits members
can use API pagination when a token is configured; incomplete lists are never presented as complete.
Vimeo's regional restrictions, API quotas and content permissions still apply.

Account sign-in inside WizeStream, posting or liking comments, live chat, account libraries,
password-protected, private, domain-restricted and DRM-only videos are not supported. Unlisted
video playback preserves its share hash; public API access to its discussion is not guaranteed.

## How do bulk downloads work?

Open a remote or local playlist's menu and choose **Download playlist**, or open the play queue and
choose its download action. WizeStream loads the complete remote playlist when needed, then lets you
choose video or audio once for the batch. Each item uses its default available quality and audio
track. Track-number prefixes are optional, duplicate filenames are made unique automatically, and
entries that already point to local media files are skipped.

Bulk downloading requires valid default video and audio folders. Disable **Ask where to download**
and choose the folders under **Settings > Download** before starting a batch.

## How does content blocking work?

Long-press a video to block that video or its channel. Under **Settings > Content > Blocked
content**, you can enable or disable filtering, add title or content keywords, review blocked videos
and channels, remove individual rules, or clear everything. Keyword rules do not match uploader
names; use **Block channel** when you want to hide all content from a channel. Matching remote
videos, channels, posts, playlists, feeds, search results, and related content are hidden locally.
Blocking does not unsubscribe from channels, delete history, or report anything to a service.

## How do proxy settings work?

Open **Settings > Proxy** and select **HTTP** or **SOCKS5**, then enter the proxy host and port.
The setting applies to new remote browsing, image, playback, download, update, and community-service
connections. Existing connections are closed when the configuration changes.

Localhost, private IP addresses, and local host names bypass the proxy so TV casting and device
synchronization remain reachable on the LAN. HTTPS content stays encrypted in transit, but the proxy
operator can still see destination hosts. Optional HTTP Basic and SOCKS5 username/password
authentication is supported. The password is encrypted with Android Keystore in app-private,
non-backed-up storage and is never included in WizeStream exports. HTTP Basic credentials are not
encrypted between the device and a plain HTTP proxy, so use only a proxy and network you trust. A
failed configured proxy does not silently expose the request through a direct connection.

## What is DeArrow support?

DeArrow is an optional community service that provides descriptive YouTube titles and alternative
thumbnail frames. It is disabled by default. Enable it under **Settings > SponsorBlock > DeArrow**,
where title and thumbnail replacement can be controlled separately. If no accepted contribution is
available or the request fails, WizeStream keeps the original title and thumbnail.

## How do I zoom a video?

Pinch with two fingers over the main player to zoom continuously from 100% to 400%. Once enlarged,
drag with two fingers to move around the video. Pinch back to 100% or tap the Fit/Fill/Zoom control
to reset the custom zoom. A vertical two-finger swipe still changes playback speed when that gesture
is enabled and the video has not already been enlarged. Pinch-to-zoom is enabled by default and can
be turned off under **Settings > Video and audio > Behavior > Pinch to zoom**.

## What is Listen mode and how do I change its visualizer?

Tap the waveform Listen action on a video's details page to continue the same item as
audio-only playback without losing its queue or position. The video is replaced by the selected
visualizer while the normal player controls remain available. Choose among 15 styles under
**Settings > Video and audio > Visualizer style**. The visualizer reads decoded player audio and
does not require microphone permission.

## Why is WizeStream missing from Android Auto?

Android Auto support is available in builds newer than WizeStream 1.8.0. A stable build installed
from a trusted source should appear as a media app. Android Auto hides debug, nightly, CI, and other
sideloaded APKs unless its developer-only **Unknown sources** option is enabled.

Open Android Auto settings, open **About**, tap **Version and permission info** 10 times, and approve
developer mode. Then open the overflow menu, choose **Developer settings**, and enable **Unknown
sources**. Reconnect the vehicle after changing the option. This setting belongs to Android Auto and
is separate from Android's general permission to install unknown apps.

See the [Android Auto guide](android-auto.md) for complete setup and troubleshooting steps.

## How do I cast to a TV?

On Android 8 or newer, open a compatible video's details and select **Cast to TV**. WizeStream
discovers FCast and Chromecast-compatible receivers on the local network, sends the selected media
URL to the receiver, and provides play, pause, stop, and receiver-switching controls. Casting is
separate from Kodi support and does not require Google Play Services. Some streams cannot be cast
when the service does not expose a compatible HLS, DASH, or progressive media URL.

## How does Android picture-in-picture work?

On Android 8 or newer, enable **Android picture-in-picture** under **Settings > Video and audio**.
When a visible video is playing or paused, leaving WizeStream moves it into the system PiP window.
The media session supplies play, pause, previous, and next controls. Returning to WizeStream restores
the prior player layout. The existing popup player remains available separately and continues to be
the supported floating-player option on Android 6 and 7.

## Does WizeStream use NewPipeExtractor or PipePipeExtractor?

WizeStream uses extractor source integrated directly into its app module. That source is derived
from NewPipeExtractor and later incorporated PipePipeExtractor and WizeStream-specific changes. It
is therefore neither the unmodified official NewPipeExtractor nor an external PipePipeExtractor
dependency. The exact extractor source is versioned and released together with each WizeStream
build.

This integrated source supports WizeStream's services and conventional YouTube playback.
WizeStream maintains these changes and accepts responsibility for defects caused by them.

## Where should I report a problem?

Use the WizeStream repository's issue form for every problem observed in WizeStream, including
service or extractor failures. Maintainers may ask whether the same problem occurs in official
NewPipe to identify a shared upstream defect, but that comparison is not a reason to reject a
WizeStream report. Report upstream only when the problem is reproducible there and follows the
upstream project's reporting rules. Do not include private URLs, cookies, passwords, or other
sensitive data in a public report.
