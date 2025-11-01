# NewPipe External Player (feature/mx-like-external-player) — updated

What's in this update:
- Foreground PlayerService for background playback and persistent MediaStyle notification.
- MediaSession integration so lockscreen/playback controls work.
- Activity delegates playback to the service (better lifecycle behavior).
- Playbook speed control (cycle speeds), subtitle toggle placeholder.
- Updated manifest to request FOREGROUND_SERVICE permission.

Important integration notes:
- YouTube: ExoPlayer cannot play raw youtube.com/watch?v= links. Use NewPipe's extractor module to transform a YouTube page/ID into direct stream URLs (muxed/DASH) before passing URIs to PlayerService.
- Subtitles: This is currently a UI placeholder. To implement, supply VTT/SRT/TTML URLs from the extractor and attach the subtitle tracks to ExoPlayer in PlayerService.
- Audio focus & interruptions: Basic playback is implemented; extend audio focus handling and proper interruption handling for production.
- Casting, DRM, AD handling, advanced subtitle formats and timing are not in scope for this commit and should be added incrementally.

How to test locally:
1. Add `include ':external-player'` to your root settings.gradle
2. Build and install.
3. Start a direct HTTP(S) playable URL:
   adb shell am start -a android.intent.action.VIEW -d "https://www.example.com/video.mp4" org.newpipe.externalplayer/.ExternalPlayerActivity
4. Observe playback continues when you press Home (notification appears). Use notification controls to pause/play.
5. Share a text containing a direct video URL via Android share sheet -> choose NewPipe External Player.
6. Try the speed button to cycle speeds, and the SUB button to toggle the placeholder.

Next steps:
- Integrate with the app's extractor to resolve YouTube links into playable stream URLs.
- Implement subtitles support in PlayerService and UI for subtitle selection.
- Implement proper audio focus handling and media-button receiver plumbing.
- Add instrumentation tests for service lifecycle and notification behavior.