package org.schabi.newpipe.player.mediasource;

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public interface ManagedMediaSource extends MediaSource {
    /**
     * Determines whether or not this {@link ManagedMediaSource} can be replaced.
     *
     * @param newIdentity a stream the {@link ManagedMediaSource} should encapsulate over, if
     *                    it is different from the existing stream in the
     *                    {@link ManagedMediaSource}, then it should be replaced.
     * @param isInterruptable specifies if this {@link ManagedMediaSource} potentially
     *                        being played.
     * */
    boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                 final boolean isInterruptable);

    /**
     * Determines if the {@link PlayQueueItem} is the one the
     * {@link ManagedMediaSource} encapsulates over.
     * */
    boolean isStreamEqual(@NonNull final PlayQueueItem stream);
}
