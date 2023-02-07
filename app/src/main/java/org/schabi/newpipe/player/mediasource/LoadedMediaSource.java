package org.schabi.newpipe.player.mediasource;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.CompositeMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LoadedMediaSource extends CompositeMediaSource<Integer> implements ManagedMediaSource {
    private final MediaSource source;
    private final PlayQueueItem stream;
    private final MediaItem mediaItem;
    private final long expireTimestamp;

    /**
     * Uses a {@link CompositeMediaSource} to wrap one or more child {@link MediaSource}s
     * containing actual media. This wrapper {@link LoadedMediaSource} holds the expiration
     * timestamp as a {@link ManagedMediaSource} to allow explicit playlist management under
     * {@link ManagedMediaSourcePlaylist}.
     *
     * @param source            The child media source with actual media.
     * @param tag               Metadata for the child media source.
     * @param stream            The queue item associated with the media source.
     * @param expireTimestamp   The timestamp when the media source expires and might not be
     *                          available for playback.
     */
    public LoadedMediaSource(@NonNull final MediaSource source,
                             @NonNull final MediaItemTag tag,
                             @NonNull final PlayQueueItem stream,
                             final long expireTimestamp) {
        this.source = source;
        this.stream = stream;
        this.expireTimestamp = expireTimestamp;

        this.mediaItem = tag.withExtras(this).asMediaItem();
    }

    public PlayQueueItem getStream() {
        return stream;
    }

    private boolean isExpired() {
        return System.currentTimeMillis() >= expireTimestamp;
    }

    /**
     * Delegates the preparation of child {@link MediaSource}s to the
     * {@link CompositeMediaSource} wrapper. Since all {@link LoadedMediaSource}s use only
     * a single child media, the child id of 0 is always used (sonar doesn't like null as id here).
     *
     * @param mediaTransferListener A data transfer listener that will be registered by the
     *                              {@link CompositeMediaSource} for child source preparation.
     */
    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        super.prepareSourceInternal(mediaTransferListener);
        prepareChildSource(0, source);
    }

    /**
     * When any child {@link MediaSource} is prepared, the refreshed {@link Timeline} can
     * be listened to here. But since {@link LoadedMediaSource} has only a single child source,
     * this method is called only once until {@link #releaseSourceInternal()} is called.
     * <br><br>
     * On refresh, the {@link CompositeMediaSource} delegate will be notified with the
     * new {@link Timeline}, otherwise {@link #createPeriod(MediaPeriodId, Allocator, long)}
     * will not be called and playback may be stalled.
     *
     * @param id            The unique id used to prepare the child source.
     * @param mediaSource   The child source whose source info has been refreshed.
     * @param timeline      The new timeline of the child source.
     */
    @Override
    protected void onChildSourceInfoRefreshed(final Integer id,
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

    @NonNull
    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    @Override
    public boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return newIdentity != stream || (isInterruptable && isExpired());
    }

    @Override
    public boolean isStreamEqual(@NonNull final PlayQueueItem otherStream) {
        return this.stream == otherStream;
    }
}
