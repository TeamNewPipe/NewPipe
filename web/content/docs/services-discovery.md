WizeStream integrates extractor source directly into the Android app. Services provide different kinds of media and expose different metadata, formats and search controls. A feature available on one service is not necessarily available on every other service.

## Supported sources

| Source | WizeStream access |
| --- | --- |
| YouTube | Videos, live streams, channel content and supported public metadata. |
| YouTube Music and Shorts | Dedicated destinations within the YouTube service experience. |
| SoundCloud | Supported audio and creator content. |
| PeerTube | Supported content from the selected instance. |
| Bandcamp | Supported artist, album and track content. |
| media.ccc.de | Supported conference and event recordings. |
| Bilibili and Niconico | Integrated service support; details vary by source. |
| BitChute and Rumble | Integrated browsing and playback support. |
| Odysee | Experimental support; expect limitations. |
| Vimeo | Public videos, shared unlisted links, creator/channel uploads, Staff Picks, showcases and live events. Optional API search and public comments. |

Live Radio and Live TV use separate station/channel directories and imported stream lists. They do not turn every directory item into a supported social-platform subscription. See [live media and local files](live-media.html).

## Search the right service

Select the service before searching. Use its available content, date, duration, feature and sorting filters. YouTube Music has its own filter context. Supported presets can preserve repeatable search choices for a profile and service.

**These Vimeo features are included in the reviewed 1.52.2 release.** Use **Open link** to paste or share a video, creator, channel, showcase/album or event URL, or browse **Staff Picks**. Keep the full URL for an unlisted video, including its access hash. Public creator/channel uploads support pagination. Public showcases open as complete playlists, including legacy album and custom showcase links. Event links follow the broadcast or recording selected by Vimeo, with a waiting state for upcoming events. Videos use the native player when Vimeo provides unencrypted streams, with captions when available; downloads require a progressive video file.

Global keyword search and reading public video comments/replies require an optional **public-only Vimeo API token**. Follow [Vimeo's official instructions](https://help.vimeo.com/hc/en-us/articles/12427789081745-How-to-generate-a-personal-access-token) to create your own API app and generate an **Unauthenticated** token, then verify it under **Settings > Content > Vimeo > Set public API token**. WizeStream accepts only the public scope, stores the token encrypted on the device, and excludes it from backups and Device Sync. Search uses relevance order; comments use Vimeo's chronological order. Removing the token disables these API features. Public playback, showcases and events do not require a token. If a large showcase's public embed omits members, configured API access can load the remaining pages; partial lists are never labeled complete.

The shared **Search Vimeo** action remains visible while filtering local tabs such as **What's New**. Before API setup, it offers **Open Vimeo settings** and preserves the query so search can continue when you return. Canceling keeps the local filter. The same prompt also offers **Open link** for public URL entry without a token.

Vimeo's regional restrictions and API quotas still apply. Account sign-in inside WizeStream, account libraries, posting/liking comments, live chat and password/private/domain-restricted/DRM-only content are unsupported. Public API access to an unlisted video's comments is not guaranteed.

YouTube searches can use custom or relative upload-date ranges, duration limits and supported deeper collection. Larger searches can take longer and still depend on the service’s returned results; they are not a complete database query over every upload.

The microphone button delegates recognition to an installed speech-recognition application. WizeStream requests offline recognition, but the provider determines whether it can work locally and may use its own servers. No Google account or bundled speech SDK is required by WizeStream.

## Add a service plugin

Since 1.52.0, **Settings → Plugins** can install signed service packages from the included [WizeStream Plugins catalog](https://github.com/wizdom13/WizeStream-Plugins) or another HTTPS JSON repository. Enable **Allow plugins**, choose a publisher you trust, install the package and restart when prompted. A plugin cannot replace a service already built into WizeStream.

Native WizeStream plugins use **API 2.0**; compatible PipePipe packages use **API 1.0 or 1.1**. External plugins require **Android 7/API 24** or newer even though the app and built-in services support **Android 6/API 23**. A package can require a newer Android version or host release.

Packages and repositories belong to the device. Plugin settings and optional login cookies are separated by profile, and profile changes restart the process when plugins are loaded. Packages, repositories, publisher pins and plugin configuration are not transferred by backups or Device Sync. Reinstall and configure the same plugin on another device to use restored service records.

Plugins execute inside the app with its permissions; a valid package signature identifies the publisher and verifies the downloaded bytes, but does not sandbox the code. Read the [plugin installation and authoring guide](https://github.com/wizdom13/WizeStream/blob/pipe/docs/plugins.md) for trust, update and compatibility rules. The [native Kotlin template](https://github.com/wizdom13/WizeStream-Plugin-Template) is available to service authors.

## Explore a channel

Channel tabs can expose videos, Shorts, playlists, past live streams, Courses, Podcasts and album/release content where the service provides them. Channel sorting can offer latest, popular or oldest ordering.

Read-only YouTube community posts can contain text, images, polls and media attachments. Viewing a poll is not the same as voting with a YouTube account. Subscribe locally when you want the channel represented in your WizeStream feed.

## Search within a collection

Contextual search helps narrow channel tabs and local or remote playlists. Library search can also work across subscriptions, playlists, feeds, history and Downloads.

Comment search operates on loaded comments and loaded reply content. It does not search every remotely available reply or the entire discussion unless that material has actually been loaded. Service ordering such as Top or Newest and pagination remain relevant.

## Personalize without a platform account

Optional on-device recommendations can learn separately for each profile. The For you destination, Shorts and autoplay can use supported local signals after opt-in. Pause, reset and exclusion controls let you adjust that behavior; Incognito does not add new learning activity, and the recommendation model is not synchronized to other devices.

Saved searches, local blocks and subscription groups provide useful discovery even without recommendation learning. The app does not need to recreate a remote account profile to organize your library.

## Report service failures to WizeStream

WizeStream’s integrated extractor is derived from NewPipeExtractor with later PipePipeExtractor and project-specific changes. It is not an unchanged external extractor dependency.

Report failures observed in WizeStream to WizeStream first. Include a public item URL, the selected service, app version and the action that failed. Testing an upstream app can help identify a shared defect, but it does not change who maintains WizeStream’s bundled changes.
