package org.schabi.newpipe.player.mediasource;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.source.CompositeMediaSource;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.Allocator;

import org.schabi.newpipe.player.mediaitem.PlaceholderTag;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

final class PlaceholderMediaSource
        extends CompositeMediaSource<Void> implements ManagedMediaSource {
    public static final PlaceholderMediaSource COPY = new PlaceholderMediaSource();
    private static final MediaItem MEDIA_ITEM = PlaceholderTag.EMPTY.withExtras(COPY).asMediaItem();

    private PlaceholderMediaSource() { }

    @NonNull
    @Override
    public MediaItem getMediaItem() {
        return MEDIA_ITEM;
    }

    @Override
    protected void onChildSourceInfoRefreshed(@NonNull final Void id,
                                              @NonNull final MediaSource mediaSource,
                                              @NonNull final Timeline timeline) {
        /* Do nothing, no timeline updates or error will stall playback */
    }

    @Override
    public MediaPeriod createPeriod(@NonNull final MediaPeriodId id,
                                    @NonNull final Allocator allocator,
                                    final long startPositionUs) {
        return null;
    }

    @Override
    public void releasePeriod(@NonNull final MediaPeriod mediaPeriod) { }

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
