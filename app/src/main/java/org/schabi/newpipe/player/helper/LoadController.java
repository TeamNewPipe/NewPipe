package org.schabi.newpipe.player.helper;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;

public class LoadController implements LoadControl {

    public static final String TAG = "LoadController";

    private final long initialPlaybackBufferUs;
    private final LoadControl internalLoadControl;

    /*//////////////////////////////////////////////////////////////////////////
    // Default Load Control
    //////////////////////////////////////////////////////////////////////////*/

    public LoadController() {
        this(PlayerHelper.getPlaybackStartBufferMs(),
                PlayerHelper.getPlaybackMinimumBufferMs(),
                PlayerHelper.getPlaybackOptimalBufferMs());
    }

    private LoadController(final int initialPlaybackBufferMs,
                           final int minimumPlaybackbufferMs,
                           final int optimalPlaybackBufferMs) {
        this.initialPlaybackBufferUs = initialPlaybackBufferMs * 1000;

        DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
        builder.setBufferDurationsMs(minimumPlaybackbufferMs, optimalPlaybackBufferMs,
                initialPlaybackBufferMs, initialPlaybackBufferMs);
        internalLoadControl = builder.createDefaultLoadControl();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Custom behaviours
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPrepared() {
        internalLoadControl.onPrepared();
    }

    @Override
    public void onTracksSelected(final Renderer[] renderers, final TrackGroupArray trackGroupArray,
                                 final TrackSelectionArray trackSelectionArray) {
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
    public boolean shouldContinueLoading(final long bufferedDurationUs,
                                         final float playbackSpeed) {
        return internalLoadControl.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
    }

    @Override
    public boolean shouldStartPlayback(final long bufferedDurationUs, final float playbackSpeed,
                                       final boolean rebuffering) {
        final boolean isInitialPlaybackBufferFilled
                = bufferedDurationUs >= this.initialPlaybackBufferUs * playbackSpeed;
        final boolean isInternalStartingPlayback = internalLoadControl
                .shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering);
        return isInitialPlaybackBufferFilled || isInternalStartingPlayback;
    }
}
