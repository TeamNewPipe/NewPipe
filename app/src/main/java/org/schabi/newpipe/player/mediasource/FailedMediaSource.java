package org.schabi.newpipe.player.mediasource;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.io.IOException;

public class FailedMediaSource extends BaseMediaSource implements ManagedMediaSource {
    private final String TAG = "FailedMediaSource@" + Integer.toHexString(hashCode());
    private final PlayQueueItem playQueueItem;
    private final FailedMediaSourceException error;
    private final long retryTimestamp;

    public FailedMediaSource(@NonNull final PlayQueueItem playQueueItem,
                             @NonNull final FailedMediaSourceException error,
                             final long retryTimestamp) {
        this.playQueueItem = playQueueItem;
        this.error = error;
        this.retryTimestamp = retryTimestamp;
    }

    /**
     * Permanently fail the play queue item associated with this source, with no hope of retrying.
     * The error will always be propagated to ExoPlayer.
     *
     * @param playQueueItem play queue item
     * @param error         exception that was the reason to fail
     */
    public FailedMediaSource(@NonNull final PlayQueueItem playQueueItem,
                             @NonNull final FailedMediaSourceException error) {
        this.playQueueItem = playQueueItem;
        this.error = error;
        this.retryTimestamp = Long.MAX_VALUE;
    }

    public PlayQueueItem getStream() {
        return playQueueItem;
    }

    public FailedMediaSourceException getError() {
        return error;
    }

    private boolean canRetry() {
        return System.currentTimeMillis() >= retryTimestamp;
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        throw new IOException(error);
    }

    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id, final Allocator allocator,
                                    final long startPositionUs) {
        return null;
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) { }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        Log.e(TAG, "Loading failed source: ", error);
    }

    @Override
    protected void releaseSourceInternal() { }

    @Override
    public boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return newIdentity != playQueueItem || canRetry();
    }

    @Override
    public boolean isStreamEqual(@NonNull final PlayQueueItem stream) {
        return playQueueItem == stream;
    }

    public static class FailedMediaSourceException extends Exception {
        FailedMediaSourceException(final String message) {
            super(message);
        }

        FailedMediaSourceException(final Throwable cause) {
            super(cause);
        }
    }

    public static final class MediaSourceResolutionException extends FailedMediaSourceException {
        public MediaSourceResolutionException(final String message) {
            super(message);
        }
    }

    public static final class StreamInfoLoadException extends FailedMediaSourceException {
        public StreamInfoLoadException(final Throwable cause) {
            super(cause);
        }
    }
}
