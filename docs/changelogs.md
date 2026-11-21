# WizeStream changelogs

Release history is listed newest first. The number beside each release is its Android version code.

## Unreleased

## WizeStream 1.49.1 (`1049001`)

### Downloads hotfix

- Fix a crash when switching Downloads to List view caused by a missing caption badge in the linear download item layout.
- Add regression coverage ensuring both Downloads layouts provide the required item views.

## WizeStream 1.49.0 (`1049000`)

### Playback and live media

- Preserve fullscreen-to-landscape browsing and use more compact phone landscape player, video-list and channel layouts.
- Improve player queue recovery checkpoints, popup playback quality, picture-in-picture sizing and Media3 title/artwork continuity.
- Allow seeking within supported live-DVR windows, retain selected livestream speed and stabilize post-live audio transitions.
- Correct live-chat replay position sampling on the Media3 main thread.

### Feeds, history and local library

- Show optional seen markers and clearer watched states in What's New, plus downloaded-video badges on thumbnails.
- Browse older cached feed pages, retain recent feed entries for 26 weeks, and include bounded feed archives in full backups.
- Expand watch-history sorting and date filters, add view-only reverse local-playlist ordering and preserve long quick-add playlist IDs in backups.
- Support full YouTube channel searching, show attached images in comments, and improve video-card touch targets and subscription menu actions.

### Personalization, settings and devices

- Refine local-like controls and recommendation learning so queued events remain attached to the correct profile.
- Cast videos to and control playback on paired WizeStream devices, with bounded Device Sync transport retries.
- Preview selective settings imports and allow exporting or resetting supported preferences, with improved Home-tab defaults and tooltips.
- Clarify rate-limiting and HTTP 403 errors and refine fast-scroller positioning in subscriptions.

### Community reviews

- Let maintainers publish official developer replies under moderated reviews.
- Allow original reviewers to propose changes without altering their published ratings or text before moderation approval.
- Add optional browser-local Remember My Review and My Reviews management without accounts or cookies.

## WizeStream 1.48.0 (`1048000`)

### Playback and reliability

- Stabilize high-speed popup quality changes and fit portrait videos to the popup window.
- Keep recently ended livestreams playing through temporary background transitions.
- Present upcoming livestreams as Upcoming/Waiting without trying to play unavailable sources.
- Restore appropriate popup, background and download controls for livestreams, and improve live-chat tab visibility.

### Feeds, recommendations and interface

- Add an opt-in, per-profile feed archive with bounded retention and cleanup on profile deletion.
- Load older cached feed videos progressively, with an optional deeper YouTube channel-history fetch.
- Restore channel avatars in For You and local-like controls on video details.
- Add faster list scrolling, comment display controls and a limited Recent Actions undo menu.

### Synchronization, downloads and tests

- Retry additional temporary peer network failures during Device Sync.
- Keep downloaded captions with the selected video destination and improve offline subtitle handling.
- Improve Android emulator graphics test reliability while retaining physical-device assertions.

## WizeStream 1.47.0 (`1047000`)

### Playback and presentation

- Choose Auto, Portrait or Landscape fullscreen orientation and correct portrait-video fullscreen behavior when video dimensions arrive.
- Add optional fullscreen ambient glow using the existing thumbnail, disabled by default.
- Preserve caption language preferences across videos and improve playback queue recovery.

### Home, feeds and playlists

- Collapse subscription channel groups independently for each profile.
- Modernize the What's New refresh indicator with lightweight Material styling and reduced-motion support.
- Improve large-feed pagination, move recommendation work off the main thread and reduce responsiveness delays.
- Add more local playlist sorting options and allow selecting a Home startup tab.

### Settings, imports and synchronization

- Add shared collapsing headers throughout Settings.
- Import search history from NewPipe backups and refine migration behavior.
- Improve Device Sync diagnostics for received history events.

## WizeStream 1.46.0 (`1046000`)

### Playback and downloads

- Choose when player controls hide after touch or D-pad input. Use the default, 5, 10, or 15 seconds, or keep them visible.
- Play downloaded audio and video inside WizeStream with cached media details and artwork when available. Use Open with for external playback.
- Start popup playback from Local Media, change watch status, copy titles, manage Learning, and open the actions for completed downloads.
- Save companion captions in the same folder as the video when choosing a custom download location.
- Play SoundCloud tracks that use unencrypted 96 kbps AAC streams.

### Home, feed and playlists

- Reduce memory use when switching Home tabs and reuse unchanged For You results. Score each What's New item once per relevance sort.
- Fill in missing YouTube video durations in What's New when details are already available for the active profile.
- Show each video's uploader in YouTube playlists, with a playlist-level fallback for auto-generated albums.

### Website

- Link the current release and GitHub APK downloads to the latest GitHub release page.

## WizeStream 1.45.0 (`1045000`)

### Playback and downloads

- Set custom rewind and fast-forward durations independently and choose a custom hold-to-speed multiplier.
- Minimize the player from the comments header while keeping the video playing in the mini-player.
- Add real-time volume boost up to +12 dB and improve the visibility of controls in portrait playback.
- Preserve the last confirmed position when recovering playback after an interruption.
- Cache media details for completed downloads and show them in Downloads.

### Discovery and recommendations

- Export or import Your Taste for the active profile and include the full local model in app backups.
- Remove learned interests from Your Taste and keep them out of future recommendations and learning.
- Add opt-in controls that rank Shorts and short regular videos lower in For You. Reduce passive
  learning from videos with YouTube's structured AI or synthetic-content disclosure.
- Keep What's New responsive when sorting by relevance.
- Show YouTube channel join dates and external links in the channel About tab.
- Discover monitored HTTPS Invidious instances from the official directory in settings.

### Home, history and playlists

- Reduce memory pressure when switching between distant Home tabs.
- Start new installations and reset Home layouts on the search-first tab, with current-service
  search selected by default for new installations.
- Add an Audio filter in Watch history and use a Material 3 confirmation for clearing search history.
- Add Play All, Enqueue next, Enqueue and Edit playlists actions to bookmarked playlist menus.
- Keep new subscriptions outside separator groups.

### Website

- Add Reviews to the main website navigation.

## WizeStream 1.44.1 (`1044001`)

### Reliability and recovery

- Fix a crash when opening or returning to For You with local recommendations enabled.
- Add crash recovery from the error-report screen, allowing WizeStream to start temporarily with
  safe defaults, access backup and restore, restore the original settings or keep the safe settings
  without removing subscriptions, playlists, history, downloads or other saved data.

## WizeStream 1.44.0 (`1044000`)

### Discovery and personalization

- Add a Material 3 Sort feed dialog to What's New, including device-local Relevance sorting when
  local recommendations are enabled.
- Add device-local interest chips and All, Videos, Shorts and Audio filters to For You.
- Allow every Home tab to use any feed-group icon, with stable persistence and one-tap reset.

### Interface

- Add an enabled-by-default Appearance switch for hiding or restoring local-like controls without
  removing saved likes or playlists.
- Improve channel layouts for long names, subscription controls, compact filter scrolling and
  spacing across screen sizes.
- Keep channel content above the collapsed mini-player.

### Distribution

- Upload architecture-specific release APKs to the official website automatically after each
  successful GitHub release.

## WizeStream 1.43.0 (`1043000`)

### Recommendations and appearance

- Improve private local recommendations with explicit local likes, bounded public-like signals,
  channel affinity, recent activity, per-video explanations and a per-profile Your taste summary.
- Add a direct action for placing For you on Home and compact, accessible Like locally and Liked
  videos controls.
- Add custom Material accent colors while preserving the existing theme modes.

### Playback and live media

- Extend playback speed controls to 10×.
- Restore chat replay for finished livestreams and show details for upcoming streams.
- Preserve pending live HTTP 403 recovery while playback preparation is still in progress.

### Performance, TV and diagnostics

- Speed up large history loading, reduce resource use on low-memory Android TV devices and avoid
  retaining large manual feed-refresh results.
- Make SponsorBlock category settings TV-accessible and fix subscription button text clipping.
- Add privacy-respecting direct-download badges, refresh their counts promptly and include version
  diagnostics in Device Sync logs.

## WizeStream 1.42.0 (`1042000`)

### Library and migration

- Add Like locally controls and per-profile Liked videos playlists, with duplicate prevention,
  library shortcuts and support for full database-and-settings backups.
- Import and export FreeTube subscriptions and playlists with previews and duplicate handling.
- Preserve channel notification settings when importing backups.

### Community features and website

- Launch [WizeStream.org](https://wizestream.org) with mobile-friendly documentation, app details,
  competitor comparisons, downloads, FAQs and privacy information.
- Add optional, moderated community reviews without an account and a live README rating badge.
- Link About to the website, FAQ and review page. Offer an optional review prompt after 15 app
  openings, with a five-day reminder and Don't ask again.
- Keep review reminders active when opening the review page; opening a browser does not confirm
  that a review was submitted.
- Add optional full-video SponsorBlock sponsorship disclosures and separate opt-in segment
  submissions and voting.

### Playback and reliability fixes

- Preserve protected live DVR playback across missing DASH refreshes and refresh compatible
  live media URLs while retaining buffered playback.
- Bound live preparation retries, recheck ended broadcasts and renew the URL recovery budget
  after 30 seconds of successful playback.
- Prevent backpressure failures in throttled feed, subscription and library UI streams.

### Playback settings

- Add separate switches for 360° playback and A–B repeat in Video and audio settings, both off
  by default. Hide their controls while disabled; disabling restores flat playback or clears the
  active repeat range immediately.

## WizeStream 1.41.0 (`1041000`)

### Playback

- Add 360° video playback with automatic projection detection, manual equirectangular mode,
  touch and D-pad panning, optional motion tracking, and Recenter.

### Improvements

- Add optional thumbnail previews to new-stream notifications.

- Search loaded video comments and author names, with a clear action and explicit page loading.
- Play or queue a stream's audio on a self-hosted [yt-fifo](https://github.com/jagjordi/yt-fifo)
  server (Snapcast home audio) from the long-press menu and the Cast picker, once its HTTPS URL is set
  in the video and audio settings. Validate replies and keep existing casting connections intact.

- Add radio song titles supplied by stations to profile history, with station names and logos,
  duplicate suppression, and support for Incognito and disabled watch history.

- Use Edit mode for channel selections, with select-all and clear-selection actions, and keep
  unsubscribe behavior consistent between library groups and the subscription screen.
- Hold player controls to see their labels, while preserving existing long-press actions.

### Fixes

- Add spacing between the miniplayer preview and channel details in standard and wide landscape layouts.
- Guard miniplayer video-surface reparenting against duplicate attachments.
- Improve Bandcamp requests with the client verification cookie.

- Recover AAC decoder failures separately for each audio language and remember same-track Opus
  fallbacks while switching languages, without granting repeated retries to failed tracks.

- Load deferred channel-tab pages before showing an empty list, including past livestreams.
- Use Material 3 dialogs when deleting entries from watch history.

- Display each community-post photo once instead of repeating its different thumbnail resolutions.
- Clear the previous video's details when starting Live Radio and keep audio-only live streams
  from displaying the previous video surface.

### Live TV

- Show a search button beside the Live TV search field while text is entered, matching Live Radio.
- Add a Material 3 Live TV screen beneath Live Radio, with M3U file/HTTPS URL import,
  channel logos, group filters, search, favorites, and playlist management.
- Browse worldwide country lists from IPTV-org by default or Free-TV, with cached lists and
  automatic refresh when opening or resuming after 24 hours. Retain cached channels if refresh fails.
- Combine duplicate directory channels, preserve directory favorites across stream URL changes,
  and allow manual selection of alternative streams.
- Cache playlists and favorites per profile and play channels through the existing video player
  with fullscreen and TV remote controls.

## WizeStream 1.40.1 (`1040001`)

### Interface

- Add the optional Glass theme with translucent, blurred navigation surfaces. On supported phone
  portrait layouts, What’s New can scroll beneath the toolbar and bottom navigation.
- Add a Playlists shortcut beside Folders in Local Media videos and a Tracks tab between Albums
  and Genres in audio.

### Build improvements

- Correct unstable generated-interface ordering in the release compiler that caused independent
  rebuilds to differ. Independent verification is still required.
- Compare clean release APKs under different JVM identity-hash strategies and preserve artifacts
  when reproducibility checks fail.

## WizeStream 1.40.0 (`1040000`)

### Improvements

- Discover worldwide stations in Live Radio, save favorites, and add custom HTTPS audio or HLS streams.
- Search station names and pick country, language, and genre from searchable lists. Applied filters
  are remembered per profile and synchronize through Device Sync when Filters is enabled.
- Filter What’s New by publication date.

### Interface

- Show the station logo, name, and details in the collapsed Live Radio miniplayer.
- Keep What’s New updates arriving during refresh while reducing unnecessary row updates and showing
  a single completion banner when new highlighted items appear.

### Fixes

- Recover YouTube playback from timed-out CDN connections and AAC decoder output failures.

## WizeStream 1.39.0 (`1039000`)

### Improvements

- Combine content type and watch status in stream lists, so you can filter for unwatched videos or other combinations.
- Improve compatibility with installed speech recognizers for voice search on Android TV.

### Interface

- Distinguish YouTube Music filters from regular YouTube filters in the combined search dialog.
- Keep playlist and album search results compact in landscape and Samsung DeX.
- Keep the Incognito status visible without crowding the search field.

### Fixes

- Keep the What’s New refresh bar visible alongside the channel count while cached results are displayed.
- Fix subscription feed errors from YouTube course cards and skip cards that have no usable URL.
- Handle missing Bandcamp item URLs.
- Preserve the full live DVR window during playback recovery, with bounded retries.
- Allow more time to process watch-history sync requests and identify which stage timed out.
- Respect the disabled dislike-fetching setting when falling back to other metadata sources.

## WizeStream 1.38.0 (`1038000`)

### Improvements

- Added session-only A–B repeat for non-live playback, with controls in the expanded player.
- Added subtitle timing adjustment in 100 ms steps from the caption menu; the offset resets when the track changes.
- Added a home-screen playback widget with previous, play/pause, and next controls.
- Added an option to download a companion caption file with a video in the selected language.
- Added daily download windows for internal transfers, with optional unmetered-network and charging requirements.
- Added YouTube Courses channel tabs where channels provide them.
- Added chapter boundaries to standard and Wavy seek bars, with chapter titles while seeking and haptic feedback where supported.
- Added Brotli compression for supported service metadata responses.

### Interface

- Moved the What’s New channel-group selector into the toolbar.
- Improved Android TV remote navigation: mini-player buttons can be focused individually, and moving left from home content returns focus to the selected navigation item.

### Fixes

- Improved recovery from transient YouTube media redirects and truncated CDN responses.
- Refreshed live media URLs after repeated rejected segments before the player exhausts its buffer, with bounded retries.

## WizeStream 1.37.0 (`1037000`)

### Improvements

- Added optional muted video previews to What’s New cards, with seeking, mute controls, and full-player position handoff. Previews start only after a tap, stop off-screen or when leaving the feed, and do not write watch history.
- Added profile-specific Quick add to playlist in What’s New, with one-tap saving, saved-state checkmarks, duplicate prevention, and Undo. Uses local playlists and is disabled by default.
- Added a channel-group selector directly in What’s New, preserving the selected group and filters.
- Show subscription feed results progressively as channels finish refreshing, with stable new-video highlights. Refresh can continue while navigating or playing videos.
- Added optional automatic fullscreen for landscape videos on tablets while preserving manual overrides and keeping portrait videos unforced.
- Added an optional perceptual brightness gesture for finer low-light control.
- Added a playback information panel showing current playback formats, decoder details, buffering, and dropped frames.

### Interface

- Refined feed previews with a translucent Material 3 Preview button and lighter control panels. Preview actions hide with the playback controls, and the thumbnail duration badge is hidden during playback.

## WizeStream 1.36.1 (`1036001`)

### Improvements

- Added local subscription and group-channel search by saved YouTube handles, with or without @ and regardless of letter case. Handles populate when full channel metadata is loaded or refreshed; fast feed updates preserve previously saved handles.
- Added microphone search on phones and TVs using an installed speech-recognition provider.
- Added search within feed-group channel lists and optional cleanup after leaving the app.

### Fixes

- Restored video geometry after seamless autoplay and improved miniplayer surface restoration.
- Restored Invidious descriptions and comment avatars for additional response formats.


## WizeStream 1.36.0 (`1036000`)

### Improvements

- Added searchable caption transcripts with language selection, tap-to-seek, copying, and editable timestamped video notes. Captions load on request and stay in memory.
- Added profile-scoped smart playlists with watched-state, duration, title, subscribed-channel, and Learning-content rules. Results update from locally indexed feeds, playlists, and history.
- Added opt-in offline-library cleanup for newly tracked automatic channel downloads, with per-channel retention, storage limits, watched-file cleanup, permanent protection, and a manual deletion preview. Manual downloads and imports are excluded.
- Added explicit recovery of the current and previous playback queues per profile, including the saved position. Queues stay on-device for seven days, require watch history, and are never saved in Incognito.
- Added LibreTube JSON backup imports with a preview and selectable subscriptions, groups, history, playback positions, and playlists.
- Added text-file imports of YouTube video URLs into new or existing local playlists, with a preview, duplicate handling, and per-line results.
- Added Default, Blue, Green, and Purple launcher icons with matching Android TV banners under Appearance settings.
- Added a charging-only option for scheduled device synchronization; manual sync and incoming requests remain available.
- Increased explicitly trusted Invidious media servers to 16 per instance.

### Fixes

- Preserved progressing live buffers during recovery and prevented quality feedback from resetting working playback.
- Improved recovery from AAC decoder output-buffer failures.
- Restored supported Invidious thumbnail proxy URLs without falling back to direct YouTube requests.
- Fixed Android 10 video-detail controls with a scoped software-rendering layer.
- Restored SoundCloud chart playlist pagination.
- Kept the Android TV mini-player reachable while browsing.

## WizeStream 1.35.0 (`1035000`)

### Improvements

- Expanded theme packs with version 2 glass surfaces, native listening-player styles, and embedded PNG skins, artwork, and icons. Added the Glass Record preset and updated creator documentation. Packs customize supported native templates; arbitrary layouts and effects are not supported.
- Added read-only YouTube live chat in a video-details tab and fullscreen panel.
- Added preferred audio quality with a separate override for metered networks.
- Added comment filters, bounded regular-expression rules, and more reliable channel blocking.
- Added YouTube channel Releases browsing and optional player seek buttons.
- Displayed YouTube content-origin disclosures below video descriptions when supplied by YouTube.
- Added explicitly trusted HTTPS Companion media servers per Invidious instance, with setup documentation and protection against forwarding instance credentials. Direct YouTube requests remain blocked while using Invidious.

### Fixes

- Corrected post-live DVR classification, shortened live/post-live metadata caching, and preserved duration and available quality controls for manifest-backed archives.
- Increased live buffer headroom after rebuffering to help reduce recurring pauses on unstable connections. Playback may move farther behind live after a stall; results still depend on the connection and stream.
- Fixed authenticated SOCKS5 proxy connections on Android.
- Added recovery for AAC decoder output failures.
- Prevented orphaned settings dialogs after screen rotation.
- Constrained video surfaces to the current window bounds and preserved explicit link timestamps during playback startup.
- Fixed Android 10 detail-text rendering and retained undated YouTube feed uploads during feed extraction.
- Added a validated YouTube client-version fallback and handled missing avatar URLs safely.

## WizeStream 1.34.0 (`1034000`)

### Improvements

- Added optional on-device recommendations with a For you home tab, personalized Shorts and autoplay, and separate learning for each profile. Learning uses new activity after enabling the feature, respects history settings and Incognito, and includes pause, reset, and video/topic exclusions. Recommendation data is not uploaded or included in device sync or Android backups.
- Added community theme-pack import/export, day/night previews, and restoration of previous appearance settings under Settings → Appearance → Theme packs. Version 1 combines existing light, dark, AMOLED, Eye Comfort, and accent palettes.
- Added Forest, Ocean, and Midnight theme packs, plus a detailed creator guide, JSON Schema, and example files for sharing compatible packs.

### Fixes

- Fixed permission-related crashes when opening or sharing downloaded files, and handled unavailable file access without crashing.
- Applied default resolution and metered-network limits to live playback, preserved valid quality choices across manifest updates, and excluded unsupported tracks.
- Kept SoundCloud HLS streams available as a playback fallback when progressive streams are also listed.
- Kept Eye Comfort colours in player controls when system-colour accents are selected.

## WizeStream 1.33.0 (`1033000`)

### Improvements

- Added per-profile Kids Mode under Settings → Profiles, with a device-local parent PIN protecting settings and profile changes. Kids Mode disables YouTube search and subscription actions; it does not filter video content.
- Added Invidious community posts with text, photos, video and playlist attachments, read-only polls, and pagination. Post images use the selected Invidious instance without silently falling back to direct YouTube requests.
- Added topic chips to related videos using the current video's tags and description hashtags. Topics filter loaded recommendations locally, and All restores the original list without changing autoplay recommendations.
- Added an Allow AV1 video setting for devices where AV1 playback stutters or shows a black screen, while keeping other 60fps formats available.
- Added inline thumbnail-row selection in bookmarks and local playlists, kept editing controls visible while scrolling, and preserved subscription scroll position when changing edit mode or sorting.
- Replaced the feed header refresh control with a compact icon.
- Improved watch-history synchronization by reusing history snapshots within each sync batch.

### Fixes

- Kept Android TV remote controls inside the player lock and prevented locked remote actions from changing playback or escaping into underlying content.
- Restored swipe-down while video details are still loading when no scrolling child is available.
- Corrected video quality labels to reflect the selected format's actual frame rate.

## WizeStream 1.32.1 (`1032001`)

### Improvements

- Added bulk unsubscribe to the Subscriptions menu, with channel selection, search filtering, and a confirmation showing the selected count. Removal affects only the current profile.

### Fixes

- Restored SoundCloud playback for tracks requiring authorization and added support for unprotected 160 kbps AAC streams.
- Restored D-pad access to the fullscreen button when bottom player controls are enabled on Android TV.
- Kept Android TV fullscreen focus inside the player so navigation cannot scroll the video off-screen into underlying details or comments.
- Fixed playback queue artwork appearing as solid-colored rectangles due to inherited icon tint.
- Allowed swipe-down during media buffering and on loading or error details screens, and prevented hidden player controls from blocking the gesture.

## WizeStream 1.32.0 (`1032000`)

### Improvements

- Added profile merge and replacement with an affected-data preview, duplicate handling, and preservation of the source profile. Another profile can become the default, allowing deletion of the original Default profile.
- Isolated search history, suggestions, deletions, imports, exports, and device synchronization by profile. Existing search history stays in the Default profile.
- Added Incognito mode to pause recording searches, watch history, playback progress, and learning activity across profiles and services.
- Kept video visible in the minimized player and allowed minimizing while video details load.
- Added preferred audio and caption languages with a Show all languages option.
- Added Open album for YouTube Music tracks with available album metadata.
- Kept part of the video visible while browsing chapters using adaptive side and bottom panels.
- Saved available SponsorBlock segments with newly completed full downloads for offline skipping without modifying the media file.
- Added SDR video color inversion to playback adjustments, including Remember and Reset.
- Added an explicit, confirmed switch to direct YouTube playback after Invidious failures.
- Added optional background feed refresh across profiles, configurable intervals, and an unmetered-network option.
- Added copying subscription groups to another profile, subscription totals and Edit controls, and multi-item moves in bookmarks and local playlists.
- Added the yellow accent theme, Basque translation, a Material feed help dialog, and a larger feed header refresh control.
- Clarified Mark as watched behavior for scheduled videos.

### Fixes

- Show subscriptions from all services in the current profile, including edit mode.
- Restored missing upload dates for compact YouTube date labels and preserved fetched feed results when another tab or continuation fails.
- Allowed another DASH refresh after stable Full live playback to improve rewind preservation across separate buffering incidents.
- Moved synchronization response processing off network event loops to prevent slow history work blocking other categories.
- Updated channel metadata asynchronously without overwriting subscription settings, saved feed refreshes atomically, and preserved group channel positions.
- Supported current and legacy PeerTube avatar formats.
- Improved recovery from released video codecs and surface-detach timeouts, and ignored progress callbacks after player disconnection.
- Fixed legacy download folder selection, update-download cancellation and install prompts, and the Eye Comfort Appearance summary.

## WizeStream 1.31.9 (`1031009`)

### Improvements

- Added Allowed channels under Content blocking, with subscribed-channel selection and exceptions to keyword, channel, and AiSList hiding rules; explicitly blocked videos remain hidden.
- Added 60% size presets for video titles, channel names, metadata, and comment bodies.
- Added optional bold media and comment text under Appearance, preserving each view’s original typeface when disabled.
- Made Bookmarks and local playlist separators independent, reorderable rows, including consecutive and trailing headings, with migration of existing separators.
- Updated play queue controls to use the active Material primary color and kept the AMOLED header black in portrait and landscape.

### Fixes

- Fixed the startup crash after selecting Eye Comfort by using resource-backed theme colors.
- Fixed dragging interruptions in lists with separators and restored the original presentation row when undoing video removal.
- Added one DASH URL refresh with playback position preserved before Full live falls back to HLS after sustained buffering.

## WizeStream 1.31.8 (`1031008`)

### Improvements

- Added Eye Comfort (warm dark) under Appearance → Theme, with a fixed warm interface palette across services while leaving video colors unchanged.
- Added remote-accessible Move up and Move down actions to Customize home, preserving touch dragging and saving reordered sections.
- Added a headphones icon beside the selected audio-track name in the player.
- Added Edit/Done controls to Bookmarks and local playlists, with reorder handles available only while editing.
- Added an optional Content setting for swipe-to-remove playlist videos, disabled by default and active only in Edit mode; home-screen playlists retain horizontal tab navigation.

### Fixes

- Fixed the LIVE mode selector’s single-choice row inflation failure with player themes, including AMOLED styling.
- Preserved intervening section order when dragging home sections across multiple rows and guarded invalid positions.

## WizeStream 1.31.7 (`1031007`)

### Improvements

- Load seek-preview images only when needed, with a bounded cache to reduce background downloads and memory use.
- Added a setting to show the floating feed refresh button, hidden by default; enabling it replaces swipe-to-refresh and the upper refresh button, with theme-colored background and icon.
- Combined channel subscription and group actions into a split control.
- Added optional rounded upper corners to the collapsed mini-player, enabled by default under Appearance, with corners flattening as the player expands.
- Moved the Notes button beside Comments; tap opens video notes and long press adds a note at the current playback position.

### Fixes

- Fixed seek-preview frame selection for rectangular storyboard grids.
- Recreated feed card holders after orientation changes to prevent mixed portrait and landscape layouts after refresh.
- Fixed the LIVE mode selector crash on phones and Samsung DeX by attaching its dialog to the player window.

## WizeStream 1.31.6 (`1031006`)

### Improvements

- Added Normal and Full live playback modes, with a saved default and a mode selector on long press of LIVE; Full live can rewind through the history supplied by the stream.
- Displayed the current resolution beside Auto and added live automatic quality-change notifications at the top in portrait and bottom in landscape.
- Added Delete playlist to the local playlist menu, with confirmation and support for empty playlists.
- Added Copy all notes to the Notes tab and per-video notes dialog, preserving timestamps, Unicode, and line breaks.
- Enabled adding and editing video notes during playback, with long press of the note button starting a note at the current position.
- Added note-text search from Bookmarks and local playlists, with matching videos shown once and notes accessible on long press.
- Added named separators to Bookmarks and local playlists, with rename/delete on long press, per-profile storage, and headings excluded from playback queues and video counts.
- Added a floating feed refresh action, including Cancel during refresh and remote access by pressing Up from the first video row.
- Added 50% size options for video titles, channel names, video metadata, and comment text.
- Added long press of Open in browser to open on-demand YouTube videos at the current playback timestamp.

### Fixes

- Protected the selected Learning lesson when reopening a new detail screen and ignored late player callbacks after dismissal.
- Removed the TV focus effect that could tint the video or add white edges while retaining focus outlines on controls.
- Preserved repeated directional seeking on TV after seek feedback appears, while retaining navigation when controls are explicitly opened.

## WizeStream 1.31.5 (`1031005`)

### Improvements

- Added Cyan to the theme color options for light, dark, and AMOLED themes.
- Made Auto list view use Card view unless the active layout selects a large landscape grid.
- Added monthly search collection for a selected year, with a shared page limit, cancellation, and duplicate removal.
- Added adaptive two-pane Settings and Learning overview layouts in windows at least 840 dp wide.
- Reduced YouTube loading overhead by overlapping independent metadata requests with playback extraction and replacing CPU polling with completion waits.
- Removed the recurring Keep Android Open startup dialog.

### Fixes

- Kept the selected Learning lesson during player reconnection.
- Attached DeX and desktop player dialogs to the player window to improve their visibility.
- Added one stream URL refresh for sustained live HLS buffering stalls while preserving playback position and pause state.
- Fixed asynchronous response completion races and preserved live timing when metadata arrives before playback selection.

## WizeStream 1.31.4 (`1031004`)

### Improvements

- Added optional fullscreen playback dimming after 1, 5, or 10 minutes of inactivity, with continued audio and wake-up by touch, remote key, or accessibility action; disabled by default.
- Added separate portrait and landscape video layouts and grid column counts under Appearance, available on phones without enabling Tablet mode.
- Preserved the visible list position when changing layouts on rotation and included the orientation-specific preferences in device sync.
- Updated the play queue with Material 3 surfaces, rounded thumbnails, two-line titles, a clearer playback icon, and accessible drag handles.
- Replaced the decorative empty-list face and message with a simple "No items to show" message.

### Fixes

- Applied saved channel intro skips when resuming inside the intro, while respecting explicit timestamp links, manual seeks, and positions beyond the intro.
- Opened player choices in themed Material dialogs in DeX and desktop mode to address menus that did not open, with safe dismissal during player teardown.

## WizeStream 1.31.3 (`1031003`)

### Improvements

- Added optional independent player volume for quieter playback on earbuds, with a saved level that works with mute, audio focus, equalizer headroom, and sleep timer fades.
- Updated the speed and pitch dialog to Material 3 with themed sliders and wrapping selection chips.
- Removed the 100% speed/pitch adjustment step; retained the 1%, 5%, 10%, and 25% choices.
- Added a compact, focusable CC control for subtitle selection when captions are available.
- Improved portrait player metadata spacing and readability.
- Added custom search batches of 1–250 additional pages and configurable request delays of 0.3–10 seconds, defaulting to 2 seconds.
- Added subscribe and unsubscribe actions for channels inside subscription groups.

### Fixes

- Replaced cascading player choice menus with flat anchored lists to address the DeX quality-menu crash.
- Kept speed/pitch selection labels visible on narrow dialog layouts.
- Preserved underlying stream and manifest extraction errors in copied reports and explained Invidious responses containing no media formats or manifests.
- Redacted URLs and bounded Invidious API error details consistently, including errors returned with successful HTTP status codes.
- Kept usable YouTube streams playable when watch-next metadata requests time out, with one metadata retry before continuing without optional details.
- Enabled the existing one-shot DASH-to-HLS recovery for sustained live playback stalls in the background and rearmed recovery when resuming while already buffering.
- Added rate-limited debug diagnostics for live media loading and buffer state without logging signed media URLs or request headers.
- Updated BiliBili video metadata requests to the public WBI endpoint to reduce legacy-endpoint risk-control failures while preserving playback authentication and multipart selection.

## WizeStream 1.31.2 (`1031002`)

### Improvements

- Replaced the long search filter popup with compact chips in expandable sections.
- Added saved filter presets with load, update, and delete actions, separated by profile, service, and YouTube Music mode.
- Added custom YouTube upload-date ranges and relative days, weeks, months, and years, resolved once per search and retained across pagination.
- Added longest-first and shortest-first search result ordering with optional minimum and maximum durations in hours.
- Added cancellable deeper search with progress, retained partial results, duplicate removal, and bounded collection of additional pages.
- Added a Last lesson watched card to Learning progress, with course context and progress, resuming unfinished lessons and replaying completed lessons.
- Updated the Subscribe button with theme-aware outlined and filled states for light, dark, AMOLED, and Material You palettes.
- Added support for proxied Invidious DASH-only livestream manifests while retaining instance-only media requests.

### Fixes

- Prevented native player hover tooltips from crashing in DeX and other desktop windows.
- Kept navigation surfaces and their system-bar backing pure black in AMOLED mode.
- Applied updated per-channel intro and outro skip settings during playback, including after pause and resume.
- Parsed Odysee publication timestamps supplied as SDK strings.
- Recovered safely from unreadable main-tab saved state.
- Explained Invidious API access failures and unusable media or manifest responses with instance-switching guidance.
- Prevented truncated DASH XML from stalling manifest parsing and retained network-error handling.

## WizeStream 1.31.1 (`1031001`)

### Improvements

- Added inline subscription sorting with drag handles, multi-selection, and bulk moves under existing or new separators.
- Preserved selected channels and their relative order across search and service filters while saving layouts automatically per profile.
- Added search to blocked channels, videos, and keywords.
- Added saved Invidious instances and a quick instance selector.

### Fixes

- Restored fullscreen when replaying videos on Android TV and made Back close embedded playback.
- Reported video output surface dimensions to video effects.
- Prevented rejected Invidious suggestion requests from interrupting search and clarified API failures.
- Loaded Invidious feeds through paginated channel tabs, including enabled video, Shorts, and live-stream tabs within existing feed limits.

## WizeStream 1.31.0 (`1031000`)

### New features

- Added an Arrange subscriptions editor with drag-and-drop ordering and named separators, independent of channel groups.
- Saved subscription layouts separately for each profile and included them in full settings backups.
- Added timestamp playback and text-only copying through long-press gestures in video notes.

### Improvements

- Added separate timestamp, text, edit, and delete controls to the per-video notes dialog.
- Kept the fullscreen button visible on phones and desktop displays.

### Fixes

- Prevented player popup menus from opening on detached views or invalid windows and dismissed them during player teardown.
- Applied fullscreen entry and exit directly in DeX and multi-window mode, including Back navigation.
- Prevented desktop windows from re-entering fullscreen automatically because of landscape orientation.
- Refreshed YouTube media URLs after CDN connection failures, preserving playback position and limiting recovery attempts.

## WizeStream 1.30.0 (`1030000`)

### New features

- Added an optional Invidious backend for YouTube.
- Added an optional external downloader.
- Added a quick profile switcher to the navigation drawer.
- Added saved-note indicators to video thumbnails.

### Improvements

- Made per-channel intro and outro skip durations available from the playback speed dialog.
- Used a Material switch for new-stream notifications.

### Fixes

- Kept scrolling responsive while marking videos as watched.
- Restored video output when replaying the current item and improved video-surface cleanup.
- Made TV Back dismiss player lists and controls before exiting fullscreen, without a timed double press.
- Restored the player channel name and removed tinting from uploader avatars.
- Used Odysee publication dates before claim-update timestamps.
- Added an HLS fallback for live DASH HTTP 403 failures after refresh retries.
- Validated profile identities consistently across device synchronization categories.

## WizeStream 1.29.0 (`1029000`)

### New features

- Added an optional all-platform scope for What's New and channel groups; the current-platform scope remains the default.
- Added per-channel intro and outro skip durations, disabled by default.
- Included lesson difficulty settings in backups and added optional synchronization of ratings between matching profiles.

### Fixes

- Validated profile synchronization identities consistently across Android versions.
- Added a dark branded starting window on Android versions before Android 12.
- Preserved muxed video selection during temporary background playback, including Odysee streams.
- Kept the fullscreen button visible on Android TV when tablet mode is disabled.

## WizeStream 1.28.0 (`1028000`)

### Improvements

- Added a Video metadata text size setting for views, upload dates, and duration badges.
- Restored D-pad focus movement between fullscreen player controls and options on Android TV.

### Fixes

- Displayed video controls for manifest-only YouTube live streams instead of an audio-only icon.
- Added a one-time HLS retry when a manifest-only YouTube live stays buffering after playback begins.

## WizeStream 1.27.0 (`1027000`)

### New features

- Added an assignable Sync now remote shortcut that runs device synchronization without leaving playback.
- Added an optional animated Wavy player slider under Video and audio settings; Standard remains the default.
- Added timestamped sharing, browser opening, and link copying for non-live YouTube playback.
- Added per-video notes to saved-playlist menus, available without Learning mode and shared across playlists containing the same video.

### Improvements

- Added an explicit Refresh all now action while normal What's New refreshes respect the configured update threshold.
- Improved feed focus entry and kept player directional shortcuts within playback controls.
- Added clearer profile catalog validation diagnostics without exposing profile identities.

### Fixes

- Fixed channel description/About-tab crashes and the BitChute trending tab icon crash.
- Refreshed cached stream information after stale YouTube DASH manifests.
- Improved Odysee claim validation and handling of missing optional stream metadata.
- Applied the saved night mode on initial activity creation and used the active theme background for feed and subscription pages.
- Minimized embedded playback on Back and released forced phone orientation when collapsing the player.

## WizeStream 1.26.1 (`1026001`)

### New features

- Added playlist-wide Learning notes browsing, editing, deletion, and export as plain text or CSV.
- Added optional, per-profile lesson difficulty ratings with searchable review, hardest-first ordering, and Hard-only shortcuts. Ratings are stored locally and are not synchronized between devices.
- Added Subscribe and Channel groups actions to remote-video long-press menus.

### Improvements

- Added a bottom-sheet recommendations grid on TV when pressing Up during fullscreen playback with controls hidden, plus explicitly assignable D-pad shortcuts.
- Added uploader avatars to player metadata, enlarged related-video thumbnails, reduced channel details, and added a 70% title/channel text-size option.
- Updated subscription menus and the group reorder dialog to Material 3 styling.

### Fixes

- Backfilled older videos during full-channel What's New refreshes and prevented undated Shorts from crowding source-dated videos out of the feed's 500-item selection.
- Avoided redundant video-surface rebinds that could trigger Media3 detach timeouts on Android 13 and newer.
- Restored content-aware portrait and square-video fullscreen on phones.
- Kept channel avatars and titles visible during group selection and preserved the picker's scroll position.
- Repaired Odysee JSON-RPC requests, API error handling, channel Videos tabs, and continuation loading for subscription feeds.

## WizeStream 1.26.0 (`1026000`)

### New features

- Added a Learning home tab, course navigation, daily reminders, and per-profile Learning inclusion.
- Added an optional SmartTube-inspired fullscreen player layout and customizable remote buttons for playback and navigation.

### Improvements

- Kept fullscreen comments and replies beside the playing video instead of minimizing playback.
- Improved group-channel browsing and media display controls across phones and Android TV.
- Kept Device Sync discovery on physical LAN networks when a VPN is active.
- Improved native Picture-in-Picture so portrait video remains visible as the PiP window resizes.
- Added safer watch-history deletion and new search-history controls.

### Fixes

- Refreshed subscriptions and groups immediately when switching profiles.
- Restored live rewind and prevented silence skipping from interfering with live playback.
- Prevented normal search queries from being misidentified as Odysee links and handled services without a default kiosk at startup.
- Stopped undated Shorts from appearing as newly published and preserved explicit Shorts metadata so ordinary short-duration videos remain Videos.

## WizeStream 1.25.0 (`1025000`)

### New features

- Added an experimental Odysee service integration.

### Improvements

- Improved Android TV fullscreen D-pad navigation between playback controls and the action row.
- Made fullscreen comments panels more compact on TVs and phones.
- Applied the Subscriptions grid column presets to feed groups as well as subscribed channels.
- Improved native-library compatibility with Android devices using 16 KB memory pages.

### Fixes

- Restored SoundCloud charts using public chart playlists when the legacy charts endpoint is unavailable.
- Fixed a crash when opening the channels inside a feed group.
- Kept fullscreen exit available after playback finishes, including Back, the exit button, and the swipe-down gesture.

### Known issues

- Odysee search, playback, channel browsing, and subscription refresh may fail. Request handling, URL recognition, and pagination fixes are still pending.

## WizeStream 1.24.0 (`1024000`)

### New features

- Added a privacy-friendly GitHub star prompt that can open the project page without requiring a WizeStream account.
- Added direct access to the channels inside an opened subscription group.
- Added fullscreen comments in a side panel so video playback can remain fullscreen while browsing comments.
- Added configurable Subscriptions grid column presets alongside the responsive Auto layout.

### Improvements

- Improved Subscriptions search so both subscribed channels and feed groups are filtered by the current query.
- Improved Feed refresh progress with the current/total count and the channel currently being fetched.
- Improved Android TV playback with direct D-pad left/right seeking and double-Back player exit behavior.
- Improved Shorts thumbnail presentation so portrait artwork is preserved instead of zoom-cropped.
- Added subscription/group actions to searched channels and improved playlist thumbnail/title click targets.

### Fixes

- Show clearer content-unavailable messages for known removed, copyright-taken-down, community-guidelines, deleted, and unavailable videos while preserving real parser errors.
- Honor the global members-only hiding preference consistently in the Feed.

## WizeStream 1.23.0 (`1023000`)

### New features

- Added optional danmaku bullet-comment overlays for supported services.
- Added live-stream quality selection and adaptive Auto quality for supported streams.
- Added Local Media exclusions plus an option to group the All media view by filesystem folder.
- Added selectable Device Sync categories so individual data groups can be enabled or disabled per paired device.

### Improvements

- Modernized dialogs, settings, cards, chips, playback controls, search surfaces, and shared widgets across WizeStream with Material 3 styling and typography.
- Improved Android TV D-pad navigation and moved Mark as watched / Mark as unwatched higher in stream action menus for easier TV access.
- Added NicoNico live discovery and clearer reporting when subscriptions fail to refresh.
- Improved native Picture-in-Picture transitions and kept the Android navigation area transparent in landscape.
- Improved fullscreen behavior, adaptive video quality selection, decoder fallback, and transient audio-output recovery.

### Fixes

- Restored long-press actions for searched bookmarked playlists, including Tablet Mode Grid view, while keeping reordering disabled during filtering.
- Kept fullscreen letterbox areas black and made explicit fullscreen entry consistently target landscape.
- Guarded search refreshes after view teardown and repaired legacy profile timestamp values during migration.

## WizeStream 1.22.2 (`1022002`)

### Improvements

- Added “Subscribe to profile…” to the Subscriptions-tab long-press menu when multiple local profiles exist, without switching away from the active profile.
- Added an optional “Also match channel names” Content Blocking setting so blocked keywords can also match uploader and channel names while preserving the existing title-only behavior by default; the setting is synchronized through Device Sync.
- Improved channel pages on phones in landscape by hiding the banner and allowing the metadata header to scroll away, leaving more room for the video list.

### Fixes

- Prevented double-tap fast seeking from crashing on affected Android resource configurations when the internal seek counter resets to zero.
- Improved Rumble playback reliability by reusing the active session headers for page requests.
- Handled incomplete PeerTube API responses more safely instead of failing the whole request.

## WizeStream 1.22.1 (`1022001`)

### Improvements

- Applied the existing default, popup, and mobile-data video resolution preferences consistently across supported video services, including PeerTube and Rumble, with nearest-quality fallback when the exact resolution is unavailable.
- Kept BitChute videos playable when BitChute marks them as restricted while preserving the existing restricted-content preference behavior for other services.
- Refined the optional Wavy feed refresh indicator so the inactive track stays straight and only the active progress wave moves.

### Fixes

- Fixed a crash when opening Settings > Content after the members-only preference was moved into Content Blocking.
- Prevented Rumble tracking redirects from looping while resolving stream URLs.
- Recovered safely from stale main-tab fragment state after activity or navigation restoration.

## WizeStream 1.22.0 (`1022000`)

### New features

- Added channel actions for assigning subscriptions to feed groups and subscribing a channel to another local profile without switching profiles.
- Added device synchronization for blocked videos, blocked channels, blocked keywords, blocking targets, and AiSList behavior while keeping downloaded AiSList data local to each device.
- Added members-only feed visibility controls, including a global Content Blocking preference synchronized across devices and a feed-specific visibility choice.
- Added an Appearance option to choose Wavy or Line for the feed refresh indicator, with Wavy as the default.

### Improvements

- Hardened profile deletion behind a neutral Profile actions entry while retaining the final confirmation step and protecting the Default profile.
- Added bounded retries for transient YouTube channel-feed server errors while keeping permanent client errors non-retryable.
- Polished stream action dialogs with Material 3 surfaces, typography, and touch targets.
- Reused the active decoder across repeated Video Adjustments off/on cycles to avoid unnecessary player rebuilds.

### Fixes

- Restored PeerTube TLS compatibility on Android 6 and 7.0 while preserving normal hostname verification and modern Android trust behavior.
- Prevented Android 17 foreground-service restart crashes in the persistent Device Sync listener.
- Restored uncropped bookmarked playlist artwork in Card and Grid views.
- Recovered the video surface after native Picture-in-Picture entry to prevent intermittent black video, especially with portrait streams.
- Kept What’s New feed cards consistent when switching or refreshing List, Card, and Grid layouts.

## WizeStream 1.21.0 (`1021000`)

### New features

- Added local profiles so subscriptions, feed groups, watch history and progress, playlists, saved search feeds, imports, backups, and device synchronization can stay separated between profiles.
- Added on-device comment translation that keeps translation local to the device and hides the Translate action when a comment is already in the selected language.
- Added optional AiSList-based labeling, warning, or hiding for likely AI-generated YouTube content with local matching and no WizeStream account requirement.

### Improvements

- Made backup, restore, NewPipe/PipePipe/Takeout migration, and peer-to-peer device synchronization profile-aware while preserving compatibility with the Default profile.
- Made content-blocking refreshes target-aware so blocking a channel or video refreshes Related immediately without unnecessarily reloading Comments.
- Made bookmarked playlist and YouTube Music album artwork more resilient by falling back from playlist artwork to track artwork and then uploader artwork while preserving valid stored covers.

### Fixes

- Prevented Android service restarts with a null PlayerService intent from crashing playback startup.
- Fixed repeated Video Adjustments rebuilds on Android 9 by giving each ExoPlayer its own LoadControl and preserving the latest playback position.
- Corrected comment-translation action alignment and long-text layout handling.

## WizeStream 1.20.2 (`1020002`)

### Improvements

- Expand large-screen and TV grid density with 5–7 column choices and optional compact navigation.
- Refine Material popup contrast and typography across shared popup surfaces.
- Combine feed filters and refresh controls into one compact row, showing the full-width loader and cancel action only while refreshing.
- Support PeerTube channel video tabs.

### Fixes

- Restore YouTube and YouTube Music playlist or album artwork when page-header hero-image metadata is returned.
- Recover unsupported legacy player resize preferences instead of crashing playback startup on affected TVs and Android devices.
- Keep paired devices reachable for background synchronization with a persistent trusted-device listener.
- Handle missing or null avatar metadata without crashing image loading.
- Make rotation-driven fullscreen content-aware so portrait and near-square videos are not forced into landscape fullscreen.
- Keep manually entered portrait-video fullscreen locked to portrait until fullscreen exits.

## WizeStream 1.20.1 (`1020001`)

### Improvements

- Keep feed refresh non-blocking with a compact progress strip while the feed and filter controls remain usable.
- Modernize stream-action, toolbar, and queue popup surfaces with rounded Material styling and the active theme palette.

### Fixes

- Prevent video-adjustment restarts from reusing a Media3 track selector across replacement players, fixing Android 9 crashes while preserving adjustment values.
- Repair fullscreen enter and exit when orientation and split/two-pane layout state become temporarily inconsistent.
- Restore full-backup import on the current database schema, including Android TV devices.
- Recover device sync from transient peer disconnects by refreshing LAN addresses and retrying the failed category once.
- Restore YouTube and YouTube Music playlist or album artwork in Bookmarked Playlists when the newer playlist metadata layout is returned.

## WizeStream 1.20.0 (`1020000`)

### New features

- Choose Fixed, Follow system rotation, or Follow device sensors for player rotation, while preserving the previous rotation preference.
- Sort the subscription feed by publication date or first discovery, preserving discovery times when videos are refreshed.

### Fixes

- Import channel groups and their memberships with subscriptions from NewPipe and PipePipe database backups, preserving existing groups and channels.
- Fall back to normal playback when video-adjustment effects cannot be installed, and keep crash reporting independent of automatic-download initialization.
- Improve contrast between played, buffered, and remaining seek-bar ranges across player modes and themes.
- Restrict saved CAPTCHA and restricted-mode cookies to secure YouTube destinations, including redirected requests.
- Play inline subtitle text produced by Bilibili and YouTube caption deduplication without downloading it again.
- Register the media session during internal playback startup so Android can receive the configured custom notification actions.

## WizeStream 1.19.0 (`1019000`)

### New features

- Added Video adjustments to the expanded player controls, with live brightness, contrast, and
  saturation sliders, Reset, and optional remembered settings for local and streamed SDR videos.
  Adjustments carry across fullscreen, rotation, popup, and PiP. HDR bypasses the adjustments,
  and processing failures fall back to normal playback.

### Improvements

- Added a prominent Search settings entry and search access inside settings categories.
- Improved settings search with localized names, descriptions, available choices, and category paths,
  including caption translation and SponsorBlock categories. Results open and highlight the option;
  going back restores the search, and the query survives rotation.
- Updated playback and image-loading components.

### Fixes

- Restored the correct player height, system bars, and detail tabs when returning in portrait after
  turning the screen off in landscape fullscreen. Fixed rotation into fullscreen becoming stuck
  after this sequence.
- Removed the unintended "I-albhamu - " prefix from YouTube Music album titles while preserving
  user-created playlist names.
- Fixed NicoNico video and background-audio playback authentication, including fresh session cookies
  for playlists, segments, and encryption keys. Restored the final quality option in HLS playlists.
- Fixed the player seek bar retaining the default green color with wallpaper-based themes, including
  Black mode and popup playback. Its colors now refresh when the selected theme changes.
- Kept the rounded settings search field inset from the screen edge.
- Fixed circular search-field theme colors that could still crash copying text on Android 9/ColorOS.
- Restored inherited highlight colors in toolbar popups and search suggestions.

## WizeStream 1.18.0 (`1018000`)

### New features

- Added a bundled, offline changelog and a first-launch release-notes dialog with access to the full
  release history from the dialog, navigation drawer, and About.
- Added downloads for selected time ranges and chapters.
- Added optional automatic channel downloads on unmetered connections.
- Added discovery personalization and automatic playback of available downloaded copies.
- Added Google Takeout import for YouTube playlists, subscriptions, watch history, and search history.

### Improvements

- Remembered the selected YouTube comment sorting mode.
- Expanded NewPipe-compatible exports to include local and bookmarked playlists, channel groups,
  and search history.

### Fixes

- Fixed orientation synchronization when leaving fullscreen with Back.
- Kept download, playlist, and channel-group dialogs consistent with the active system color palette.
- Made screen backgrounds follow the active Material surface color while preserving AMOLED-black mode.

## WizeStream 1.17.0 (`1017000`)

### New features

- Added scheduled automatic backups and NewPipe-compatible data export.
- Added a fullscreen touch lock to prevent accidental player gestures.
- Added multi-select actions in search results, channel pages, and feeds.
- Added playlist categories to organize and filter bookmarked playlists.
- Added optional loudness normalization and compression for more consistent audio levels.
- Added **Top** and **Newest** sorting for YouTube comments.

### Improvements

- Made All playlists and category/search results follow the selected List, Card, or Grid view mode.
- Matched bookmark backgrounds, category controls, and rounded thumbnails to the active app theme.
- Preserved playlist layouts while filtering and kept drag-to-reorder available in every view mode
  when viewing all playlists.

### Fixes

- Improved recovery for YouTube livestreams that provide only a streaming manifest.
- Kept Shorts auto-queues limited to short-form videos.
- Fixed a crash when opening the playback queue in landscape.
- Fixed phones being incorrectly detected as desktop windows when entering fullscreen.
- Corrected the theme used by copy-notification toasts on older Android versions.

## WizeStream 1.16.1 (`1016001`)

### New features

- Added an **Upcoming** stream filter and controls to show or hide live streams, videos, and Shorts
  in What’s New.

### Fixes

- Restored Shorts that had missing dates in subscription feeds and distributed their approximate
  dates so they no longer dominate the top of What’s New.
- Fixed subscription-file imports and restored subscription migration from NewPipe and PipePipe
  backups.
- Added automatic recovery when a YouTube HLS playlist becomes stuck during playback.
- Smoothed audio visualizer animations after the Media3 migration by pacing decoded audio frames.

## WizeStream 1.16.0 (`1016000`)

### New features

- Added an **Apply blocking to** selector so content-blocking rules can be enabled separately for
  search results, kiosks and What’s New, related items, channel pages, subscriptions and channel
  groups, remote playlists, and comments.

### Fixes

- Fixed fullscreen requests being lost while the detail layout was recreated in landscape and
  restored custom notification actions when Android System UI connects to the media session.
- Prevented the player-start receiver from restarting the player service while WizeStream is in the
  background.
- Improved BiliBili risk-control, invalid-response, missing-metadata, and unavailable-video handling
  so these conditions produce clear errors instead of malformed playback states.

## WizeStream 1.15.2 (`1015002`)

### Fixes

- Prevented a native Android Runtime crash while OkHttp repeatedly parsed YouTube API hostnames on
  some Android 16 custom ROMs.
- Restored the video surface and correct aspect ratio after the screen is turned off and back on,
  preventing stretched or black video.

## WizeStream 1.15.1 (`1015001`)

### New features

- Added a setting to choose whether Android Picture-in-Picture or Popup Player is the primary
  floating-player action while keeping the other action available in the secondary controls.

### Improvements

- Strengthened player, extractor, and CI regression coverage and migrated additional low-risk
  components to Kotlin.
- Improved local folder and playlist artwork handling, including deterministic avatar reuse.

### Fixes

- Fixed notification button configurations not applying reliably, including empty and reordered
  action slots.
- Prevented cold-start media-button broadcasts from causing a foreground-service timeout crash.
- Prevented video-surface detach timeouts during player teardown from being reported as playback
  crashes.
- Kept the active video visible above scrolling details and corrected local folder artwork tinting.

## WizeStream 1.15.0 (`1015000`)

### New features

- Added YouTube caption auto-translation with a configurable target language.
- Expanded local music metadata and artwork support, including embedded OGG/Opus artwork and
  same-folder `cover`, `folder`, `albumart`, or `front` image fallbacks.
- Added one-tap local playlist shuffle plus direct Play All / Shuffle navigation into the Play queue
  for local albums, artists, genres, folders, and playlists.
- Added local-library navigation from the Play queue so users can jump back to all local songs.

### Improvements

- Migrated playback and media-session internals to AndroidX Media3 and decomposed the Player core
  into focused Kotlin controllers for improved maintainability and playback stability.
- Improved local-media metadata propagation across the player, Play queue, notifications,
  lock screen, and Android Auto.
- Moved backup import validation off the UI thread and decomposed structured preference sync storage
  to reduce blocking work and simplify synchronization maintenance.

### Fixes

- Forced live streams to play at normal 1.0x speed while preserving saved speed preferences for
  non-live videos.
- Fixed Share and Picture-in-Picture transitions that could force distorted fullscreen states, and
  restored Repeat / Close actions in Android System UI media controls.
- Fixed a crash when opening caption translation settings because the language preference registered
  its dependency before the parent preference was attached.
- Improved handling of YouTube anti-bot access blocks and unreleased premieres.
- Fixed local audio artwork covering the song title after playback starts.

## WizeStream 1.14.0 (`1014000`)

### New features

- Added a configurable global-search default so Search can prefer the current tab/context or the
  current streaming service.
- Kept the main bottom navigation or navigation rail available while Search is open, with direct
  handoff back to the selected main destination.
- Added a quick return-to-top action for What's New by reselecting the active What's New
  navigation destination.

### Improvements

- Remembered What's New stream filters separately for each subscription group across app restarts.
- Updated the live-stream-not-started prompt to use WizeStream's Material 3 dialog styling while
  preserving Close and Retry behavior.

### Fixes

- Fixed the fullscreen-orientation regression that could fail to enter fullscreen in landscape,
  remain fullscreen after returning to portrait, or leave a black video surface after exiting
  fullscreen on affected devices.
- Stabilized the video surface and fullscreen handoff so phone landscape rotation no longer gets
  interrupted by the wide video-detail layout transition.

## WizeStream 1.13.0 (`1013000`)

### New features

- Added a **Videos** filter to What's New so standard videos can be shown separately from Shorts.
- Added a default-enabled **Rotate device to enter fullscreen** option that responds to physical
  rotation even when Android's system orientation is locked.
- Expanded NewPipe/PipePipe migration with compatible settings and PipePipe SponsorBlock settings,
  while preserving history and local-playlist migration.
- Added a History-style Local Media toolbar with on-demand search, a compact rounded search field,
  an overflow menu, and a full local-media refresh/rescan action.

### Improvements

- Made the player PiP action enter Android's native Picture-in-Picture directly while keeping the
  legacy floating Popup player available separately.
- Smoothed fullscreen-to-mini-player transitions and restored normal device orientation when the
  fullscreen player is minimized.

### Fixes

- Prevented opening the Play Queue from triggering Picture-in-Picture and guarded PiP APIs on
  Android versions that do not support them.
- Improved local-video surface recovery after fullscreen changes, including a targeted fresh
  SurfaceView lifecycle for high-resolution videos affected by stale hardware-compositor surfaces.

## WizeStream 1.12.0 (`1012000`)

### New features

- Added import of history and playlists from NewPipe-compatible backups.
- Added YouTube Music search filter chips.
- Added adjustable comment text and optional drawer service sections.

### Improvements

- Improved tablet video details, fullscreen rotation, grid titles, and feeds.
- Polished refresh and watched-progress indicators.

### Fixes

- Fixed BiliBili playback, blocked keywords, comments, playlist swipes, search, and avatars.

## WizeStream 1.11.5 (`1011005`)

### New features

- Added optional full video titles in grid layouts.
- Added configurable tablet grid columns with responsive thumbnails.
- Added a copy-title action to video menus.

### Improvements

- Made automatic queues prefer Shorts when watching Shorts.
- Stabilized tablet video details after rotation.
- Made reproducible release builds portable across independent build environments.

## WizeStream 1.11.4 (`1011004`)

### New features

- Added a tablet setting to place app navigation on the left rail or bottom bar.

### Fixes

- Prevented Android 6 feed filtering failures when opening or refreshing What's New.
- Made tablet grids recalculate from the available content width after navigation and orientation
  changes.
- Selected the single- or two-pane video-detail layout from the current screen width and removed
  navigation-rail gaps from expanded and fullscreen playback.

## WizeStream 1.11.3 (`1011003`)

### Fixes

- Serialized the complete release pipeline, including Gradle, D8/L8, R8, and child JVMs, to
  prevent nondeterministic DEX interface ordering across independent build environments.
- Added a fail-fast processor-count check and limited reproducible release builds to one Gradle
  worker while retaining normal parallelism for debug and test builds.

## WizeStream 1.11.2 (`1011002`)

### Fixes

- Kept fullscreen video edge-to-edge while protecting interactive controls from status bars,
  navigation bars, and display cutouts.
- Kept video-detail tabs and content above system navigation and restored compact fullscreen seek
  feedback.
- Prevented long fullscreen titles, channel names, and audio-track labels from being clipped.
- Reset video-surface geometry between queue transitions so cached return previews fill the player
  correctly, including rotated and anamorphic video.

## WizeStream 1.11.1 (`1011001`)

### Fixes

- Kept the expanded video-detail player below the status bar and prevented the parent screen from
  covering it during loading transitions.
- Restored compact player-overlay text and icons while preserving 48dp accessible touch targets.
- Corrected the collapsed mini-player layout and hid content behind an opaque system-navigation
  surface.
- Hardened YouTube search suggestions and feed view-count parsing against malformed metadata.
- Prevented preference updates from reaching a destroyed feed view and handled unsupported channels.
- Prevented Android 6 device-sync linkage failures, bounded the image cache, and reused player
  thumbnails to reduce memory pressure.
- Rejected incompatible foreign databases before they could replace WizeStream data.

## WizeStream 1.11.0 (`1011000`)

### New features

- Added BitChute and Rumble browsing, search, channels, comments, live streams, and playback.

### Improvements

- Added direct notification keyword management to subscribed channel menus.
- Polished feed refreshing with a translucent overlay, circular progress, and immediate cancellation.
- Updated colors, typography, components, seekbars, and navigation indicators to Material 3.
- Added adaptive bottom navigation and navigation rails for phone, tablet, and landscape layouts.
- Improved player-control accessibility and touch targets.

### Fixes

- Prevented feed refreshes and filters from crashing when the displayed list shrinks to zero items.
- Prevented the circular feed refresh indicator from being clipped along its edges.
- Handled Rumble videos that do not provide related items.
- Kept navigation and drawer content clear of status bars, display cutouts, gesture navigation,
  and three-button navigation while preserving fullscreen playback.

## WizeStream 1.10.4 (`1010004`)

### New features

- Enabled BiliBili and NicoNico as selectable streaming services.
- Added reusable saved-search feeds with service-specific filters, persistent bounded result
  caching, pagination, deduplication, manual refresh, and cached-result fallback.

### Improvements

- Reproducible release APK builds via pinned toolchain.
- Auto-refreshes Subscriptions/What's New on service switch.

### Fixes

- Instantly halts deleted downloads & clears temp files.
- Fixed empty-body POST requests in extractors.
- Prevented FCast crashes from missing stream manifests.
- Handled BiliBili risk-control blocks & fallbacks.
- Restored YouTube HD, audio & download options.
- Added Retry dialog for unstarted livestreams.
- Fixed layout overlap in portrait videos.  

## WizeStream 1.10.3 (`1010003`)

### Fixes

- Restored live playback when a service provides only an HLS or DASH manifest without separate
  audio or video stream entries.
- Kept manifest-only YouTube live streams playing by preferring their refreshable HLS source over
  a finite DASH DVR window.
- Fixed selected bottom-navigation labels occasionally being truncated until the app was
  restarted.

## WizeStream 1.10.2 (`1010002`)

### Fixes

- Restored playback for YouTube videos incorrectly reported as unavailable by using the standard
  Android Reel request path and retaining the existing client fallbacks.
- Retried transient YouTube page-reload responses once automatically before showing an error.
- Bounded persistent database growth by clearing feed caches and unreferenced stream metadata,
  compacting inactive sync journals while preserving paired-device synchronization state.

## WizeStream 1.10.1 (`1010001`)

### Improvements

- Sped up MP3 encoding while retaining the selected output quality.
- Kept caption controls accessible on small screens.

### Fixes

- Restored audio downloads when only muxed MP4 streams are available and made the download dialog
  fit its content while remaining scrollable on smaller screens.
- Stopped active or queued downloads immediately when their entries are deleted, while preserving
  the previous state when the deletion is undone.
- Restored channel avatars and banner artwork, including reliable banner rendering inside the
  collapsible channel header.

## WizeStream 1.10.0 (`1010000`)

### Fixes

- Fixed the bottom navigation occasionally remaining translated off-screen after an interrupted
  player-sheet transition, leaving a clipped blank area until the app was restarted.
- Fixed NewPipe subscription imports failing completely when one channel is unavailable; valid
  channels now import independently and the result reports imported and skipped counts.
- Made history date scrolling transient, consistently show month and year, and process large
  histories without blocking the interface or unnecessarily reloading the database.
- Fixed Material You wallpaper colors falling back to green with the Black night theme, while
  preserving true-black surfaces, and made the player seek bar follow the selected accent color.

### Improvements

- Moved the History date scrollbar closer to the screen edge while preserving its accessible touch
  target, and added smooth cancellable fade transitions.

### New features

- Added separate x86_64 release APKs for Waydroid and Android-x86 environments while keeping the
  existing ARM release download unchanged.
- Added optional on-device MP3 audio downloads at 128, 192, 256, or 320 kbps, with conversion
  progress, cancellation-safe temporary files, and title, uploader, and source URL metadata.
- Added per-channel keyword and phrase filters for new-stream notifications.
- Added date-aware fast scrolling and a calendar jump action for navigating large watch histories.
- Added an option to prioritize main-tab swiping when viewing pinned channels.

## WizeStream 1.9.1 (`1009001`)

### Fixes

- Kept channel metadata and Subscribe controls reachable while the banner collapses, including in
  landscape and on channels without banners.
- Fixed YouTube Android VR AV1/HFR streams repeatedly failing with HTTP 403 by temporarily falling
  back to the nearest available stream without changing the saved quality preference.
- Fixed duplicate global and contextual search actions appearing together on channel pages.
- Fixed channel names and avatars not opening their channels from History and local playlists.

## WizeStream 1.9.0 (`1009000`)

### New features

- Added a first-class Listen mode that switches videos to audio-only playback without losing the
  queue or playback position and keeps the normal player controls available.
- Added a permission-free spectrum visualizer for Listen mode, driven directly by decoded player
  audio without microphone access.
- Added Android Auto media browsing and voice-search playback for audio-only listening, with video
  and visualization intentionally excluded from the driving surface.
- Added Android Auto media resumption, a bounded Continue listening section, car-friendly content
  style hints, and time-limited browse requests for a more reliable driving experience.
- Added continuous pinch-to-zoom from 100% to 400% and two-finger panning in the main player, with
  a default-enabled Behavior setting, while preserving the vertical two-finger speed gesture.
- Added opt-in native Android picture-in-picture for visible video playback on Android 8 and newer,
  with automatic Android 12+ entry, media-session controls, and popup-player fallback on older
  devices.
- Added app-wide HTTP and SOCKS5 proxy configuration for remote browsing, playback, images, and
  downloads, including securely stored proxy authentication and automatic local-network bypass
  for casting and device synchronization.

### Improvements

- Added 15 selectable Listen-mode visualizers and a distinct waveform icon so Listen mode is no
  longer confused with the Background player action.
- Fixed History filter chips not updating the displayed watched-video list.
- Added bulk video and audio downloads directly from local playlists; entries that already point to
  local media files are skipped.

### Fixes

- Fixed rotating a playing video to landscape no longer entering fullscreen after native
  picture-in-picture configuration handling was added.
- Fixed a crash when opening video details in landscape on large-screen devices.
- Fixed channel metadata and Subscribe controls disappearing on channels without banners.
- Fixed playback failing with an audio visualizer runtime error on newer Android versions.

## WizeStream 1.8.0 (`1008000`)

### New features

- Added bulk video and audio downloads for complete playlists and loaded play queues, with default-quality selection, collision-safe filenames, and optional track-number prefixes.
- Added local content blocking for individual videos, channels, and title/uploader keywords, with long-press actions and a dedicated management screen.
- Added in-page search for channel tabs, remote playlists, and local playlists.
- Added optional DeArrow titles and thumbnails for YouTube lists and video details, with request deduplication, in-memory caching, and automatic fallback to original metadata.
- Added TV casting to discovered FCast and Chromecast-compatible receivers from video details on
  Android 8 and newer.

### Improvements

- Embedded title, uploader, genre, upload date, and source URL metadata in generated M4A, MP4, and Opus downloads.
- Improved subscription group management with an Import shortcut and a clearer Material 3 creation and editing dialog with guided validation.

### Fixes

- Fixed channel and playlist search crashing when restored before the initial content load completed.

## WizeStream 1.7.1 (`1007001`)

### Improvements

- Improved recovery and client fallback for expiring or rejected YouTube media URLs.

### Fixes

- Fixed YouTube playback stopping with HTTP 403, including streams failing around the one-minute mark.
- Fixed media.ccc.de live streams failing to open.

## WizeStream 1.7.0 (`1007000`)

### New features

- Added local music and video browsing and playback, including search, filters, thumbnails, background playback, popup playback, and queue integration.
- Added local media information such as format, resolution, duration, capture date, and audio quality.

### Improvements

- Improved recovery from temporary YouTube playback failures by refreshing media URLs with bounded retries while preserving playback position.

### Fixes

- Restored YouTube comment author avatars.
- Fixed UTF-8 YouTube search queries.
- Fixed the app-data export picker on Android 8.
- Fixed bookmark removal before playlist loading completes.

## WizeStream 1.6.0 (`1006000`)

### New features

- Added a built-in 10-band audio equalizer with five presets, automatic clipping headroom, and one persistent custom curve.
- Added read-only YouTube channel posts with text, images, polls, links, attached videos and playlists, and continuation-page support.

### Improvements

- Improved device synchronization recovery after hotspot or local-network address changes.

### Fixes

- Restored channel metadata in portrait layouts.
- Fixed timestamped note button visibility during Learning Mode playback.

## WizeStream 1.5.0 (`1005000`)

### New features

- Added optional Learning Mode with timestamped notes, study sessions, playlist progress, and a learning dashboard.
- Added **Members only** badges for membership-restricted videos, an explanation when they are opened, and an option to hide them from content lists.
- Added swipe-to-remove with an Undo action for videos in local playlists.

### Improvements

- Restored the main-player position and playback state after returning from popup playback.

### Fixes

- Fixed repeated bookmark-removal actions and reduced memory usage when removing very large playlists.

## WizeStream 1.4.1 (`1004001`)

### Improvements

- Improved consistency between the main and video-detail navigation bars.

### Fixes

- Fixed a crash when displaying feed cards in landscape mode.

## WizeStream 1.4.0 (`1004000`)

### New features

- Introduced the new WizeStream visual identity across the launcher, splash screen, notifications, updater, and Android TV.
- Added channel avatars to stream lists and made channel identity areas open their channels.

### Improvements

- Restored conventional YouTube playback and downloading, removing SABR and its bundled runtime to reduce complexity and APK size.
- Optimized subscription feed refreshes by increasing lightweight RSS concurrency and replacing long blocking pauses with short, cancellable rate-limit delays only for full YouTube extraction batches.
- Made the two-finger playback-speed gesture respect the configured speed increment.
- Improved launcher-logo scale and themed-icon consistency.

### Fixes

- Fixed exiting fullscreen sometimes leaving the device locked in landscape orientation.
- Fixed release builds failing on duplicate Bouncy Castle license and notice metadata.

**Compatibility note:** YouTube playback and downloads now use conventional stream extraction. SABR support introduced in WizeStream 1.2.0 has been removed.

## WizeStream 1.3.0 (`1003000`)

### New features

- Added configurable bottom-navigation labels: **Always visible**, **Active only**, or **Hidden**.
- Added an optional two-finger vertical swipe gesture for adjusting playback speed.

### Improvements

- Optimized release APKs for ARM devices to reduce download and installation size.
- Improved YouTube Music and Movies/Shows Trending charts.

### Fixes

- Changed the default playback speed from **1.2× to 1.0×** for new installations. Existing saved speeds remain unchanged.
- Improved recovery from temporary YouTube media-server DNS failures while preserving playback position.
- Fixed playback stopping during longer SABR redirect chains.
- Added redirect-loop detection and bounded stream-information recovery.
- Locked single-finger player gestures to their initial direction, preventing volume or brightness gestures from unexpectedly becoming seeking gestures—and vice versa.
- Fixed Nightly release cleanup occasionally deleting the newly published build.

**Compatibility note:** Official release APKs now support ARM64 and ARMv7 Android devices. x86 and x86_64 device builds are no longer included.

## WizeStream 1.2.0 (`1002000`)

- Added YouTube SABR playback and downloading.
- Added dedicated YouTube Music and YouTube Shorts destinations.
- Separated Subscriptions and What's New by service, including independent refresh states.
- Added remote playlist sorting.
- Improved device synchronization and player transitions.
- Fixed WebView and large-download crashes, channel headers, playlist counts, and synchronization stability.

## WizeStream 1.1.1 (`1001001`)

- Redesigned local search with a dedicated floating button for searching the selected streaming service.
- Automatically carries the typed query into the online search.
- Keeps local and online search actions clearly separated.
- Improved search-button positioning above bottom navigation and bottom tabs.
- Pressing Done now dismisses the keyboard without switching search modes.

## WizeStream 1.1.0 (`1001000`)

- Added secure peer-to-peer sync for subscriptions, playlists, history, settings and download metadata, including automatic background sync.
- Added "Download on this device" for synchronized media missing locally.
- Added search filters, channel sorting, podcast tabs and per-channel playback profiles.
- Added multi-audio selection with clearer original, dubbed and descriptive labels.
- Improved live-stream playback and fixed startup, artwork and migration issues.

## WizeStream 1.0.0 (`1000000`)

- Adopted an independent semantic version and release cycle.
- Added unified filters for stream lists.
- Fixed subscription import when a source has no matching placeholder.
- Improved release and update validation.

## WizeStream 0.28.8-m14 (`101314`)

### Hotfix

- Fixed a crash when opening or restoring local search with no matching results.
- Added release-build and Android API 23/35 regression checks.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m13 (`101313`)

- Completed project-wide rebranding from NewPipe Material to WizeStream.
- Added sleep timer presets, custom time, end-of-video/queue and fade-out.
- Added search to subscriptions, playlists, feeds, history and Downloads.
- Modernized video details navigation while keeping playback visible.
- Hardened updates with checksum and APK identity checks.
- Fixed YouTube reloads, metadata and double splash on older Android.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m12 (`101312`)

- Added swipe down to the miniplayer without disabling fullscreen swipe, with a default-on toggle.
- Added an option to keep the video visible while scrolling.
- Added view and subscriber counts.
- Positioned the miniplayer above bottom navigation.
- Improved the splash transition, playlist dialog theming, and update dialogs on small screens.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m11 (`101311`)

- Fixed playback option checkboxes that could render incorrectly.
- Fixed the search keyboard not closing reliably on older Android versions.
- Improved the warning shown before enabling GitHub update checks.
- Pinned PipePipeExtractor to ensure consistent and reproducible builds.
- Unified local, CI, and external builds through shared build scripts and updated documentation.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m10 (`101310`)

- Added customizable Home navigation with Downloads and smarter drawer entries.
- Added per-category SponsorBlock behavior, colors, presets, and playback improvements.
- Polished SponsorBlock settings with app switches, aligned headers, and icons.
- Updated the launcher icon to follow the Android 12+ system palette.
- Improved update downloads on Android.
- Clarified GitHub update safety and handled missing browsers.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m9 (`101309`)

- Added a clearer in-app update flow with changelog preview, APK download progress, and install prompt.
- Improved Backup/Restore safety and user experience.
- Improved large-screen and landscape layouts with Material 3 styling.
- Updated launcher, splash, and TV banner artwork to use Material 3 colors.
- Added playback retry handling for HTTP 403/404 errors.
- Recognized WizeStream release builds correctly.
- Fixed PipePipeExtractor source checkout instructions.
- Disabled Android dependency metadata in APK builds.

Based on NewPipe 0.28.8.

## WizeStream 0.28.8-m8 (`101308`)

- Updated the app base version to NewPipe 0.28.8.
- Improved YouTube thumbnail, poster, and avatar loading.
- Improved YouTube embedded web playback request handling.
- Fixed channel tab pagination compatibility with the updated extractor.
- Improved release build stability.

Based on NewPipe 0.28.8.

## WizeStream 0.28.7-m7 (`101207`)

- Switched to the latest PipePipeExtractor source checkout to improve YouTube compatibility.
- Updated app integration for the new extractor API and Java 21 bytecode requirements.
- Improved YouTube live and post-live stream compatibility through the updated extractor.
- Improved YouTube rating compatibility when likes and dislikes are not provided directly by the extractor.
- Suppressed non-critical optional metadata extraction warnings in release builds when stream loading still succeeds.
- Continued extractor compatibility and release stability cleanup.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m6 (`101206`)

- Added an Updates settings screen with current version, installed app version, manual update checks, changelog viewing, and optional automatic background update checks.
- Improved update checks for WizeStream GitHub Releases, including prerelease-aware release selection and in-app manual check results.
- Added Swipe seek, Fullscreen swipe, and Hold to speed up player gesture settings under Video and audio.
- Added Show dislikes in Appearance, enabled by default where supported by the extractor.
- Added SponsorBlock settings, category controls, automatic skipping, manual skip overlay button, and seek-bar segment markers for supported YouTube segments.
- Improved YouTube quality availability through the updated extractor client strategy, allowing higher adaptive qualities such as 720p and 1080p when available.
- Continued Material settings polish and stability cleanup.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m5 (`101205`)

- Added helpful summaries to the main settings categories.
- Switched to the modified NewPipeExtractor source checkout with HLS fallback support to improve livestream playback reliability.
- Added README instructions for setting up the NewPipeExtractor source checkout.
- Fixed bottom navigation staying visible when only one main page tab is enabled.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m4 (`101204`)

- Cleaned up release workflow after pausing custom F-Droid repo publishing.
- Kept GitHub release/artifact publishing with APK signature verification.
- Shortened release changelog metadata for F-Droid readiness.
- Prepared release metadata flow for IzzyOnDroid and official F-Droid submission.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m3 (`101203`)

- Added localized Fastlane metadata and store assets.
- Updated screenshots and feature artwork.
- Improved player popup readability.
- Improved comment reply contrast.
- Expanded contribution and release QA docs.
- Cleaned up README release links.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m2 (`101202`)

- Added WizeStream version identity using the 0.28.7-m2 format.
- Updated release workflow to publish signed APKs from m-tags.
- Added per-release changelog support from `changelogs/m*.txt`.
- Improved GitHub release naming and APK artifact naming.
- Continued Material polish and stability cleanup.

Based on NewPipe 0.28.7.

## WizeStream 0.28.7-m1 (`101201`)

- First WizeStream release based on NewPipe 0.28.7.
- Added Material 3 visual polish across the app.
- Improved bottom navigation with Material-style bottom navigation behavior.
- Improved About screen fork attribution.
- Added WizeStream identity and fork attribution.
- Fixed fullscreen status bar restoration after rotating video playback.
- Removed flaky live YouTube dependency from subscription instrumentation tests.
- Completed Material readiness audit and release QA documentation.

Based on NewPipe 0.28.7.

