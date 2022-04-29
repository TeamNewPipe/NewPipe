package org.schabi.newpipe.player.mediasource;

import android.util.Log;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SilenceMediaSource;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.mediaitem.ExceptionTag;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FailedMediaSource extends BaseMediaSource implements ManagedMediaSource {
    /**
     * Play 2 seconds of silenced audio when a stream fails to resolve due to a known issue,
     * such as {@link org.schabi.newpipe.extractor.exceptions.ExtractionException}.
     *
     * This silence duration allows user to react and have time to jump to a previous stream,
     * while still provide a smooth playback experience. A duration lower than 1 second is
     * not recommended, it may cause ExoPlayer to buffer for a while.
     * */
    public static final long SILENCE_DURATION_US = TimeUnit.SECONDS.toMicros(2);
    public static final MediaPeriod SILENT_MEDIA = makeSilentMediaPeriod(SILENCE_DURATION_US);

    private final String TAG = "FailedMediaSource@" + Integer.toHexString(hashCode());
    private final PlayQueueItem playQueueItem;
    private final Exception error;
    private final long retryTimestamp;
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
                             @NonNull final Exception error,
                             final long retryTimestamp) {
        this.playQueueItem = playQueueItem;
        this.error = error;
        this.retryTimestamp = retryTimestamp;
        this.mediaItem = ExceptionTag
                .of(playQueueItem, Collections.singletonList(error))
                .withExtras(this)
                .asMediaItem();
    }

    public static FailedMediaSource of(@NonNull final PlayQueueItem playQueueItem,
                                       @NonNull final FailedMediaSourceException error) {
        return new FailedMediaSource(playQueueItem, error, Long.MAX_VALUE);
    }

    public static FailedMediaSource of(@NonNull final PlayQueueItem playQueueItem,
                                       @NonNull final Exception error,
                                       final long retryWaitMillis) {
        return new FailedMediaSource(playQueueItem, error,
                System.currentTimeMillis() + retryWaitMillis);
    }

    public PlayQueueItem getStream() {
        return playQueueItem;
    }

    public Exception getError() {
        return error;
    }

    private boolean canRetry() {
        return System.currentTimeMillis() >= retryTimestamp;
    }

    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    /**
     * Prepares the source with {@link Timeline} info on the silence playback when the error
     * is classed as {@link FailedMediaSourceException}, for example, when the error is
     * {@link org.schabi.newpipe.extractor.exceptions.ExtractionException ExtractionException}.
     * These types of error are swallowed by {@link FailedMediaSource}, and the underlying
     * exception is carried to the {@link MediaItem} metadata during playback.
     * <br><br>
     * If the exception is not known, e.g. {@link java.net.UnknownHostException} or some
     * other network issue, then no source info is refreshed and
     * {@link #maybeThrowSourceInfoRefreshError()} be will triggered.
     * <br><br>
     * Note that this method is called only once until {@link #releaseSourceInternal()} is called,
     * so if no action is done in here, playback will stall unless
     * {@link #maybeThrowSourceInfoRefreshError()} is called.
     *
     * @param mediaTransferListener No data transfer listener needed, ignored here.
     */
    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        Log.e(TAG, "Loading failed source: ", error);
        if (error instanceof FailedMediaSourceException) {
            refreshSourceInfo(makeSilentMediaTimeline(SILENCE_DURATION_US, mediaItem));
        }
    }

    /**
     * If the error is not known, e.g. network issue, then the exception is not swallowed here in
     * {@link FailedMediaSource}. The exception is then propagated to the player, which
     * {@link org.schabi.newpipe.player.Player Player} can react to inside
     * {@link com.google.android.exoplayer2.Player.Listener#onPlayerError(PlaybackException)}.
     *
     * @throws IOException An error which will always result in
     * {@link com.google.android.exoplayer2.PlaybackException#ERROR_CODE_IO_UNSPECIFIED}.
     */
    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (!(error instanceof FailedMediaSourceException)) {
            throw new IOException(error);
        }
    }

    /**
     * This method is only called if {@link #prepareSourceInternal(TransferListener)}
     * refreshes the source info with no exception. All parameters are ignored as this
     * returns a static and reused piece of silent audio.
     *
     * @param id                The identifier of the period.
     * @param allocator         An {@link Allocator} from which to obtain media buffer allocations.
     * @param startPositionUs   The expected start position, in microseconds.
     * @return The common {@link MediaPeriod} holding the silence.
     */
    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id,
                                    final Allocator allocator,
                                    final long startPositionUs) {
        return SILENT_MEDIA;
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) {
        /* Do Nothing (we want to keep re-using the Silent MediaPeriod) */
    }

    @Override
    protected void releaseSourceInternal() {
        /* Do Nothing, no clean-up for processing/extra thread is needed by this MediaSource */
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

    private static Timeline makeSilentMediaTimeline(final long durationUs,
                                                    @NonNull final MediaItem mediaItem) {
        return new SinglePeriodTimeline(
                durationUs,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* useLiveConfiguration= */ false,
                /* manifest= */ null,
                mediaItem);
    }

    private static MediaPeriod makeSilentMediaPeriod(final long durationUs) {
        return new SilenceMediaSource.Factory()
                .setDurationUs(durationUs)
                .createMediaSource()
                .createPeriod(null, null, 0);
    }
}
