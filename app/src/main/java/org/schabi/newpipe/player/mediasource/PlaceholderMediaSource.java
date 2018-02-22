package org.schabi.newpipe.player.mediasource;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.Allocator;

import java.io.IOException;

public class PlaceholderMediaSource implements ManagedMediaSource {
    // Do nothing, so this will stall the playback
    @Override public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {}
    @Override public void maybeThrowSourceInfoRefreshError() throws IOException {}
    @Override public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) { return null; }
    @Override public void releasePeriod(MediaPeriod mediaPeriod) {}
    @Override public void releaseSource() {}

    @Override
    public boolean canReplace() {
        return true;
    }
}
