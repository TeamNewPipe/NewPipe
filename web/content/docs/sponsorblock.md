SponsorBlock, DeArrow and dislike information are optional community services. They are independent of a media platform’s own metadata and each introduces a network request to its provider when used. You can use WizeStream without enabling them.

## SponsorBlock playback controls

The SponsorBlock master setting is **off by default**. Once enabled, category settings let you choose how supported segments are skipped or marked. Colors, notifications, seek-bar markers and manual actions help distinguish a segment from ordinary playback controls.

Community segment data can be incomplete or inaccurate. Inspect what a category means before enabling automatic skipping, especially if you prefer to hear context, introductions or non-sponsored material.

For supported newly completed full downloads, available SponsorBlock segment data can accompany the offline record. Offline skipping changes the player’s position; it does not cut or rewrite the media file.

## Full-video sponsorship disclosures

**Available since 1.42.0, including 1.52.2:** optional full-video sponsorship labels describe fully sponsored videos, exclusive/subsidized access and unpaid/self-promotion.

These labels are separate from Members only and AI/content-origin disclosures. They describe community-supplied sponsorship information, not a guarantee about the creator or video.

**Full-video sponsorship labels** is enabled within SponsorBlock by default, but remains dependent on the SponsorBlock master switch. Since the master is off by default, this does not mean every installation contacts SponsorBlock immediately.

## Enable contribution tools deliberately

**Available since 1.42.0, including 1.52.2:** submissions and voting require a separate opt-in.

1. Open SponsorBlock settings and enable **Enable contribution tools**.
2. Read the contribution privacy notice before confirming.
3. Use the contribution action in the player to create a segment or vote on an existing one.
4. For a new segment, set the start, end and category, then review the proposed submission.
5. Explicitly submit it. For existing segments, choose an upvote, downvote or undo action.

Ordinary range segments require valid start/end times. Highlights use a start timestamp rather than a normal range. Tools apply to eligible non-live YouTube streams, not arbitrary local files or downloaded segment URLs.

## Contribution privacy and failures

Contributions are not anonymous. SponsorBlock receives normal connection information and a persistent private contribution ID. Accepted contributions become public community data.

The private ID is stored separately from normal app data and is excluded from WizeStream backups and Device Sync. Keep it private rather than posting it in a bug report.

An uncertain response is not automatically replayed as another public submission. Check the service before retrying, because a request can arrive even when its confirmation is lost. The same caution applies when you are unsure whether a vote was accepted.

## DeArrow titles and thumbnails

Open **Settings → SponsorBlock → DeArrow**. DeArrow is disabled by default. Once enabled, accepted title and thumbnail replacements can be controlled separately.

If no accepted replacement exists or a request fails, WizeStream keeps the original metadata. The current reviewed WizeStream implementation displays DeArrow contributions; it does not provide the DeArrow contribution interface available in LibreTube.

## Dislikes and source metadata

Optional dislike information is community-derived and may differ from historical platform counts. It is not an official private YouTube statistic. Media services, comment authors and third-party providers can also change or remove information over time.

For a side-by-side view of these capabilities, use the [competitor comparison](../compare.html). For data-flow details and controls, see [privacy and networking](privacy-network.html).
