package org.schabi.newpipe.player.mediasource;

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.upstream.Allocator;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public class PlaceholderMediaSource extends BaseMediaSource implements ManagedMediaSource {
    // Do nothing, so this will stall the playback
    @Override public void maybeThrowSourceInfoRefreshError() {}
    @Override public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) { return null; }
    @Override public void releasePeriod(MediaPeriod mediaPeriod) {}
    @Override protected void prepareSourceInternal(ExoPlayer player, boolean isTopLevelSource) {}
    @Override protected void releaseSourceInternal() {}

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
