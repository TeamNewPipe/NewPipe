# Vimeo official API contract fixtures

These JSON documents are **synthetic contract fixtures**, not successful live API
captures. The names, IDs, counts, image paths, and comment text are invented. No
real Vimeo access token was available or acquired during implementation. JVM tests
use the explicitly fictitious string `test-public-token` with a fake downloader.

The response structure follows Vimeo's official API documentation and its archived
OpenAPI 3.4 specification:

- <https://developer.vimeo.com/api/reference/videos>
- <https://developer.vimeo.com/api/common-formats>
- <https://developer.vimeo.com/api/authentication>
- <https://github.com/vimeo/openapi/blob/master/api.yaml>

The archived specification documents `GET /videos` with a keyword `query`,
`GET /videos/{video_id}/comments`, and
`GET /videos/{video_id}/comments/{comment_id}/replies`. Collection responses use
`data` and `paging.next`; the comment representation includes `uri`, `text`,
`created_on`, nullable `user`, and `metadata.connections.replies`.

The shared-video fixture exercises preservation of an explicitly supplied unlisted
URL when parsing a collection item. It is deliberately separate from global public
search results. The comment shared-URL test verifies local source identity only;
it does not establish that a public-scope token can access an unlisted discussion.
Provider restrictions remain errors and are never retried with another identity.

Ordinary unauthenticated probes on 2026-11-22 returned HTTP 401/error 8003 for the
search and comments API endpoints. Public website search/video pages did not
provide complete server-rendered result/comment lists. Therefore these features
require the optional, explicitly user-supplied official public-scope API token.
Tests do not claim live authenticated API verification or scrape website tokens.
