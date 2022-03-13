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

public class LoadedMediaSource extends CompositeMediaSource<Void> implements ManagedMediaSource {
    private final MediaSource source;
    private final PlayQueueItem stream;
    private final MediaItem mediaItem;
    private final long expireTimestamp;

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

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        super.prepareSourceInternal(mediaTransferListener);
        prepareChildSource(null, source);
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
