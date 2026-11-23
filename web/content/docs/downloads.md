Downloads are separate from streaming caches and subscription records. Choose where files should be saved and which video, audio or caption formats you need. A downloaded item remains subject to the storage permissions and file access available on that Android device.

## Choose folders first

Open **Settings → Download** and choose default video and audio folders. Android may ask you to grant access through its document picker. Keep enough free space for the download and any temporary post-processing files.

For batch downloads, turn off **Ask where to download** and set the default folders first. Otherwise a batch cannot reliably save each queued item without repeated destination prompts.

## Download an individual item

1. Open a supported video or audio item and choose Download.
2. Select a media type, format, quality and available audio track.
3. Check the destination and filename.
4. Start the download and monitor it in Downloads.

Available formats come from the source service. A service can remove an item or change the streams it exposes; a failed transfer does not necessarily indicate a problem with the destination folder.

## Video and companion captions

When supported, a full video download can queue one caption track as a **separate companion file**. This is a sidecar subtitle file, not a subtitle stream embedded into the video container.

Companion caption behavior applies to full downloads rather than segment downloads. For playback outside WizeStream, the external player must support the caption format and sidecar-file naming/location you use.

## Chapters, transcripts and audio artwork in newer builds

**Merged after 1.50.0:** new downloads retain available chapter metadata, including precise timestamps and chapters remapped to a selected-range export. Recorded companion captions can play offline and open a searchable local transcript with timestamp navigation. This depends on the chapter/caption information actually saved with the download; older downloads may not have it.

Current source also reads supported embedded chapters from local MP3 and MP4/M4A files when saved download chapters are unavailable. Unsupported or damaged chapter metadata does not add chapters, and replaced or edited files can invalidate saved associations.

The optional **Embed audio artwork** download setting is off by default. In builds containing this post-1.50.0 change, new MP3 and M4A downloads can keep the best available original artwork while preserving existing covers. Artwork fetching uses additional data and temporary storage; missing or failed artwork does not make the audio unusable.

## Batch downloads

Use **Download playlist** from a local or remote playlist’s menu, or the queue’s download action. WizeStream can collect a remote playlist before starting the batch and lets you choose video or audio for the group.

Each item uses its available default quality and audio track. Optional track-number prefixes preserve a useful file order; duplicate filenames are made unique. Entries that already reference local media are skipped rather than downloaded again.

## Automatic channel downloads

Selected subscriptions can be checked for new uploads approximately hourly. The current implementation bounds work to up to three new items per channel and twenty per check. Background execution remains subject to Android scheduling, battery and network conditions.

Automatic downloads are not a promise to fetch a channel’s complete back catalog. They are intended for newly discovered uploads from channels you have selected.

## Scheduled download windows

Configure a **Scheduled download window** in download settings to allow queued transfers during a daily local-time interval. Overnight windows are supported. Optional constraints require charging or an unmetered network.

Running transfers check the boundary roughly every thirty seconds; post-processing can still finish. Scheduled workers ordinarily check queued work about every fifteen minutes, but Android may delay them. Treat the schedule as an allowed window, not an exact alarm.

Manually paused jobs stay paused. External downloaders are outside WizeStream’s window controls.

## Manage offline storage

Open **Offline library management** from Downloads to opt into retention rules for tracked automatic channel downloads. You can keep the newest items per channel, limit managed storage, or remove fully watched tracked downloads.

Protect items with **Keep permanently** and review the exact candidates before a manual cleanup. Manual downloads, imported media, changed-size files and protected files are excluded. Automatic cleanup pauses while the player service is open or Incognito is active.

## Downloads and Device Sync

Device Sync transfers completed-download metadata, not the media file itself. A synchronized entry whose file is absent appears as **Not local**. **Download on this device** resolves the original source again and offers normal format choices.

The original source may no longer be available. Keep your own media-file backups when you need durable access. App database backup and peer-to-peer synchronization are not substitutes for copying your actual video, audio and subtitle files.
