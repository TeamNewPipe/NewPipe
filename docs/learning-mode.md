# Learning Mode

Learning Mode adds optional study tools to WizeStream without changing normal playback. It is
disabled by default, and only content that you explicitly designate as Learning contributes to study
activity.

## Enable Learning Mode

1. Open **Settings > Learning Mode**.
2. Enable **Learning Mode**.
3. Choose whether to enable timestamped notes, playlist progress, and background-listening time.

Enabling Learning Mode makes the Learning actions and dashboard available. It does not automatically
classify everything you watch or listen to as study activity. The **Learning** dashboard appears in
the navigation drawer while Learning Mode is enabled.

## Select Learning content

WizeStream supports individual videos, local media, local playlists, and remote playlists as Learning
content.

### Individual videos and local media

Open a video's context menu, usually by long-pressing its card or opening its item menu, and select
**Mark as Learning**. Use **Remove from Learning** in the same menu to remove its individual mark.

### Local playlists

Open the playlist menu and select **Mark as Learning**. The playlist and its current contents become
Learning content. When playlist-progress tools are enabled, the playlist also provides controls to
mark all videos as watched or reset its learning progress.

Use **Remove from Learning** in the playlist menu to remove the playlist designation.

### Remote playlists

Open the remote playlist menu and select **Mark as Learning**. WizeStream indexes the playlist's
contents as its pages load and includes them in the Learning dashboard and progress calculations.

Use **Remove from Learning** in the playlist menu to remove the playlist designation.

## How overlapping selections work

WizeStream remembers why a video is Learning content. A video can be designated directly, through a
local playlist, through a remote playlist, or through more than one of these sources.

Removing one mark removes only that source. The video remains Learning content while another marked
source still includes it. To exclude the video completely, remove every direct or playlist mark that
designates it.

## Tracked activity

Only playback of designated Learning content is included in:

- Study time for today, the current week, and all time
- Current and longest study streaks
- The 28-day activity calendar
- Learning progress and completion
- Continue Learning and other dashboard sections

Background playback is included only when **Count background listening** is enabled under
**Settings > Learning Mode**. Normal, unmarked playback remains available as usual but does not
affect Learning statistics or streaks.

## Timestamped video notes

When Learning Mode and timestamped notes are enabled for the current profile, the main player's
note button is available for non-live videos, including videos not designated as Learning content.
The detail screen's **Notes** tab remains available for designated Learning content. A note records
the associated playback position so the video can be reopened at that point.

By default, tap the player's note button to open **Video notes**, or touch and hold it to add a note
at the current playback position. You can also tap **Add timestamped note** in the notes list; a
list opened from the player keeps the position at which you opened it.

For one-tap note creation, open **Settings > Video and audio > Behavior > Note button tap action**
and choose **Add timestamped note**. A tap then opens the note editor, and touching and holding
opens the notes list. Choose **Open video notes** to restore the default. The setting changes
the player's button actions without enabling notes for additional content or profiles.

Inside a note, ordinary taps still edit or select text. Touch and hold a timestamp to seek to it,
or a web link to open it; the player-button setting does not change these deliberate gestures.

## Learning dashboard

Open **Learning** from the navigation drawer to see:

- Study-time totals, streaks, and recent activity
- All designated Learning content
- Videos ready to continue
- Active and completed Learning playlists
- Recently annotated videos

The exact sections shown depend on the Learning Mode options enabled in Settings and the Learning
content available on the device.

## Existing data after an upgrade

Upgrading preserves existing learning notes and session history. A video that already has a
timestamped learning note is automatically designated as Learning content, and its related historical
sessions remain included. Historical activity for other unmarked content is preserved in the database
but is not counted as designated Learning activity.

## Device synchronization

Learning-content selections are currently local to each device and are not synchronized. Mark the
required videos and playlists separately on every device where you want them treated as Learning
content.

Timestamped learning notes have their own optional synchronization setting under
**Settings > Device synchronization**. Note synchronization does not synchronize Learning-content
selections.

### Difficulty and review

Enable **Lesson difficulty** in Learning settings to rate designated lessons as
Easy, Medium, or Hard from their description. Use **Review lessons** in a marked
playlist's menu to search its indexed videos, sort hardest first, and filter to
Hard only. Tap a lesson to watch it or change its rating. Choose Not rated to clear
its rating. A hard-lesson count on the Learning dashboard and in the course panel
provides a shortcut before continuing to the next lesson. Reviewing is optional;
playback and progress are never blocked by a rating.

Ratings are per profile, stored locally, and are not included in Learning note
sync. Remote course search covers videos already loaded and indexed from the
playlist; opening further playlist pages adds them to review results. Local course
membership follows the playlist's current contents.

## Playlist notes

Open a designated Learning playlist and choose **View playlist notes** from its menu.
Select a note to edit it or export/delete it. **Note actions** exports all playlist notes as
UTF-8 text or CSV, or deletes them after confirmation. Android's document picker lets you
choose where to save exports. Notes are shared by video: deleting a note also removes it
from any other playlist containing that video. Remote playlist notes use the indexed
playlist contents, so open/load the playlist first to update its membership.
