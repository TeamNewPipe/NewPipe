package org.schabi.newpipe.player.mediasource;

import android.util.Log;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.CompositeMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SilenceMediaSource;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.mediaitem.ExceptionTag;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FailedMediaSource extends CompositeMediaSource<Void> implements ManagedMediaSource {
    /**
     * Play 2 seconds of silenced audio when a stream fails to resolve due to a known issue,
     * such as {@link org.schabi.newpipe.extractor.exceptions.ExtractionException}.
     *
     * This silence duration allows user to react and have time to jump to a previous stream,
     * while still provide a smooth playback experience. A duration lower than 1 second is
     * not recommended, it may cause ExoPlayer to buffer for a while.
     * */
    public static final long SILENCE_DURATION_US = TimeUnit.SECONDS.toMicros(2);

    private final String TAG = "FailedMediaSource@" + Integer.toHexString(hashCode());
    private final PlayQueueItem playQueueItem;
    private final Throwable error;
    private final long retryTimestamp;
    private final MediaSource source;
    private final MediaItem mediaItem;
    /**
     * Fail the play queue item associated with this source, with potential future retries.
     *
     * The error will be propagated if the cause for load exception is unspecified.
     * This means the error might be caused by reasons outside of extraction (e.g. no network).
     * Otherwise, a silenced stream will play instead.
     *
     * @param playQueueItem  play queue item
     * @param error          exception that was the reason to fail
     * @param retryTimestamp epoch timestamp when this MediaSource can be refreshed
     */
    public FailedMediaSource(@NonNull final PlayQueueItem playQueueItem,
                             @NonNull final Throwable error,
                             final long retryTimestamp) {
        this.playQueueItem = playQueueItem;
        this.error = error;
        this.retryTimestamp = retryTimestamp;

        final MediaItemTag tag = ExceptionTag
                .of(playQueueItem, Collections.singletonList(error))
                .withExtras(this);
        this.mediaItem = tag.asMediaItem();
        this.source = new SilenceMediaSource.Factory()
                .setDurationUs(SILENCE_DURATION_US)
                .setTag(tag)
                .createMediaSource();
    }

    public static FailedMediaSource of(@NonNull final PlayQueueItem playQueueItem,
                                       @NonNull final FailedMediaSourceException error) {
        return new FailedMediaSource(playQueueItem, error, Long.MAX_VALUE);
    }

    public static FailedMediaSource of(@NonNull final PlayQueueItem playQueueItem,
                                       @NonNull final Throwable error,
                                       final long retryWaitMillis) {
        return new FailedMediaSource(playQueueItem, error,
                System.currentTimeMillis() + retryWaitMillis);
    }

    public PlayQueueItem getStream() {
        return playQueueItem;
    }

    public Throwable getError() {
        return error;
    }

    private boolean canRetry() {
        return System.currentTimeMillis() >= retryTimestamp;
    }

    /**
     * Returns the {@link MediaItem} whose media is provided by the source.
     */
    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        super.prepareSourceInternal(mediaTransferListener);
        Log.e(TAG, "Loading failed source: ", error);
        if (error instanceof FailedMediaSourceException) {
            prepareChildSource(null, source);
        }
    }


    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (!(error instanceof FailedMediaSourceException)) {
            throw new IOException(error);
        }
        super.maybeThrowSourceInfoRefreshError();
    }

    @Override
    protected void onChildSourceInfoRefreshed(final Void id,
                                              final MediaSource mediaSource,
                                              final Timeline timeline) {
        refreshSourceInfo(timeline);
    }


    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id, final Allocator allocator,
                                    final long startPositionUs) {
        return source.createPeriod(id, allocator, startPositionUs);
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) {
        source.releasePeriod(mediaPeriod);
    }

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
