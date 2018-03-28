package org.schabi.newpipe.player.mediasource;

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.upstream.Allocator;

import org.schabi.newpipe.playlist.PlayQueueItem;

import java.io.IOException;

public class PlaceholderMediaSource implements ManagedMediaSource {
    // Do nothing, so this will stall the playback
    @Override public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {}
    @Override public void maybeThrowSourceInfoRefreshError() throws IOException {}
    @Override public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) { return null; }
    @Override public void releasePeriod(MediaPeriod mediaPeriod) {}
    @Override public void releaseSource() {}

    @Override
    public boolean shouldBeReplacedWith(@NonNull PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return true;
    }

    @Override
    public boolean isStreamEqual(@NonNull PlayQueueItem stream) {
        return false;
    }
}
