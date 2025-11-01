# NewPipe External Player (feature/mx-like-external-player)

This module provides an ExoPlayer-based external video player which can be used as a system external player:
- Accepts VIEW and SEND intents (http(s) links, text share).
- Basic ExoPlayer integration with PlayerView and controls.
- Picture-in-Picture (PiP) support (Android O+).
- UI skeleton matching typical external players (MX-style quick controls).

What is included in this branch:
- external-player module with PlayerActivity, layout, and manifest intent filters.
- build.gradle configured for ExoPlayer.

Important notes / Limitations:
- Direct YouTube links cannot be played directly by ExoPlayer; YouTube requires stream extraction. This branch includes placeholders and a TODO where the app should call the extractor already present in the main app (NewPipe's extractor) to produce playable stream URLs, or integrate a secure YouTube extraction pipeline.
- DRM, subtitles, audio-only background service, casting, and advanced subtitle selection are not yet implemented in this initial commit.

Planned next steps / TODOs (can be split to issues):
- Integrate with NewPipe extractor API to handle YouTube pages/IDs -> actual video stream URLs (muxed/dash) (High priority)
- Support background playback and notification controls (media session & notification)
- Support playlists and queueing (play next/previous)
- Add subtitle downloading and selection (TTML/SRT/WebVTT)
- Add audio boost, hardware acceleration toggles, speed control, equalizer integration
- Implement Chromecast / DLNA / Google Cast support
- Improve UX: gestures (seek/surface brightness/volume), aspect-ratio toggles, resume playback
- Add tests and instrumentation tests for intent handling and PiP flows

How to test locally:
1. Add `include ':external-player'` to your root settings.gradle
2. Build and install the app variant or run as a standalone APK and send an ACTION_VIEW intent:
   adb shell am start -a android.intent.action.VIEW -d "https://www.example.com/video.mp4" org.newpipe.externalplayer/.ExternalPlayerActivity
3. Share a YouTube URL via Android share sheet to the player to validate intent handling (extraction not implemented yet).

Security & privacy notes:
- Do not embed broken/unsafe YouTube extractors. Prefer reusing the app's extractor or server-assisted extraction. Respect Terms of Service where applicable.
- If integrating with YouTube, prefer the app's existing extractor infrastructure to avoid duplicating logic and leaking credentials.
