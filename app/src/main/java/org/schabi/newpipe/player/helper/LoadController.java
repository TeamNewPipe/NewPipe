package org.schabi.newpipe.player.helper;

import android.content.Context;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

public class LoadController implements LoadControl {

    public static final String TAG = "LoadController";

    private final LoadControl internalLoadControl;

    /*//////////////////////////////////////////////////////////////////////////
    // Default Load Control
    //////////////////////////////////////////////////////////////////////////*/

    public LoadController(final Context context) {
        this(PlayerHelper.getMinBufferMs(context),
                PlayerHelper.getMaxBufferMs(context),
                PlayerHelper.getBufferForPlaybackMs(context),
                PlayerHelper.getBufferForPlaybackAfterRebufferMs(context));
    }

    public LoadController(final int minBufferMs,
                          final int maxBufferMs,
                          final long bufferForPlaybackMs,
                          final long bufferForPlaybackAfterRebufferMs) {
        final DefaultAllocator allocator = new DefaultAllocator(true, 65536);
        internalLoadControl = new DefaultLoadControl(allocator, minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Custom behaviours
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPrepared() {
        internalLoadControl.onPrepared();
    }

    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroupArray, TrackSelectionArray trackSelectionArray) {
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
    public boolean shouldStartPlayback(long l, boolean b) {
        return internalLoadControl.shouldStartPlayback(l, b);
    }

    @Override
    public boolean shouldContinueLoading(long l) {
        return internalLoadControl.shouldContinueLoading(l);
    }
}
