# Android TV 10-Foot Leanback Interface Design Specification (NewPipe)

**Date**: 2026-10-13  
**Status**: Approved by User  
**Target Project**: [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe)  
**Branch**: `feat/android-tv-support`  
**License Compliance**: 100% FOSS / F-Droid Compliant (GPL-3.0-or-later)  

---

## 1. Overview & Objectives

Deliver a native, first-class 10-foot television experience for NewPipe on Android TV and Google TV devices.

### Core Principles
1. **100% FOSS & F-Droid Compliant**:
   - Built exclusively using AndroidX Leanback (`androidx.leanback:leanback` and `androidx.leanback:leanback-preference`).
   - Zero proprietary Google Play Services dependencies or binary tracking libraries.
2. **Dedicated 10-Foot TV Subsystem**:
   - Fully isolated in `org.schabi.newpipe.tv` to eliminate any risk of regressions in existing phone and tablet code paths.
   - Tailored specifically for directional pad (D-pad) remote controls with smooth focus animations, card scaling, and high-contrast typography.
3. **Seamless Shared Core**:
   - Reuses 100% of NewPipe’s existing Room database (`AppDatabase`), subscription feeds (`FeedManager`), history manager (`HistoryRecordManager`), extractors (`NewPipeExtractor`), and playback services (`PlayerService`).
   - Instant synchronization between mobile and TV usage without redundant data structures.

---

## 2. Architecture & Subsystem Structure

The TV subsystem is encapsulated under `org.schabi.newpipe.tv`:

```
app/src/main/java/org/schabi/newpipe/tv/
├── ui/
│   ├── TvMainActivity.java             # Entry point for Android TV Leanback launcher
│   ├── TvBrowseFragment.java           # BrowseSupportFragment hosting side navigation & content rows
│   ├── TvVideoDetailsActivity.java     # Hosts DetailsSupportFragment for video overview
│   ├── TvVideoDetailsFragment.java     # Video synopsis, action buttons & related stream rows
│   ├── TvPlayerActivity.java           # Fullscreen 10-foot media playback activity
│   ├── TvPlaybackSupportFragment.java  # Video playback fragment with Leanback transport glue
│   ├── TvSearchActivity.java           # TV search container activity
│   ├── TvSearchFragment.java           # SearchSupportFragment with TV keyboard & voice input
│   ├── TvChannelActivity.java          # 10-foot Channel hub (banner, uploads, playlists)
│   ├── TvChannelFragment.java          # BrowseSupportFragment for channel tabs
│   ├── TvSettingsActivity.java         # TV settings container activity
│   ├── TvSettingsFragment.java         # LeanbackSettingsFragment for TV preferences
│   └── TvCommentsDialog.java           # D-pad scrollable 10-foot comments viewer
├── presenter/
│   ├── VideoCardPresenter.java         # Presenter for 16:9 video cards (thumbnail, title, author, views)
│   ├── ChannelCardPresenter.java       # Presenter for channel cards (avatar, title, subscribers)
│   ├── PlaylistCardPresenter.java      # Presenter for playlists and bookmarked streams
│   └── DetailsDescriptionPresenter.java# Leanback presenter for video title, metadata, & synopsis
├── player/
│   ├── TvPlayerGlue.java               # Bridges PlaybackTransportControlGlue to PlayerService & ExoPlayer
│   └── TvPlaybackSeekDataProvider.java # Handles D-pad scrub increments & continuous scrubbing
└── util/
    └── TvNavigationHelper.java         # Centralized navigation routing between TV screens
```

---

## 3. Dependencies & Gradle Configuration

* **`gradle/libs.versions.toml`**:
  ```toml
  [libraries]
  androidx-leanback = { module = "androidx.leanback:leanback", version = "1.0.0" }
  androidx-leanback-preference = { module = "androidx.leanback:leanback-preference", version = "1.0.0" }
  ```
* **`app/build.gradle.kts`**:
  ```kotlin
  dependencies {
      implementation(libs.androidx.leanback)
      implementation(libs.androidx.leanback.preference)
  }
  ```

---

## 4. Manifest Registration & Launch Routing

### 4.1 AndroidManifest.xml
* **Leanback Launcher**:
  `TvMainActivity` is registered with:
  ```xml
  <activity
      android:name=".tv.ui.TvMainActivity"
      android:exported="true"
      android:label="@string/app_name"
      android:screenOrientation="landscape"
      android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize"
      android:theme="@style/Theme.NewPipe.Tv.Browse">
      <intent-filter>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
      </intent-filter>
  </activity>
  ```
* **Hardware & Features**:
  ```xml
  <uses-feature
      android:name="android.hardware.touchscreen"
      android:required="false" />
  <uses-feature
      android:name="android.software.leanback"
      android:required="false" />
  ```

### 4.2 Runtime Detection & Fallback Routing
* `MainActivity` and `RouterActivity` check `DeviceUtils.isTv(context)`.
* If true (or if user enabled "Force TV UI" in Settings), the intent is transparently forwarded to `TvMainActivity`, ensuring sideloaded Fire TV / generic Android TV boxes seamlessly enter the 10-foot interface.

---

## 5. Browsing & Discovery (`TvMainActivity` & `TvBrowseFragment`)

### 5.1 Leanback Theme (`Theme.NewPipe.Tv.Browse`)
* Extends `Theme.Leanback.Browse`.
* Base color palette:
  * Brand Accent: `#E62117` (NewPipe Red)
  * Background: `#121212` (OLED Dark)
  * Card Background: `#1F1F1F`
  * Text: `#FFFFFF` (Primary), `#AAAAAA` (Secondary)

### 5.2 Sidebar Navigation & Content Rows
* **Headers**:
  1. **Trending / What's Hot**: Default kiosk stream feed from `ServiceList.getKioskList()`.
  2. **Subscriptions**: Unified feed of new video releases from subscribed channels.
  3. **Playlists & Bookmarks**: Local user playlists and bookmarked remote playlists.
  4. **Watch History**: Chronologically ordered stream records from Room DB.
  5. **Settings**: TV-tailored preferences.
* **Auto-Collapsing Sidebar**:
  * Expanding D-pad focus into content cards collapses headers to compact icons, maximizing viewing area.
  * Pressing `Back` or D-pad Left from the first column smoothly re-expands sidebar headers.
* **Infinite Pagination**:
  * Listens to `OnItemViewSelectedListener` to detect approaching row ends and triggers asynchronous next-page stream extraction.

### 5.3 Presenters
* **`VideoCardPresenter`**:
  * Renders 16:9 widescreen `ImageCardView` (320dp × 180dp).
  * Video thumbnail, title, channel name, views, and duration badge.
  * Hardware-accelerated focus zoom (+10%) and glow elevation (+8dp).

---

## 6. Video Details, Channel & Comments

### 6.1 `TvVideoDetailsFragment` (`DetailsSupportFragment`)
* **Dynamic Backdrop**: `BackgroundManager` displays a dimmed high-resolution thumbnail background.
* **Overview Row**:
  * Full-width detail overview with thumbnail, video title, author, views, and description summary.
* **Action Buttons**:
  * **Play Now**: Starts fullscreen playback (default focus).
  * **Enqueue**: Appends stream to current `PlayQueue`.
  * **Channel**: Opens `TvChannelActivity`.
  * **Subscribe / Subscribed**: Toggles channel subscription in Room DB.
  * **Add to Playlist**: Displays TV dialog to save to playlists.
  * **Comments**: Launches `TvCommentsDialog`.
* **Related Rows**:
  * Related Videos carousel.
  * More from Channel carousel.

### 6.2 `TvChannelActivity` (`TvChannelFragment`)
* Displays channel banner, avatar, subscriber count, and verified badge.
* Includes action buttons to Subscribe and "Play All".
* Horizontal rows for **Recent Uploads** and **Playlists**.

### 6.3 `TvCommentsDialog`
* 10-foot modal with a Leanback `VerticalGridView`.
* Large high-contrast typography (18sp+), author avatar, timestamp, like count, and comment text.
* D-pad vertical scrolling with automatic infinite loading.

---

## 7. Playback & Remote Transport Controls

### 7.1 `TvPlayerActivity` & `TvPlaybackSupportFragment`
* Extends `VideoSupportFragment`.
* Keeps screen on (`FLAG_KEEP_SCREEN_ON`).
* Bridges directly to NewPipe's existing `PlayerService` backend.

### 7.2 Remote Control Ergonomics
* **D-pad Center / OK**: Instantly toggles Play / Pause.
* **D-pad Left / Right (Click)**: Relative seek (-10s / +10s).
* **D-pad Left / Right (Hold)**: Continuous timeline scrubbing (ramping 10s → 30s → 60s).
* **D-pad Down**: Reveals the Leanback transport bar, secondary controls, and queue drawer.
* **D-pad Up**: Reveals stream title, channel name, and resolution badge.
* **Back Button**:
  * Dismisses active transport overlay if visible.
  * Exits player back to details if controls are hidden.

### 7.3 `TvPlayerGlue` Controls
* **Primary Controls**: Play/Pause, Next Track, Previous Track, Fast-Forward, Rewind.
* **Secondary Controls**:
  * **Stream Resolution Picker**: Modal dialog for 4K, 1440p, 1080p, 720p, etc.
  * **Subtitles & Captions**: Language selector and CC styling.
  * **Playback Speed**: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x.
  * **Repeat & Shuffle**: Queue mode toggles.

### 7.4 Hardware Compatibility
* Integrates `DeviceUtils.shouldSupportMediaTunneling()` to automatically disable tunneled playback on known problematic TV hardware (Sony Bravia, RealtekATV, Formuler, etc.), preventing black screens and playback crashes.

---

## 8. Search & TV Settings

### 8.1 `TvSearchFragment` (`SearchSupportFragment`)
* **Voice Search**: Leanback `SpeechOrbView` connects to system speech recognition.
* **On-Screen Keyboard & Suggestions**: Full D-pad typing support with instant search suggestions.
* **Categorized Results**: Grouped rows for Channels, Videos, and Playlists.

### 8.2 `TvSettingsFragment` (`LeanbackSettingsFragment`)
* TV-tailored preferences:
  * Default resolution on TV.
  * TV Mode toggle (Auto-detect vs Force TV UI).
  * Subtitle preferences and size.
  * Default service selector.
  * Watch history clearing and database backup.

---

## 9. Error Handling & Verification

### 9.1 Error Handling
* Offline and network error states presented via Leanback `ErrorSupportFragment` with prominent "Retry" action cards.
* Graceful fallback when video streams fail to resolve.

### 9.2 Verification Strategy
* **Automated Unit Tests**:
  * Validate TV navigation routing and TV device detection (`DeviceUtils.isTv()`).
  * Verify presenter data binding and view holders.
  * Verify `TvPlayerGlue` action mappings to `PlayerService`.
* **Manual TV Verification**:
  * Test on Android TV 1080p / 4K emulator and real TV devices.
  * Validate full D-pad remote navigation across all screens.
  * Verify video playback, seeking, queue advancement, and background audio transitions.
