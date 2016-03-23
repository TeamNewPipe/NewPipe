/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.schabi.newpipe.player.exoplayer;

import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.dash.DashChunkSource;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer.MetadataRenderer;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.util.DebugTextViewHelper;
import com.google.android.exoplayer.util.PlayerControl;

import android.media.MediaCodec.CryptoException;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link ExoPlayer} that provides a higher level interface. It can be prepared
 * with one of a number of {@link RendererBuilder} classes to suit different use cases (e.g. DASH,
 * SmoothStreaming and so on).
 */
public class NPExoPlayer implements ExoPlayer.Listener, ChunkSampleSource.EventListener,
    HlsSampleSource.EventListener, DefaultBandwidthMeter.EventListener,
    MediaCodecVideoTrackRenderer.EventListener, MediaCodecAudioTrackRenderer.EventListener,
    StreamingDrmSessionManager.EventListener, DashChunkSource.EventListener, TextRenderer,
    MetadataRenderer<Map<String, Object>>, DebugTextViewHelper.Provider {

  /**
   * Builds renderers for the player.
   */
  public interface RendererBuilder {
    /**
     * Builds renderers for playback.
     *
     * @param player The player for which renderers are being built. {@link NPExoPlayer#onRenderers}
     *     should be invoked once the renderers have been built. If building fails,
     *     {@link NPExoPlayer#onRenderersError} should be invoked.
     */
    void buildRenderers(NPExoPlayer player);
    /**
     * Cancels the current build operation, if there is one. Else does nothing.
     * <p>
     * A canceled build operation must not invoke {@link NPExoPlayer#onRenderers} or
     * {@link NPExoPlayer#onRenderersError} on the player, which may have been released.
     */
    void cancel();
  }

  /**
   * A listener for core events.
   */
  public interface Listener {
    void onStateChanged(boolean playWhenReady, int playbackState);
    void onError(Exception e);
    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio);
  }

  /**
   * A listener for internal errors.
   * <p>
   * These errors are not visible to the user, and hence this listener is provided for
   * informational purposes only. Note however that an internal error may cause a fatal
   * error if the player fails to recover. If this happens, {@link Listener#onError(Exception)}
   * will be invoked.
   */
  public interface InternalErrorListener {
    void onRendererInitializationError(Exception e);
    void onAudioTrackInitializationError(AudioTrack.InitializationException e);
    void onAudioTrackWriteError(AudioTrack.WriteException e);
    void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);
    void onDecoderInitializationError(DecoderInitializationException e);
    void onCryptoError(CryptoException e);
    void onLoadError(int sourceId, IOException e);
    void onDrmSessionManagerError(Exception e);
  }

  /**
   * A listener for debugging information.
   */
  public interface InfoListener {
    void onVideoFormatEnabled(Format format, int trigger, long mediaTimeMs);
    void onAudioFormatEnabled(Format format, int trigger, long mediaTimeMs);
    void onDroppedFrames(int count, long elapsed);
    void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate);
    void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
        long mediaStartTimeMs, long mediaEndTimeMs);
    void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
        long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs);
    void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
        long initializationDurationMs);
    void onAvailableRangeChanged(int sourceId, TimeRange availableRange);
  }

  /**
   * A listener for receiving notifications of timed text.
   */
  public interface CaptionListener {
    void onCues(List<Cue> cues);
  }

  /**
   * A listener for receiving ID3 metadata parsed from the media stream.
   */
  public interface Id3MetadataListener {
    void onId3Metadata(Map<String, Object> metadata);
  }

  // Constants pulled into this class for convenience.
  public static final int STATE_IDLE = ExoPlayer.STATE_IDLE;
  public static final int STATE_PREPARING = ExoPlayer.STATE_PREPARING;
  public static final int STATE_BUFFERING = ExoPlayer.STATE_BUFFERING;
  public static final int STATE_READY = ExoPlayer.STATE_READY;
  public static final int STATE_ENDED = ExoPlayer.STATE_ENDED;
  public static final int TRACK_DISABLED = ExoPlayer.TRACK_DISABLED;
  public static final int TRACK_DEFAULT = ExoPlayer.TRACK_DEFAULT;

  public static final int RENDERER_COUNT = 4;
  public static final int TYPE_VIDEO = 0;
  public static final int TYPE_AUDIO = 1;
  public static final int TYPE_TEXT = 2;
  public static final int TYPE_METADATA = 3;

  private static final int RENDERER_BUILDING_STATE_IDLE = 1;
  private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
  private static final int RENDERER_BUILDING_STATE_BUILT = 3;

  private final RendererBuilder rendererBuilder;
  private final ExoPlayer player;
  private final PlayerControl playerControl;
  private final Handler mainHandler;
  private final CopyOnWriteArrayList<Listener> listeners;

  private int rendererBuildingState;
  private int lastReportedPlaybackState;
  private boolean lastReportedPlayWhenReady;

  private Surface surface;
  private TrackRenderer videoRenderer;
  private CodecCounters codecCounters;
  private Format videoFormat;
  private int videoTrackToRestore;

  private BandwidthMeter bandwidthMeter;
  private boolean backgrounded;

  private CaptionListener captionListener;
  private Id3MetadataListener id3MetadataListener;
  private InternalErrorListener internalErrorListener;
  private InfoListener infoListener;

  public NPExoPlayer(RendererBuilder rendererBuilder) {
    this.rendererBuilder = rendererBuilder;
    player = ExoPlayer.Factory.newInstance(RENDERER_COUNT, 1000, 5000);
    player.addListener(this);
    playerControl = new PlayerControl(player);
    mainHandler = new Handler();
    listeners = new CopyOnWriteArrayList<>();
    lastReportedPlaybackState = STATE_IDLE;
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    // Disable text initially.
    player.setSelectedTrack(TYPE_TEXT, TRACK_DISABLED);
  }

  public PlayerControl getPlayerControl() {
    return playerControl;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public void setInternalErrorListener(InternalErrorListener listener) {
    internalErrorListener = listener;
  }

  public void setInfoListener(InfoListener listener) {
    infoListener = listener;
  }

  public void setCaptionListener(CaptionListener listener) {
    captionListener = listener;
  }

  public void setMetadataListener(Id3MetadataListener listener) {
    id3MetadataListener = listener;
  }

  public void setSurface(Surface surface) {
    this.surface = surface;
    pushSurface(false);
  }

  public Surface getSurface() {
    return surface;
  }

  public void blockingClearSurface() {
    surface = null;
    pushSurface(true);
  }

  public int getTrackCount(int type) {
    return player.getTrackCount(type);
  }

  public MediaFormat getTrackFormat(int type, int index) {
    return player.getTrackFormat(type, index);
  }

  public int getSelectedTrack(int type) {
    return player.getSelectedTrack(type);
  }

  public void setSelectedTrack(int type, int index) {
    player.setSelectedTrack(type, index);
    if (type == TYPE_TEXT && index < 0 && captionListener != null) {
      captionListener.onCues(Collections.<Cue>emptyList());
    }
  }

  public boolean getBackgrounded() {
    return backgrounded;
  }

  public void setBackgrounded(boolean backgrounded) {
    if (this.backgrounded == backgrounded) {
      return;
    }
    this.backgrounded = backgrounded;
    if (backgrounded) {
      videoTrackToRestore = getSelectedTrack(TYPE_VIDEO);
      setSelectedTrack(TYPE_VIDEO, TRACK_DISABLED);
      blockingClearSurface();
    } else {
      setSelectedTrack(TYPE_VIDEO, videoTrackToRestore);
    }
  }

  public void prepare() {
    if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT) {
      player.stop();
    }
    rendererBuilder.cancel();
    videoFormat = null;
    videoRenderer = null;
    rendererBuildingState = RENDERER_BUILDING_STATE_BUILDING;
    maybeReportPlayerState();
    rendererBuilder.buildRenderers(this);
  }

  /**
   * Invoked with the results from a {@link RendererBuilder}.
   *
   * @param renderers Renderers indexed by {@link NPExoPlayer} TYPE_* constants. An individual
   *     element may be null if there do not exist tracks of the corresponding type.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth. May be null.
   */
  /* package */ void onRenderers(TrackRenderer[] renderers, BandwidthMeter bandwidthMeter) {
    for (int i = 0; i < RENDERER_COUNT; i++) {
      if (renderers[i] == null) {
        // Convert a null renderer to a dummy renderer.
        renderers[i] = new DummyTrackRenderer();
      }
    }
    // Complete preparation.
    this.videoRenderer = renderers[TYPE_VIDEO];
    this.codecCounters = videoRenderer instanceof MediaCodecTrackRenderer
        ? ((MediaCodecTrackRenderer) videoRenderer).codecCounters
        : renderers[TYPE_AUDIO] instanceof MediaCodecTrackRenderer
        ? ((MediaCodecTrackRenderer) renderers[TYPE_AUDIO]).codecCounters : null;
    this.bandwidthMeter = bandwidthMeter;
    pushSurface(false);
    player.prepare(renderers);
    rendererBuildingState = RENDERER_BUILDING_STATE_BUILT;
  }

  /**
   * Invoked if a {@link RendererBuilder} encounters an error.
   *
   * @param e Describes the error.
   */
  /* package */ void onRenderersError(Exception e) {
    if (internalErrorListener != null) {
      internalErrorListener.onRendererInitializationError(e);
    }
    for (Listener listener : listeners) {
      listener.onError(e);
    }
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    maybeReportPlayerState();
  }

  public void setPlayWhenReady(boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
  }

  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  public void release() {
    rendererBuilder.cancel();
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    surface = null;
    player.release();
  }

  public int getPlaybackState() {
    if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILDING) {
      return STATE_PREPARING;
    }
    int playerState = player.getPlaybackState();
    if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT && playerState == STATE_IDLE) {
      // This is an edge case where the renderers are built, but are still being passed to the
      // player's playback thread.
      return STATE_PREPARING;
    }
    return playerState;
  }

  @Override
  public Format getFormat() {
    return videoFormat;
  }

  @Override
  public BandwidthMeter getBandwidthMeter() {
    return bandwidthMeter;
  }

  @Override
  public CodecCounters getCodecCounters() {
    return codecCounters;
  }

  @Override
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  public long getDuration() {
    return player.getDuration();
  }

  public int getBufferedPercentage() {
    return player.getBufferedPercentage();
  }

  public boolean getPlayWhenReady() {
    return player.getPlayWhenReady();
  }

  /* package */ Looper getPlaybackLooper() {
    return player.getPlaybackLooper();
  }

  /* package */ Handler getMainHandler() {
    return mainHandler;
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    maybeReportPlayerState();
  }

  @Override
  public void onPlayerError(ExoPlaybackException exception) {
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    for (Listener listener : listeners) {
      listener.onError(exception);
    }
  }

  @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {
    for (Listener listener : listeners) {
      listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
    }
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    if (infoListener != null) {
      infoListener.onDroppedFrames(count, elapsed);
    }
  }

  @Override
  public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
    if (infoListener != null) {
      infoListener.onBandwidthSample(elapsedMs, bytes, bitrateEstimate);
    }
  }

  @Override
  public void onDownstreamFormatChanged(int sourceId, Format format, int trigger,
      long mediaTimeMs) {
    if (infoListener == null) {
      return;
    }
    if (sourceId == TYPE_VIDEO) {
      videoFormat = format;
      infoListener.onVideoFormatEnabled(format, trigger, mediaTimeMs);
    } else if (sourceId == TYPE_AUDIO) {
      infoListener.onAudioFormatEnabled(format, trigger, mediaTimeMs);
    }
  }

  @Override
  public void onDrmKeysLoaded() {
    // Do nothing.
  }

  @Override
  public void onDrmSessionManagerError(Exception e) {
    if (internalErrorListener != null) {
      internalErrorListener.onDrmSessionManagerError(e);
    }
  }

  @Override
  public void onDecoderInitializationError(DecoderInitializationException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onDecoderInitializationError(e);
    }
  }

  @Override
  public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onAudioTrackInitializationError(e);
    }
  }

  @Override
  public void onAudioTrackWriteError(AudioTrack.WriteException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onAudioTrackWriteError(e);
    }
  }

  @Override
  public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    if (internalErrorListener != null) {
      internalErrorListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }
  }

  @Override
  public void onCryptoError(CryptoException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onCryptoError(e);
    }
  }

  @Override
  public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    if (infoListener != null) {
      infoListener.onDecoderInitialized(decoderName, elapsedRealtimeMs, initializationDurationMs);
    }
  }

  @Override
  public void onLoadError(int sourceId, IOException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onLoadError(sourceId, e);
    }
  }

  @Override
  public void onCues(List<Cue> cues) {
    if (captionListener != null && getSelectedTrack(TYPE_TEXT) != TRACK_DISABLED) {
      captionListener.onCues(cues);
    }
  }

  @Override
  public void onMetadata(Map<String, Object> metadata) {
    if (id3MetadataListener != null && getSelectedTrack(TYPE_METADATA) != TRACK_DISABLED) {
      id3MetadataListener.onId3Metadata(metadata);
    }
  }

  @Override
  public void onAvailableRangeChanged(int sourceId, TimeRange availableRange) {
    if (infoListener != null) {
      infoListener.onAvailableRangeChanged(sourceId, availableRange);
    }
  }

  @Override
  public void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  @Override
  public void onDrawnToSurface(Surface surface) {
    // Do nothing.
  }

  @Override
  public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
      long mediaStartTimeMs, long mediaEndTimeMs) {
    if (infoListener != null) {
      infoListener.onLoadStarted(sourceId, length, type, trigger, format, mediaStartTimeMs,
          mediaEndTimeMs);
    }
  }

  @Override
  public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
      long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
    if (infoListener != null) {
      infoListener.onLoadCompleted(sourceId, bytesLoaded, type, trigger, format, mediaStartTimeMs,
          mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs);
    }
  }

  @Override
  public void onLoadCanceled(int sourceId, long bytesLoaded) {
    // Do nothing.
  }

  @Override
  public void onUpstreamDiscarded(int sourceId, long mediaStartTimeMs, long mediaEndTimeMs) {
    // Do nothing.
  }

  private void maybeReportPlayerState() {
    boolean playWhenReady = player.getPlayWhenReady();
    int playbackState = getPlaybackState();
    if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
      for (Listener listener : listeners) {
        listener.onStateChanged(playWhenReady, playbackState);
      }
      lastReportedPlayWhenReady = playWhenReady;
      lastReportedPlaybackState = playbackState;
    }
  }

  private void pushSurface(boolean blockForSurfacePush) {
    if (videoRenderer == null) {
      return;
    }

    if (blockForSurfacePush) {
      player.blockingSendMessage(
          videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
    } else {
      player.sendMessage(
          videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
    }
  }

}
