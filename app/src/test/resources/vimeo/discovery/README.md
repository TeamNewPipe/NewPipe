# Vimeo public discovery captures

These fixtures are reduced HTML captures from ordinary, unauthenticated GET requests
made on 2026-11-22. They are not synthetic lists. The relevant page title, description,
canonical/next links, heading, all video cards, and one channel/profile image are
preserved. Navigation, styles, scripts, unrelated markup, and the unused `data-stream`
attribute were removed. No cookies, visitor tokens, authorization headers, or
account credentials were used by these requests or retained in these fixtures.

| Fixture | Public source |
| --- | --- |
| `creator-page1.html` | <https://vimeo.com/nkistudio/videos/page:1/> |
| `creator-page2.html` | <https://vimeo.com/nkistudio/videos/page:2/> |
| `creator-page14.html` | <https://vimeo.com/nkistudio/videos/page:14/> |
| `creator-alias-page1.html` | <https://vimeo.com/user1939265/videos/page:1/> |
| `creator-alias-page2.html` | <https://vimeo.com/user1939265/videos/page:2/> |
| `staff-picks-page1.html` | <https://vimeo.com/channels/staffpicks/videos/page:1/> |
| `staff-picks-page2.html` | <https://vimeo.com/channels/staffpicks/videos/page:2/> |

Each first/second page contained 12 videos. The creator's final page contained 10
videos and no next-page link. Inline error/empty/malformed documents in the test
code are explicitly synthetic cases, not captured provider responses.

The numeric creator alias returns HTTP 200 without redirecting, while its heading,
canonical link, and next-page link identify the same creator's `/nkistudio` URL.
Alias pagination retains the requested numeric URL so a fresh extractor can resume
without an in-memory alias registry; video uploader links use the provider's
canonical creator URL.

## Deliberate capability limits

- Public search returned a JavaScript application shell without server-rendered
  results. Legacy search-page paths returned HTTP 404. Search is unsupported.
- The public showcase <https://vimeo.com/showcase/2632481> exposed 10 JSON-LD videos
  while reporting 14 total. Adding `?page=2` returned the same subset, and the
  legacy `/videos/page:2/` variants returned HTTP 404. Showcases are not exposed as
  complete playlists.
- Vimeo marks its old Simple API unsupported and its RSS feeds discontinued:
  <https://help.vimeo.com/hc/en-us/articles/12427942328209-Deprecated-APIs-libraries-and-upload-methods>.
  Neither is used.
