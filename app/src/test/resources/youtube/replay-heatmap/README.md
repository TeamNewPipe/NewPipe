# Replay heatmap fixture

`big-buck-bunny-next.json` is a reduced **live response**, captured on
2026-11-22 at 19:15:14 UTC from:

- Video: https://www.youtube.com/watch?v=YE7VzlLtp-4 (Big Buck Bunny)
- Endpoint: `POST https://www.youtube.com/youtubei/v1/next?prettyPrint=false`
- Anonymous WEB client version: `2.20261119.07.00`, read from that video's watch page.
- Client locale: `hl=en`, `gl=US`; request also set `contentCheckOk` and `racyCheckOk`.
- SHA-256 of the original 386,485-byte response:
  `3042c0f77916711203db0efab81f8195cebfb55394a2574fc0528874d3c38c63`.

The capture retains the current `frameworkUpdates.entityBatchUpdate.mutations`
path, the video's public `externalVideoId`, the heatmap's `markerType`, and all
100 markers with their original start, duration and intensity values. Other
entities, tracking and session values, decorations and unrelated metadata were
removed. This is evidence that the existing desktop `/next` request can supply
the data; it does not imply that every video or request receives a heatmap.
Tests read this committed fixture and never contact YouTube.

Other parser cases are deliberately synthetic and constructed in the Kotlin
tests. The current entity shape is also used by yt-dlp's
[`_extract_heatmap`](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/youtube/_video.py).
The legacy overlay case follows the path documented in yt-dlp's
[`2023.07.06` extractor](https://github.com/yt-dlp/yt-dlp/blob/2023.07.06/yt_dlp/extractor/youtube.py).
