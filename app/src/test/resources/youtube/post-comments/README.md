# YouTube post comment fixtures

## Captured public post responses

`captured-*.json` was derived from successful anonymous requests on **2026-11-22** with
the YouTube WEB client **2.20261119.07.00**, using this public post discovered on `@YouTube/posts`:

<https://www.youtube.com/post/UgkxISQDxlrLJ5sy6q4H5TsejrpoueuwZTvp>

The captured sequence successfully resolved the canonical post through `navigation/resolve_url`,
then requested `browse` for top comments, newest comments, the next top-comments page, and a
comment's replies. Each top/new/next response had 20 modern comment threads. The reply response
contained two modern comment view models and no further continuation. No video `player` or `next`
request and no account sign-in was used.

The committed fixtures are deliberately minimized:

- Two comments are retained per page, including a top-level comment that has replies.
- Actual response containers, comment IDs, relevant entity keys and their linked comment,
  toolbar-state and comment-surface mutations are retained.
- Visitor, tracking and logging data, unrelated mutations, write-action commands and unused
  renderer metadata are removed. Names, author channel IDs, avatars and comment text are
  replaced with short placeholders; rich text runs are removed with the original text.
- Opaque page/reply tokens are replaced consistently across the offline request/response
  sequence. `captured-initial-requests.json` retains the two public post/channel continuation
  values generated from the documented wire fields and successfully accepted by `browse`.

The tests exercise these actual response shapes with a fake downloader, including extractor
recreation and serialized reply state. The live requests establish protocol behavior for this
public post at the capture date; they do not establish availability of every post or restricted
discussion. Empty/disabled/error and legacy-format cases below remain representative examples.

## Representative protocol edge cases

These `representative-*.json` files are intentionally constructed protocol examples, not live
YouTube recordings. IDs, authors, content, image URLs and continuation strings are synthetic.
They exercise response shapes and request routing; passing these tests is not proof of current
provider availability.

Wire format and response-container references, retrieved from Invidious commit
`9a5c91cf808966fb6a4516c9e21ec7a7ad648f16`:

- [Post resolution and channel context](https://github.com/iv-org/invidious/blob/9a5c91cf808966fb6a4516c9e21ec7a7ad648f16/src/invidious/channels/community.cr):
  `decode_ucid_from_post_protobuf` reads field 56, nested field 2;
  `fetch_channel_community_post` includes the post ID in nested field 3.
- [Post comments, sorting and responses](https://github.com/iv-org/invidious/blob/9a5c91cf808966fb6a4516c9e21ec7a7ad648f16/src/invidious/comments/youtube.cr):
  `fetch_community_post_comments` builds a browse continuation; `parse_youtube` handles reload,
  append, item-section and reply containers, modern entities and legacy renderers.
- [Canonical URL resolution](https://github.com/iv-org/invidious/blob/9a5c91cf808966fb6a4516c9e21ec7a7ad648f16/src/invidious/routes/api/v1/channels.cr):
  `post` resolves `/post/{id}` anonymously; `post_comments` uses browse for continuations.

The resolve fixture and expected top/new continuation constants were encoded independently
with a small Python protobuf-wire encoder using these published field numbers. Tests decode
and compare the result rather than obtaining expected values by calling production helpers.

Modern renderer fields also follow the existing WizeStream `YoutubeCommentsEUVMInfoItemExtractor`
and its existing avatar/image tests. A disabled-provider-notice test preserves the notice as
an unavailable result; it does not invent a language-independent disabled flag.

Future live captures should be stored separately with the public post URL, capture
date, client and sanitization notes. Do not rename these representative examples as captures.
