# Public Vimeo event captures

Captured on 2026-11-22 using ordinary, unauthenticated GET requests. No account,
viewer JWT, Authorization header, custom Referer, password, registration or
Enterprise Live API was used. These are public embed/player captures, not
synthetic representations of a successful API response.

## Event embeds

The HTML fixtures retain the selected `.player` clip/config attributes and the
public `dataForPlayer` and `entityData` JSON assignments. Unrelated scripts,
analytics and styling were removed. Each config URL keeps its actual clip ID;
resource query settings/signatures were replaced with fixed fixture values.
Only the first three clip descriptors are retained where there are more.

| Fixture | Public URL | Observed state |
| --- | --- | --- |
| `live-event.html` | <https://vimeo.com/event/4768062/embed> | Graceland Cam selects clip 1240101097, `is_live=true`. The other listed clips are not selected. |
| `pending-event.html` | <https://vimeo.com/event/5116195/embed> | Skidmore College Commencement 2025 selects pending clip 1085320934, although recording 1082194134 is also present. Its old schedule is hidden. This proves a waiting placeholder, not a newly scheduled future broadcast. |
| `archive-event.html` | <https://vimeo.com/event/4753126/embed> | Salem Live Event selects recording 1247036786 with `show_latest_archived_clip=true`, while pending clip 1248055585 is first in the list. The full response contained 100 clip descriptors; this fixture keeps the first three and makes no completeness claim. |
| `restricted-event.html` | <https://vimeo.com/event/5034253/embed> | `not_accessible`, no selected player or clips. No alternate URL/referrer was attempted. |
| `not-embeddable-event.html` | <https://vimeo.com/event/5120811/embed> | `not_embeddable`, no selected player or clips. The old schedule is not treated as playback permission or an upcoming broadcast. |

## Advertised player configurations

These JSON files were fetched from the exact `https://player.vimeo.com/video/ID/config`
resources advertised in those embeds, preserving their original public resource
queries for the request. The fixture copies remove signed URL path/query values,
unused settings and session/subtitle service descriptors. Media URLs are fixed
`/fixture/` placeholders and cannot be used for playback.

| Fixture | Clip ID | Observed state |
| --- | --- | --- |
| `live-config.json` | 1240101097 | `live_event.status=started`, duration 0, public privacy/embed permission, clear HLS, DVR disabled. The ordinary public player HTML advertised the same live state. |
| `pending-config.json` | 1085320934 | `live_event.status=pending`, duration 0, no playable CDN URL. There is no published scheduled start in the configuration. |
| `archive-recording-config.json` | 1082194134 | `live_event=null`, duration 9810, ordinary public VOD HLS/JSON manifests. |
| `selected-archive-config.json` | 1247036786 | `live_event=null`, duration 2777, ordinary public VOD HLS/JSON manifests. |

The live configuration's untouched HLS URL returned a master playlist with HTTP
200 after a normal redirect from `live-api.vimeocdn.com` to `live-ak.vimeocdn.com`.
The first advertised variant also returned HTTP 200 and contained six media
segments, no `EXT-X-ENDLIST`, and no `EXT-X-KEY`. This verifies a publicly accessible
live resource. A later read advanced the media sequence from 713850 to 714173
while retaining six segments and no end marker. Android device playback is a separate CI/device concern. The
configuration reported a 3600-second request expiration.

Tests labelled as mutations cover future schedules, `active` and `ended` states,
registration/SSO, shared event hashes, identity substitution and protected
responses. Those cases are synthetic; they are not claimed live captures. A
completed, transcoded recording is distinguished by its normal video config.
A just-ended player can remain `POST_LIVE_STREAM` if it advertises a playable
resource; otherwise extraction reports that no public recording is available.
An ingest schedule is exposed only when the player enables `event_schedule`;
the event embed's schedule additionally requires `show_start_time` or `show_schedule`.

## Player-state semantics

The provider's public [player module 4.46.152](https://f.vimeocdn.com/p/4.46.152/js/player.module.js)
was inspected on the capture date (SHA-256
`41e6d27f0472ebe072848587c458dfb6c1fcbed8f938a7deccfa0df99138536f`).
Its live-state initialization sets playback eligibility only for the `started`
state; `pending` and `active` are separate non-started states. It exposes the
ingest schedule alongside the separate `settings.event_schedule` display flag.
This provider source supports treating `active` as waiting and honoring the
schedule display flag; no public `active` response was captured, so its fixture
test remains explicitly synthetic. No player implementation code is copied here.

## Primary documentation

- [Event IDs and archive video IDs are distinct](https://help.vimeo.com/hc/en-us/articles/23741128962193-How-to-share-my-archived-live-event).
- [The owner chooses the waiting display, schedule or latest video](https://help.vimeo.com/hc/en-us/articles/24950002463377-How-to-customize-my-live-event-s-player).
- [Event privacy and domain restrictions](https://help.vimeo.com/hc/en-us/articles/12426977279889-About-privacy-settings-for-live-events).
- [The separate Enterprise external-HLS API requires authenticated access](https://developer.vimeo.com/api/live/playback); it is not used here.
