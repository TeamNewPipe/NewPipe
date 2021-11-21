package org.schabi.newpipe.player.helper;

import com.google.android.exoplayer2.DefaultLoadControl;

public class LoadController extends DefaultLoadControl {

    public static final String TAG = "LoadController";
    private boolean preloadingEnabled = true;

    @Override
    public void onPrepared() {
        preloadingEnabled = true;
        super.onPrepared();
    }

    @Override
    public void onStopped() {
        preloadingEnabled = true;
        super.onStopped();
    }

    @Override
    public void onReleased() {
        preloadingEnabled = true;
        super.onReleased();
    }

    @Override
    public boolean shouldContinueLoading(final long playbackPositionUs,
                                         final long bufferedDurationUs,
                                         final float playbackSpeed) {
        if (!preloadingEnabled) {
            return false;
        }
        return super.shouldContinueLoading(
                playbackPositionUs, bufferedDurationUs, playbackSpeed);
    }

    public void disablePreloadingOfCurrentTrack() {
        preloadingEnabled = false;
    }
}
