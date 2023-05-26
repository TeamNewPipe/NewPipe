package org.schabi.newpipe.player.mediasource

import com.google.android.exoplayer2.source.MediaSource
import org.schabi.newpipe.player.playqueue.PlayQueueItem

interface ManagedMediaSource : MediaSource {
    /**
     * Determines whether or not this [ManagedMediaSource] can be replaced.
     *
     * @param newIdentity     a stream the [ManagedMediaSource] should encapsulate over, if
     * it is different from the existing stream in the
     * [ManagedMediaSource], then it should be replaced.
     * @param isInterruptable specifies if this [ManagedMediaSource] potentially
     * being played.
     * @return whether this could be replaces
     */
    fun shouldBeReplacedWith(newIdentity: PlayQueueItem, isInterruptable: Boolean): Boolean

    /**
     * Determines if the [PlayQueueItem] is the one the
     * [ManagedMediaSource] encapsulates over.
     *
     * @param stream play queue item to check
     * @return whether this source is for the specified stream
     */
    fun isStreamEqual(stream: PlayQueueItem): Boolean
}
