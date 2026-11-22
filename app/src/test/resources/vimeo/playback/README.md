# Vimeo public playback captures

These are reduced captures from ordinary unauthenticated GET requests made on
2026-11-22. The player fixtures retain the actual `window.playerConfig` assignment,
video identity/metadata, file/CDN choices, privacy/DRM state and caption languages.
The HTML wrapper is reduced to a title and that script. Unused configuration,
session/cookie/analytics fields, DRM licence/certificate material and every signed
media path/query value have been removed. Media paths use `/fixture/` and query
values use a fixed placeholder; they are intentionally not playable URLs. No
credentials, viewer JWT, Authorization header or spoofed Referer was used.

| Fixture | Public source and observed response |
| --- | --- |
| `clear-progressive.html` | <https://player.vimeo.com/video/35572051> — public “BBB loves CC (feat. Big Buck Bunny)”; two progressive MP4 variants and clear HLS; the advertised DASH URL is proprietary `playlist.json`. |
| `clear-adaptive.html` | <https://player.vimeo.com/video/1236621193> — public Staff Pick “Ibuka, Justice”; clear HLS with no progressive variants, Arabic and English (United States) captions. |
| `drm-only.html` | <https://player.vimeo.com/video/76979871> — at capture time this player advertised only protected adaptive files with nonempty Widevine/PlayReady/FairPlay configuration; no progressive files. DRM CDM identifiers are preserved, while licence material is removed. |
| `private.html` | <https://player.vimeo.com/video/59777392> — player title “Private Video”, `view: 2`, video ID only, no files. |
| `clear-progressive-oembed.json` | <https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F35572051> — public title, creator, description, duration and upload date. |
| `clear-adaptive-oembed.json` | <https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F1236621193> — public metadata; only the first description line is retained. |

The DRM/private HTML data matches the player configurations; direct public
`/video/ID/config` responses were also inspected during research. Production uses
the normal player page's advertised configuration, because other public videos
returned a restriction from the synthesized `/config` endpoint even though their
ordinary public player advertised working clear media. A denial in the actual
player flow is terminal; no private-domain referrer or alternate access mode is
attempted.

## Live resource verification

The untouched URLs advertised by those clear player pages were requested before
sanitization. These checks do not imply Android device playback has been tested.

| Advertised resource | Result |
| --- | --- |
| 35572051 progressive MP4 | HTTP 206, `video/mp4`, first 1,024 bytes identify an MP4 (`ftypisom`). |
| 35572051 AVC HLS | Master and first advertised video variant returned HTTP 200, `application/x-mpegURL`, valid `#EXTM3U`; the variant contained media segments and no `EXT-X-KEY` markers. |
| 35572051 DASH-labelled JSON | HTTP 200, `application/json`, Vimeo segment metadata rather than an XML MPD. It is not exposed as a DASH stream. |
| 1236621193 AVC HLS | Master and first advertised video variant returned HTTP 200, `application/x-mpegURL`, valid `#EXTM3U`; the variant contained media segments and no `EXT-X-KEY` markers. |
| 1236621193 English caption | HTTP 200 from `captions.vimeo.com`, `text/vtt`, valid `WEBVTT` header and timed cues. |

The captured clear configurations reported a 3,600-second request expiration.
Their transient media addresses must not be treated as permanent links. HLS/MPD
entries use the app's native adaptive playback path. The existing downloader
only offers actual progressive files and captions; adaptive-only videos do not
gain a fabricated downloadable MP4.

Shared-hash URLs, password/domain errors, malformed/mismatched responses,
advertised MPD support, and clear progressive beside protected adaptive files
are explicitly **synthetic edge cases in Kotlin tests**, not claimed live
captures of private/unlisted or password-protected content.
