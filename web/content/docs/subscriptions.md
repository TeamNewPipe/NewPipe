Subscriptions are stored locally in WizeStream. Following a channel does not subscribe your Google account, notify the uploader or require a platform login. Your local feed depends on the channel information the selected service makes available.

## Follow channels and make groups

1. Open a supported channel and select Subscribe.
2. Open your subscriptions to organize the channel into a group.
3. Use groups to separate interests such as Music, Technology or Study.
4. Open **What’s New** to see the subscription feed for the chosen scope.

Subscriptions and feed groups are scoped to the active local profile. Grouping is useful within a profile; separate profiles also isolate library data such as history and playlists. See [profiles and synchronization](profiles-sync.html).

The Subscriptions heading has a compact count, pencil edit action, search and separate expand/collapse control. Channel groups remember their Grid/List choice. Optional floating New group and Search buttons can be enabled in Appearance; they hide while editing or while the keyboard is open.

In 1.52.2, editing controls scroll with the channel list so the final row can remain above the mini-player, including in short landscape windows. Channel order and the visible channel anchor are preserved when switching between browsing and editing.

## Understand the feed scope

The What’s New toolbar can change the selected channel group and service scope. Switching groups uses cached feed data rather than automatically requesting every channel again. A publication-date cutoff and content/watch-state filters can further narrow the displayed results.

| Control | What to check when an item is missing |
| --- | --- |
| Group | Is the channel in the selected group? |
| Service | Are you showing its platform or all supported platforms? |
| Content type | Are Videos, Shorts, Live or other types hidden? |
| Watch state | Does an unwatched or partially watched filter exclude it? |
| Date cutoff | Is the upload older than the selected cutoff? |
| Blocking | Is a video, channel, keyword or membership rule hiding it? |

Content-type choices occupy the first filter row; watch status and the independent **Downloaded** filter share the second. **Upcoming** covers scheduled broadcasts still waiting to start. Saved visibility defaults and the publication-date cutoff are available through **Feed options**.

Feeds are not unlimited historical archives. A refresh can improve freshness without guaranteeing that every older channel upload is collected.

## Refresh and notifications

Pull down at the top of a feed to refresh, whether it is opened from Home or Subscriptions and whether its header is expanded or collapsed. If you enable floating refresh controls, the floating **Refresh/Cancel** button is the alternative to the pull gesture. Progressive refresh can show updates as they arrive instead of waiting for every channel to finish. **Refresh all now** bypasses the ordinary refresh threshold.

Channel notification settings can control frequency, network conditions and title keywords. Android can delay scheduled background work, particularly when the app is battery-restricted or the device is offline. A permission or scheduling limit is different from unsubscribing.

## Playlists and organization

Local playlists belong to your WizeStream library. Remote bookmarks point to service-hosted playlists; those playlists can change independently. Categories, named separators and bulk actions help organize larger collections.

Use **Select items** for local-playlist multi-selection, including outside Edit mode. **Copy to playlist** keeps the source entries; **Move to playlist** transfers selected occurrences, including duplicates, while preserving hidden entries and saved order. **Reorder selected** is separate. Where enabled, swipe-to-remove in Edit mode offers Undo. Review the selected scope before a bulk move or unsubscribe action.

## Smart playlists and saved searches

Smart playlists work with content already indexed from feeds, playlists and history. Rules can consider watched state, duration, title keywords, channels and Learning content. Refresh feeds to discover new uploads before expecting them to match a rule.

The current implementation allows up to 50 rule sets per profile and previews up to 1,000 newest matches. A smart playlist is not a server-side search over the entirety of YouTube or another platform.

Saved searches preserve reusable discovery criteria and cached results. They are useful alongside subscriptions when you want to revisit a topic rather than follow one channel.

## Content controls

Long-press a video to block the video or its channel. **Settings → Content → Blocked content** contains local rules and controls. Optional advanced regular expressions and comment filters provide more precise matching; these are local filters, not reports sent to a platform.

If a public upload is still missing after checking filters and refreshing, include its public video URL, channel URL, installed WizeStream version and feed scope in a bug report. Never attach a private backup database to a public issue without reviewing its contents.
