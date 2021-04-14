/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.schabi.newpipe.player.renderer;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.DummySurface;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

/**
 * Helps a video {@link Renderer} release frames to a {@link Surface}.
 *
 * The helper adjusts frame release timestamps to achieve a smoother visual result. The release
 *       timestamps are smoothed, and aligned with the default display's vsync signal.
 */
public final class VideoFrameReleaseHelper {

  private static final String TAG = "VideoFrameReleaseHelper";

  /** The period between sampling display VSYNC timestamps, in milliseconds. */
  private static final long VSYNC_SAMPLE_UPDATE_PERIOD_MS = 500;
  /**
   * The maximum adjustment that can be made to a frame release timestamp, in nanoseconds, excluding
   * the part of the adjustment that aligns frame release timestamps with the display VSYNC.
   */
  private static final long MAX_ALLOWED_ADJUSTMENT_NS = 20_000_000;
  /**
   * If a frame is targeted to a display VSYNC with timestamp {@code vsyncTime}, the adjusted frame
   * release timestamp will be calculated as {@code releaseTime = vsyncTime - ((vsyncDuration *
   * VSYNC_OFFSET_PERCENTAGE) / 100)}.
   */
  private static final long VSYNC_OFFSET_PERCENTAGE = 80;

  private final FixedFrameRateEstimator frameRateEstimator;
  @Nullable private final WindowManager windowManager;
  @Nullable private final VSyncSampler vsyncSampler;
  @Nullable private final DefaultDisplayListener displayListener;

  private boolean started;
  @Nullable private Surface surface;

  private float playbackSpeed;

  private long vsyncDurationNs;
  private long vsyncOffsetNs;

  private long frameIndex;
  private long pendingLastAdjustedFrameIndex;
  private long pendingLastAdjustedReleaseTimeNs;
  private long lastAdjustedFrameIndex;
  private long lastAdjustedReleaseTimeNs;

  /**
   * Constructs an instance.
   *
   * @param context A context from which information about the default display can be retrieved.
   */
  public VideoFrameReleaseHelper(@Nullable Context context) {
    frameRateEstimator = new FixedFrameRateEstimator();
    if (context != null) {
      context = context.getApplicationContext();
      windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    } else {
      windowManager = null;
    }
    if (windowManager != null) {
      displayListener =
          Util.SDK_INT >= 17 ? maybeBuildDefaultDisplayListenerV17(checkNotNull(context)) : null;
      vsyncSampler = VSyncSampler.getInstance();
    } else {
      displayListener = null;
      vsyncSampler = null;
    }
    vsyncDurationNs = C.TIME_UNSET;
    vsyncOffsetNs = C.TIME_UNSET;
    playbackSpeed = 1f;
  }

  /** Called when the renderer is enabled. */
  @TargetApi(17) // displayListener is null if Util.SDK_INT < 17.
  public void onEnabled() {
    if (windowManager != null) {
      checkNotNull(vsyncSampler).addObserver();
      if (displayListener != null) {
        displayListener.register();
      }
      updateDefaultDisplayRefreshRateParams();
    }
  }

  /** Called when the renderer is started. */
  public void onStarted() {
    started = true;
    resetAdjustment();
  }

  /**
   * Called when the renderer changes which {@link Surface} it's rendering to renders to.
   *
   * @param surface The new {@link Surface}, or {@code null} if the renderer does not have one.
   */
  public void onSurfaceChanged(@Nullable Surface surface) {
    if (surface instanceof DummySurface) {
      // We don't care about dummy surfaces for release timing, since they're not visible.
      surface = null;
    }
    if (this.surface == surface) {
      return;
    }
    this.surface = surface;
  }

  /** Called when the renderer's position is reset. */
  public void onPositionReset() {
    resetAdjustment();
  }

  /**
   * Called when the renderer's playback speed changes.
   *
   * @param playbackSpeed The factor by which playback is sped up.
   */
  public void onPlaybackSpeed(float playbackSpeed) {
    this.playbackSpeed = playbackSpeed;
    resetAdjustment();
  }

  /**
   * Called when the renderer's output format changes.
   *
   * @param formatFrameRate The format's frame rate, or {@link Format#NO_VALUE} if unknown.
   */
  public void onFormatChanged(float formatFrameRate) {
    frameRateEstimator.reset();
  }

  /**
   * Called by the renderer for each frame, prior to it being skipped, dropped or rendered.
   *
   * @param framePresentationTimeUs The frame presentation timestamp, in microseconds.
   */
  public void onNextFrame(long framePresentationTimeUs) {
    if (pendingLastAdjustedFrameIndex != C.INDEX_UNSET) {
      lastAdjustedFrameIndex = pendingLastAdjustedFrameIndex;
      lastAdjustedReleaseTimeNs = pendingLastAdjustedReleaseTimeNs;
    }
    frameIndex++;
    frameRateEstimator.onNextFrame(framePresentationTimeUs * 1000);
  }

  /** Called when the renderer is stopped. */
  public void onStopped() {
    started = false;
  }

  /** Called when the renderer is disabled. */
  @TargetApi(17) // displayListener is null if Util.SDK_INT < 17.
  public void onDisabled() {
    if (windowManager != null) {
      if (displayListener != null) {
        displayListener.unregister();
      }
      checkNotNull(vsyncSampler).removeObserver();
    }
  }

  // Frame release time adjustment.

  /**
   * Adjusts the release timestamp for the next frame. This is the frame whose presentation
   * timestamp was most recently passed to {@link #onNextFrame}.
   *
   * <p>This method may be called any number of times for each frame, including zero times (for
   * skipped frames, or when rendering the first frame prior to playback starting), or more than
   * once (if the caller wishes to give the helper the opportunity to refine a release time closer
   * to when the frame needs to be released).
   *
   * @param releaseTimeNs The frame's unadjusted release time, in nanoseconds and in the same time
   *     base as {@link System#nanoTime()}.
   * @return The adjusted frame release timestamp, in nanoseconds and in the same time base as
   *     {@link System#nanoTime()}.
   */
  public long adjustReleaseTime(long releaseTimeNs) {
    // Until we know better, the adjustment will be a no-op.
    long adjustedReleaseTimeNs = releaseTimeNs;

    if (lastAdjustedFrameIndex != C.INDEX_UNSET && frameRateEstimator.isSynced()) {
      long frameDurationNs = frameRateEstimator.getFrameDurationNs();
      long candidateAdjustedReleaseTimeNs =
          lastAdjustedReleaseTimeNs
              + (long) ((frameDurationNs * (frameIndex - lastAdjustedFrameIndex)) / playbackSpeed);
      if (adjustmentAllowed(releaseTimeNs, candidateAdjustedReleaseTimeNs)) {
        adjustedReleaseTimeNs = candidateAdjustedReleaseTimeNs;
      } else {
        resetAdjustment();
      }
    }
    pendingLastAdjustedFrameIndex = frameIndex;
    pendingLastAdjustedReleaseTimeNs = adjustedReleaseTimeNs;

    if (vsyncSampler == null || vsyncDurationNs == C.TIME_UNSET) {
      return adjustedReleaseTimeNs;
    }
    long sampledVsyncTimeNs = vsyncSampler.sampledVsyncTimeNs;
    if (sampledVsyncTimeNs == C.TIME_UNSET) {
      return adjustedReleaseTimeNs;
    }
    // Find the timestamp of the closest vsync. This is the vsync that we're targeting.
    long snappedTimeNs = closestVsync(adjustedReleaseTimeNs, sampledVsyncTimeNs, vsyncDurationNs);
    // Apply an offset so that we release before the target vsync, but after the previous one.
    return snappedTimeNs - vsyncOffsetNs;
  }

  private void resetAdjustment() {
    frameIndex = 0;
    lastAdjustedFrameIndex = C.INDEX_UNSET;
    pendingLastAdjustedFrameIndex = C.INDEX_UNSET;
  }

  private static boolean adjustmentAllowed(
      long unadjustedReleaseTimeNs, long adjustedReleaseTimeNs) {
    return Math.abs(unadjustedReleaseTimeNs - adjustedReleaseTimeNs) <= MAX_ALLOWED_ADJUSTMENT_NS;
  }

  // Display refresh rate and vsync logic.

  @RequiresApi(17)
  @Nullable
  private DefaultDisplayListener maybeBuildDefaultDisplayListenerV17(Context context) {
    DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    return manager == null ? null : new DefaultDisplayListener(manager);
  }

  private void updateDefaultDisplayRefreshRateParams() {
    Display defaultDisplay = checkNotNull(windowManager).getDefaultDisplay();
    if (defaultDisplay != null) {
      double defaultDisplayRefreshRate = defaultDisplay.getRefreshRate();
      vsyncDurationNs = (long) (C.NANOS_PER_SECOND / defaultDisplayRefreshRate);
      vsyncOffsetNs = (vsyncDurationNs * VSYNC_OFFSET_PERCENTAGE) / 100;
    } else {
      Log.w(TAG, "Unable to query display refresh rate");
      vsyncDurationNs = C.TIME_UNSET;
      vsyncOffsetNs = C.TIME_UNSET;
    }
  }

  private static long closestVsync(long releaseTime, long sampledVsyncTime, long vsyncDuration) {
    long vsyncCount = (releaseTime - sampledVsyncTime) / vsyncDuration;
    long snappedTimeNs = sampledVsyncTime + (vsyncDuration * vsyncCount);
    long snappedBeforeNs;
    long snappedAfterNs;
    if (releaseTime <= snappedTimeNs) {
      snappedBeforeNs = snappedTimeNs - vsyncDuration;
      snappedAfterNs = snappedTimeNs;
    } else {
      snappedBeforeNs = snappedTimeNs;
      snappedAfterNs = snappedTimeNs + vsyncDuration;
    }
    long snappedAfterDiff = snappedAfterNs - releaseTime;
    long snappedBeforeDiff = releaseTime - snappedBeforeNs;
    return snappedAfterDiff < snappedBeforeDiff ? snappedAfterNs : snappedBeforeNs;
  }

  @RequiresApi(17)
  private final class DefaultDisplayListener implements DisplayManager.DisplayListener {

    private final DisplayManager displayManager;

    public DefaultDisplayListener(DisplayManager displayManager) {
      this.displayManager = displayManager;
    }

    public void register() {
      displayManager.registerDisplayListener(this, Util.createHandlerForCurrentLooper());
    }

    public void unregister() {
      displayManager.unregisterDisplayListener(this);
    }

    @Override
    public void onDisplayAdded(int displayId) {
      // Do nothing.
    }

    @Override
    public void onDisplayRemoved(int displayId) {
      // Do nothing.
    }

    @Override
    public void onDisplayChanged(int displayId) {
      if (displayId == Display.DEFAULT_DISPLAY) {
        updateDefaultDisplayRefreshRateParams();
      }
    }

  }

  /**
   * Samples display vsync timestamps. A single instance using a single {@link Choreographer} is
   * shared by all {@link VideoFrameReleaseHelper} instances. This is done to avoid a resource leak
   * in the platform on API levels prior to 23. See [Internal: b/12455729].
   */
  private static final class VSyncSampler implements FrameCallback, Handler.Callback {

    public volatile long sampledVsyncTimeNs;

    private static final int CREATE_CHOREOGRAPHER = 0;
    private static final int MSG_ADD_OBSERVER = 1;
    private static final int MSG_REMOVE_OBSERVER = 2;

    private static final VSyncSampler INSTANCE = new VSyncSampler();

    private final Handler handler;
    private final HandlerThread choreographerOwnerThread;
    private /*@MonotonicNonNull*/ Choreographer choreographer;
    private int observerCount;

    public static VSyncSampler getInstance() {
      return INSTANCE;
    }

    private VSyncSampler() {
      sampledVsyncTimeNs = C.TIME_UNSET;
      choreographerOwnerThread = new HandlerThread("ExoPlayer:FrameReleaseChoreographer");
      choreographerOwnerThread.start();
      handler = Util.createHandler(choreographerOwnerThread.getLooper(), /* callback= */ this);
      handler.sendEmptyMessage(CREATE_CHOREOGRAPHER);
    }

    /**
     * Notifies the sampler that a {@link VideoFrameReleaseHelper} is observing {@link
     * #sampledVsyncTimeNs}, and hence that the value should be periodically updated.
     */
    public void addObserver() {
      handler.sendEmptyMessage(MSG_ADD_OBSERVER);
    }

    /**
     * Notifies the sampler that a {@link VideoFrameReleaseHelper} is no longer observing {@link
     * #sampledVsyncTimeNs}.
     */
    public void removeObserver() {
      handler.sendEmptyMessage(MSG_REMOVE_OBSERVER);
    }

    @Override
    public void doFrame(long vsyncTimeNs) {
      sampledVsyncTimeNs = vsyncTimeNs;
      checkNotNull(choreographer).postFrameCallbackDelayed(this, VSYNC_SAMPLE_UPDATE_PERIOD_MS);
    }

    @Override
    public boolean handleMessage(Message message) {
      switch (message.what) {
        case CREATE_CHOREOGRAPHER: {
          createChoreographerInstanceInternal();
          return true;
        }
        case MSG_ADD_OBSERVER: {
          addObserverInternal();
          return true;
        }
        case MSG_REMOVE_OBSERVER: {
          removeObserverInternal();
          return true;
        }
        default: {
          return false;
        }
      }
    }

    private void createChoreographerInstanceInternal() {
      choreographer = Choreographer.getInstance();
    }

    private void addObserverInternal() {
      observerCount++;
      if (observerCount == 1) {
        checkNotNull(choreographer).postFrameCallback(this);
      }
    }

    private void removeObserverInternal() {
      observerCount--;
      if (observerCount == 0) {
        checkNotNull(choreographer).removeFrameCallback(this);
        sampledVsyncTimeNs = C.TIME_UNSET;
      }
    }

  }
}
