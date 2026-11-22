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
| Vimeo | Public videos, shared unlisted video links, public creator/channel uploads and Staff Picks. |

Live Radio and Live TV use separate station/channel directories and imported stream lists. They do not turn every directory item into a supported social-platform subscription. See [live media and local files](live-media.html).

## Search the right service

Select the service before searching. Use its available content, date, duration, feature and sorting filters. YouTube Music has its own filter context. Supported presets can preserve repeatable search choices for a profile and service.

Vimeo offers **Open link** in place of keyword search. Paste or share a video, creator or channel URL, or browse **Staff Picks**. Keep the full URL for an unlisted video, including its access hash. Public creator/channel uploads support pagination. Videos use the native player when Vimeo provides unencrypted streams, with captions when available; downloads require a progressive video file. Keyword search, whole showcase/album lists, live events, comments, account sign-in and password/private/domain-restricted/DRM-only videos are unsupported. Individual video links inside showcases/albums can be opened.

YouTube searches can use custom or relative upload-date ranges, duration limits and supported deeper collection. Larger searches can take longer and still depend on the service’s returned results; they are not a complete database query over every upload.

The microphone button delegates recognition to an installed speech-recognition application. WizeStream requests offline recognition, but the provider determines whether it can work locally and may use its own servers. No Google account or bundled speech SDK is required by WizeStream.

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
