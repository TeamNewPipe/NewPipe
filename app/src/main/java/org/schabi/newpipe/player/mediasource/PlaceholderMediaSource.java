package org.schabi.newpipe.player.mediasource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public class PlaceholderMediaSource extends BaseMediaSource implements ManagedMediaSource {
    // Do nothing, so this will stall the playback
    @Override
    public void maybeThrowSourceInfoRefreshError() { }

    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id, final Allocator allocator,
                                    final long startPositionUs) {
        return null;
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) { }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) { }

    @Override
    protected void releaseSourceInternal() { }

    @Override
    public boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return true;
    }

    @Override
    public boolean isStreamEqual(@NonNull final PlayQueueItem stream) {
        return false;
    }
}
