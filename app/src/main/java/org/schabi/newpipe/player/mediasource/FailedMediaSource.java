package org.schabi.newpipe.player.mediasource;

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.Allocator;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueueItem;

import java.io.IOException;

public class FailedMediaSource implements ManagedMediaSource {

    private final PlayQueueItem playQueueItem;
    private final Throwable error;

    private final long retryTimestamp;

    public FailedMediaSource(@NonNull final PlayQueueItem playQueueItem,
                             @NonNull final Throwable error,
                             final long retryTimestamp) {
        this.playQueueItem = playQueueItem;
        this.error = error;
        this.retryTimestamp = retryTimestamp;
    }

    public FailedMediaSource(@NonNull final PlayQueueItem playQueueItem,
                             @NonNull final Throwable error) {
        this.playQueueItem = playQueueItem;
        this.error = error;
        this.retryTimestamp = Long.MAX_VALUE;
    }

    public PlayQueueItem getPlayQueueItem() {
        return playQueueItem;
    }

    public Throwable getError() {
        return error;
    }

    public boolean canRetry() {
        return System.currentTimeMillis() >= retryTimestamp;
    }

    @Override
    public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {}

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        throw new IOException(error);
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        return null;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {}

    @Override
    public void releaseSource() {}

    @Override
    public boolean canReplace() {
        return canRetry();
    }
}
