package org.schabi.newpipe.player.mediasource;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.WrappingMediaSource;

import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public class LoadedMediaSource extends WrappingMediaSource implements ManagedMediaSource {
    private final PlayQueueItem stream;
    private final MediaItem mediaItem;
    private final long expireTimestamp;

    /**
     * Uses a {@link WrappingMediaSource} to wrap one child {@link MediaSource}s
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
        super(source);
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
