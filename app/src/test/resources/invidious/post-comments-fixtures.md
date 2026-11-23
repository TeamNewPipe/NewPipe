# Post comments fixtures

The `post*.json` files are synthetic, representative API responses, not live captures.
They use invented post, channel, and comment IDs and contain no user data.

Their structure follows the Invidious implementations of
[`post` and `post_comments`](https://github.com/iv-org/invidious/blob/master/src/invidious/routes/api/v1/channels.cr),
[`extract_channel_community`](https://github.com/iv-org/invidious/blob/master/src/invidious/channels/community.cr),
and [`parse_youtube`](https://github.com/iv-org/invidious/blob/master/src/invidious/comments/youtube.cr).
The fixtures cover author bootstrap, comment metadata, post pagination, and reply
pagination, with punctuation in continuation tokens to exercise URL encoding.
