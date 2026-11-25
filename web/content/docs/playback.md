WizeStream offers several ways to use the same media: a main video player, background audio, Listen mode, a floating popup and optional Android picture-in-picture. Availability depends on the source stream, Android version and enabled settings.

## Choose a playback mode

| Mode | What it does | When to use it |
| --- | --- | --- |
| Main player | Video playback with details, captions and queue controls | Watching normally on your phone or tablet. |
| Listen | Continues the current item as audio with a visualizer | Listening without losing your queue or position. |
| Background | Audio playback while the app is not in the foreground | Music, interviews and long recordings. |
| Popup | Floating WizeStream video window | Watching alongside another app. |
| Android PiP | System-managed picture-in-picture on Android 8+ | A native floating window with media controls. |

Popup playback may need Android’s permission to display over other apps. Native PiP has its own Android permission and setting. The popup remains the floating-player path for Android 6 and 7.

## Queue, quality and audio tracks

Use the queue to arrange several items, add something next or later, and change the order before it plays. Repeat and shuffle apply to the queue rather than creating a remote platform playlist.

Quality and format choices depend on what the service exposes. Configure main/popup defaults and mobile-data limits in Video and audio settings. Multi-audio videos can offer different languages or tracks; select among the tracks actually provided by that stream.

Chapter markers help you move between sections. Supported captions can be selected by language; the transcript view provides a searchable alternative to reading captions in real time. Per-channel playback profiles can preserve selected playback preferences for individual channels.

## Gestures and fullscreen

Pinch with two fingers to zoom from **100% to 400%** in the main player. Once enlarged, use two fingers to pan. Pinch back or use the Fit/Fill/Zoom control to reset the view. Touch lock helps avoid accidental playback gestures.

Volume, brightness and seeking gestures are configurable. A vertical two-finger gesture can adjust speed when enabled and when the video is not already zoomed. Phones, tablets and TV remotes have different control and focus paths; see [devices and casting](devices.html).

## Captions and timing

Open the caption menu to select a track. **Subtitle timing** adjusts a caption track in 100 ms steps, up to 30 seconds earlier or later. Use Reset to return to the original timing.

The offset is session-only and clears when the caption track changes. It does not rewrite a downloaded subtitle file or change the source video.

An optional second caption track can display another available language alongside the primary captions. The reviewed 1.52.2 release coordinates primary-language changes, restores captions across main/popup player switches and applies the session timing adjustment to both tracks. It also fixes the second-language picker and schedules short secondary cues at their boundaries, including after seeking or changing speed.

Verified downloaded companion captions can supply the second track offline. A missing or replaced caption file is rejected without falling back to a network request. See [downloads and offline playback](downloads.html).

## A–B repeat

Enable A–B repeat under **Settings → Video and audio**. Its enable switch is **off by default** and is available since 1.42.0, including 1.52.2. Set A at the start of the passage and B at its end; B must be at least half a second after A.

The selected passage repeats during non-live playback. Clear the range when you want normal playback again. The range is temporary, not a saved playlist or an export.

## 360° video

The player supports automatic projection detection and manual equirectangular viewing. First enable the optional 360° setting in Video and audio. This switch is off by default and is available since 1.42.0, including 1.52.2; the underlying projection controls first appeared in 1.41.0.

Use **360° → Video projection** to choose Automatic, Flat or a manual projection. Drag to look around, enable optional motion tracking if desired, and use Recenter to restore the forward view. TV and keyboard panning are also supported. A flat video does not become a genuine spherical recording when forced into a 360° projection.

## Listening and audio tools

Listen mode keeps the current item and queue while displaying a chosen visualizer. **Settings → Video and audio → Visualizer style** offers 15 styles. The visualizer uses decoded player audio; it does not need microphone access.

The audio tools include a 10-band equalizer, presets and a custom curve. Loudness normalization and dynamic-range compression are optional and off by default. Leave headroom when boosting frequencies to reduce clipping.

## Live streams, timers and recovery

Full live rewind is limited by the DVR history a broadcaster supplies. A broadcast can end, change formats or stop exposing its archive. No client can guarantee unlimited rewind or continuing access to every historical segment.

The sleep timer supports presets, custom durations, end-of-video or end-of-queue behavior, with an optional fade. Queue recovery can preserve loaded queue entries for later recovery when its setting and watch history are enabled. Incognito queues are excluded; recovery is not a permanent backup.

## Previous playback and remaining time

**Return to previous playback** restores the previous video with its queue, shuffle order, position and saved play/pause state. This is temporary navigation history: it clears when the playback session or privacy scope changes.

Tapping the total duration switches to estimated remaining playback time. The estimate accounts for speed and enabled automatic SponsorBlock skips. It is unavailable for live playback, casting or an active A–B repeat range.
