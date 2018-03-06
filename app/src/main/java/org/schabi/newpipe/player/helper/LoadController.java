package org.schabi.newpipe.player.helper;

import android.content.Context;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS;
import static com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES;

public class LoadController implements LoadControl {

    public static final String TAG = "LoadController";

    private final long initialPlaybackBufferUs;
    private final LoadControl internalLoadControl;

    /*//////////////////////////////////////////////////////////////////////////
    // Default Load Control
    //////////////////////////////////////////////////////////////////////////*/

    public LoadController(final Context context) {
        this(PlayerHelper.getPlaybackStartBufferMs(context),
                PlayerHelper.getPlaybackMinimumBufferMs(context),
                PlayerHelper.getPlaybackOptimalBufferMs(context));
    }

    private LoadController(final int initialPlaybackBufferMs,
                           final int minimumPlaybackbufferMs,
                           final int optimalPlaybackBufferMs) {
        this.initialPlaybackBufferUs = initialPlaybackBufferMs * 1000;

        final DefaultAllocator allocator = new DefaultAllocator(true,
                C.DEFAULT_BUFFER_SEGMENT_SIZE);

        internalLoadControl = new DefaultLoadControl(allocator,
                /*minBufferMs=*/minimumPlaybackbufferMs,
                /*maxBufferMs=*/optimalPlaybackBufferMs,
                /*bufferForPlaybackMs=*/initialPlaybackBufferMs,
                /*bufferForPlaybackAfterRebufferMs=*/initialPlaybackBufferMs,
                DEFAULT_TARGET_BUFFER_BYTES, DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Custom behaviours
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPrepared() {
        internalLoadControl.onPrepared();
    }

    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroupArray,
                                 TrackSelectionArray trackSelectionArray) {
        internalLoadControl.onTracksSelected(renderers, trackGroupArray, trackSelectionArray);
    }

    @Override
    public void onStopped() {
        internalLoadControl.onStopped();
    }

    @Override
    public void onReleased() {
        internalLoadControl.onReleased();
    }

    @Override
    public Allocator getAllocator() {
        return internalLoadControl.getAllocator();
    }

    @Override
    public long getBackBufferDurationUs() {
        return internalLoadControl.getBackBufferDurationUs();
    }

    @Override
    public boolean retainBackBufferFromKeyframe() {
        return internalLoadControl.retainBackBufferFromKeyframe();
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
        return internalLoadControl.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed,
                                       boolean rebuffering) {
        final boolean isInitialPlaybackBufferFilled = bufferedDurationUs >=
                this.initialPlaybackBufferUs * playbackSpeed;
        final boolean isInternalStartingPlayback = internalLoadControl.shouldStartPlayback(
                bufferedDurationUs, playbackSpeed, rebuffering);
        return isInitialPlaybackBufferFilled || isInternalStartingPlayback;
    }
}
