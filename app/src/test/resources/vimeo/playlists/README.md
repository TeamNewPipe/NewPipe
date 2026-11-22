# Vimeo showcase fixtures

Captured from ordinary anonymous HTTPS GET requests on 2026-11-22. No cookies,
account sign-in, API access token, visitor-token extraction, or custom Referer was
used to retrieve these public pages.

| Fixture | Public source | Observed complete list |
| --- | --- | --- |
| `showcase-2632481-embed.html` | `https://vimeo.com/showcase/2632481/embed` | 14 clips, `playlist_data.total = 14` |
| `showcase-7683386-embed.html` | `https://vimeo.com/showcase/7683386/embed` | 224 clips, `playlist_data.total = 224` |
| `showcase-7999344-embed.html` | `https://vimeo.com/showcase/7999344/embed` | 17 clips, `playlist_data.total = 17` |
| `showcase-lean.html` | `https://vimeo.com/showcase/lean` | Declared canonical `https://vimeo.com/showcase/7999344` |

These HTML fixtures retain only the public title, named `dataForPlayer` playlist
metadata/clip objects, and the identity and registration fields of `entityData`.
The custom-name page retains its public title and canonical link. Unrelated
scripts, tracking, visitor/bootstrap data, player-configuration signatures, and
presentation markup were removed. Clip IDs, order, duration, view counts,
thumbnails, and the public `h` identity parameter were retained. Interior clip
titles and creator names were replaced with short placeholders; first and last
clip metadata remain as captured. Config URLs retain their original origin/path
and `h` parameter only; the extractor reads their identity and does not request
those config endpoints.

Additional live checks established that `/album/2632481` redirects to
`/showcase/2632481`; showcase 6744116 exposed all 64 clips in its public embed;
and the public showcase 2632481 landing page exposed only 10 of 14 videos in its
structured data. Its anonymous oEmbed response declared `/embed2`, which served
a client application shell, while the ordinary `/embed` route provided the full
public playlist. The implementation therefore uses the full public playlist
bootstrap and requires exact agreement with its advertised total, rather than
assuming a landing page's initial subset is complete.

The optional official API fallback responses in `VimeoPlaylistTest` are
**representative, hand-authored fixtures**, not captured authenticated responses.
Their `data`, `total`, `page`, `per_page`, and `paging.next` shape follows Vimeo's
[common formats documentation](https://developer.vimeo.com/api/common-formats).
They exercise a user-configured public API token, `/albums/{id}` metadata,
`/albums/{id}/videos` pagination, fresh extractor continuation, and response/error
validation. No API credential was available for a live integration check.
The successful fallback case generates a 100-item first page and a one-item last
page, matching the requested API page size. Its serialized continuation retains
the collection identity and the original advertised total. Count or page-size
changes deliberately fail instead of turning an omitted subset into a complete
collection.

Protection, live/pending flags, malformed payload, empty, truncated, changed-identity, and changed
continuation cases are deliberate test mutations or representative payloads;
they are not presented as live observations. Registration/password/SSO errors
remain errors even when a public API token is configured. The deprecated Simple
API v2 and RSS feeds are not used.

Primary documentation:

- [Embedding showcases with oEmbed](https://developer.vimeo.com/api/oembed/showcases)
- [Vimeo API reference](https://developer.vimeo.com/api/reference)
- [Showcase privacy settings](https://help.vimeo.com/hc/en-us/articles/15004792582545-How-to-manage-my-showcase-s-privacy-settings)
- [Deprecated Vimeo APIs](https://help.vimeo.com/hc/en-us/articles/12427942328209-Deprecated-APIs-libraries-and-upload-methods)
