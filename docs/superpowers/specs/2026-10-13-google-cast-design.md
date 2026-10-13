# Google Cast & Smart TV Streaming Design Specification (NewPipe)

**Date**: 2026-10-13  
**Status**: Approved by User  
**Target Project**: [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe)  
**License Compliance**: 100% FOSS / F-Droid Compliant (GPL-3.0-or-later)  

---

## 1. Overview & Objectives

Enable native streaming/casting support within NewPipe to Google Cast devices (Chromecast dongles, Google TV, Nest Hub) and Smart TVs (running YouTube / SmartTube apps).

### Core Principles
1. **Zero Proprietary Google Play Services**:
   - NewPipe's official contribution guidelines strictly prohibit proprietary Google Play Services (`play-services-cast-framework`) to preserve F-Droid inclusion and privacy.
   - All network communication is implemented using open-source network protocols (native Android `NsdManager` mDNS, direct TLS socket for CastV2, and HTTP/REST + WebSocket for YouTube Lounge).
2. **Unified Device Coverage**:
   - Supports both Google Cast hardware (via CastV2) and Smart TVs (via Lounge / TV pairing code).
3. **Full In-App Control**:
   - Remote controller overlay in NewPipe's player UI.
   - Synchronized seekbar, play/pause, volume rocker, and playlist queue advancement.
   - Background notification and MediaSession remote controls.
   - Seamless disconnect handback to local ExoPlayer.

---

## 2. Architecture & Subsystem Structure

The casting subsystem resides under `org.schabi.newpipe.cast`:

```
app/src/main/java/org/schabi/newpipe/cast/
├── model/
│   ├── CastDevice.java
│   ├── CastDeviceType.java
│   └── CastPlayerState.java
├── session/
│   ├── CastSession.java
│   ├── CastSessionListener.java
│   └── CastSessionManager.java
├── driver/
│   ├── CastDriver.java
│   ├── chromecast/
│   │   ├── ChromecastDriver.java
│   │   ├── CastMessageWire.java
│   │   └── CastChannelSession.java
│   └── lounge/
│       ├── YouTubeLoungeDriver.java
│       └── LoungeSession.java
├── ui/
│   ├── CastPlayerUi.java
│   ├── CastDeviceBottomSheet.java
│   └── LinkWithTvCodeDialog.java
```

### Core Subsystem Responsibilities
* **`CastDevice`**: Represents an endpoint with device name, IP address, port, type (`CHROMECAST`, `YOUTUBE_LOUNGE`), and unique identifier.
* **`CastSessionManager`**: Singleton managing discovery lifecycle, active session state, and event dispatching.
* **`CastDriver`**: Abstraction for discovering devices and instantiating `CastSession` instances.
* **`CastPlayerUi`**: Integrates with NewPipe's `PlayerUiList` to display the "Casting to [Device]" UI and dispatch playback actions.

---

## 3. Protocol Drivers

### 3.1 ChromecastDriver (Pure FOSS CastV2)
* **Discovery**:
  - Uses `android.net.nsd.NsdManager` to discover `_googlecast._tcp`.
  - Resolves device friendly name (`fn`), model (`md`), IP, and port (8009).
* **TLS Transport**:
  - Direct connection to port 8009 via `SSLSocket`.
  - Handles self-signed Cast device TLS certificates via custom trust-manager.
  - Message framing: 4-byte big-endian length prefix followed by protocol buffer / JSON CastMessage.
* **Default Media Receiver (`CC1AD845`)**:
  - Sends `CONNECT` to `receiver-0` (`urn:x-cast:com.google.cast.tp.connection`).
  - Launches `CC1AD845` via `urn:x-cast:com.google.cast.receiver`.
  - Receives `transportId` from `RECEIVER_STATUS`.
  - Dispatches `LOAD` command to `urn:x-cast:com.google.cast.media` with progressive MP4 / HLS stream URL extracted by NewPipeExtractor.
  - Keeps connection alive via periodic `PING` messages every 5 seconds to `urn:x-cast:com.google.cast.tp.heartbeat`.
  - Processes `MEDIA_STATUS` for playback progress, buffering, and completion.

### 3.2 YouTubeLoungeDriver (Smart TV & TV Code Pairing)
* **Discovery & Pairing**:
  - Auto-discovery via SSDP multicast (`239.255.255.250:1900`) for DIAL service (`urn:dial-multiscreen-org:service:dial:1`).
  - Manual pairing code dialog (`Link with TV code`): exchanges 12-digit code with YouTube Lounge API (`/api/lounge/pairing/get_screen`) to obtain `screenId` and `loungeToken`.
  - Tokens persisted in SharedPreferences for seamless reconnection.
* **Control Channel**:
  - Communicates via `/api/lounge/bc/bind` with `setPlaylist`, `play`, `pause`, `seekTo`, `setVolume`.
  - Smart TV decodes and renders native YouTube streams with highest performance.

---

## 4. UI & Player Integration

### 4.1 Cast Button Placement
- **`player.xml`**: Dedicated button in `primaryControls` top bar.
- **`MainActivity` & `VideoDetailFragment`**: Cast menu item in top ActionBar.
- **States**: Outline when disconnected, filled/accent when connected.

### 4.2 CastDeviceBottomSheet
- Lists discovered Chromecasts and Smart TVs.
- Option to "Link with TV code".
- Active session controls: Disconnect button, volume slider.

### 4.3 CastPlayerUi
- Pauses local `ExoPlayer` when casting begins.
- Shows remote controller overlay with video thumbnail, title, channel name, seekbar, and media controls.
- Synchronizes with `MediaSessionPlayerUi` and `NotificationPlayerUi` for lockscreen and notification control.

---

## 5. Playback Lifecycle & Resumption

1. **Queue Advance**: When a cast video finishes, `CastSession` notifies `PlayerService` to advance `PlayQueue` to index + 1.
2. **Disconnect / Reconnect**: When disconnecting, captures remote elapsed timestamp and resumes local `ExoPlayer` at the exact same millisecond.
3. **Error Handling**: Graceful timeout and retry on network drop, falling back to local pause.
