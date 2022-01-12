package org.schabi.newpipe.player.mediasource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public interface ManagedMediaSource extends MediaSource {
    /**
     * Determines whether or not this {@link ManagedMediaSource} can be replaced.
     *
     * @param newIdentity     a stream the {@link ManagedMediaSource} should encapsulate over, if
     *                        it is different from the existing stream in the
     *                        {@link ManagedMediaSource}, then it should be replaced.
     * @param isInterruptable specifies if this {@link ManagedMediaSource} potentially
     *                        being played.
     * @return whether this could be replaces
     */
    boolean shouldBeReplacedWith(@NonNull PlayQueueItem newIdentity, boolean isInterruptable);

    /**
     * Determines if the {@link PlayQueueItem} is the one the
     * {@link ManagedMediaSource} encapsulates over.
     *
     * @param stream play queue item to check
     * @return whether this source is for the specified stream
     */
    boolean isStreamEqual(@NonNull PlayQueueItem stream);

    @Nullable
    @Override
    default Object getTag() {
        return this;
    }
}
