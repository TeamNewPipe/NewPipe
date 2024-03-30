package org.schabi.newpipe.player.playback

import com.google.android.exoplayer2.source.MediaSource
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueueItem

open interface PlaybackListener {
    /**
     * Called to check if the currently playing stream is approaching the end of its playback.
     * Implementation should return true when the current playback position is progressing within
     * timeToEndMillis or less to its playback during.
     *
     *
     * May be called at any time.
     *
     *
     * @param timeToEndMillis
     * @return whether the stream is approaching end of playback
     */
    fun isApproachingPlaybackEdge(timeToEndMillis: Long): Boolean

    /**
     * Called when the stream at the current queue index is not ready yet.
     * Signals to the listener to block the player from playing anything and notify the source
     * is now invalid.
     *
     *
     * May be called at any time.
     *
     */
    fun onPlaybackBlock()

    /**
     * Called when the stream at the current queue index is ready.
     * Signals to the listener to resume the player by preparing a new source.
     *
     *
     * May be called only when the player is blocked.
     *
     *
     * @param mediaSource
     */
    fun onPlaybackUnblock(mediaSource: MediaSource?)

    /**
     * Called when the queue index is refreshed.
     * Signals to the listener to synchronize the player's window to the manager's
     * window.
     *
     *
     * May be called anytime at any amount once unblock is called.
     *
     *
     * @param item          item the player should be playing/synchronized to
     * @param wasBlocked    was the player recently released from blocking state
     */
    fun onPlaybackSynchronize(item: PlayQueueItem, wasBlocked: Boolean)

    /**
     * Requests the listener to resolve a stream info into a media source
     * according to the listener's implementation (background, popup or main video player).
     *
     *
     * May be called at any time.
     *
     * @param item
     * @param info
     * @return the corresponding [MediaSource]
     */
    fun sourceOf(item: PlayQueueItem?, info: StreamInfo): MediaSource?

    /**
     * Called when the play queue can no longer be played or used.
     * Currently, this means the play queue is empty and complete.
     * Signals to the listener that it should shutdown.
     *
     *
     * May be called at any time.
     *
     */
    fun onPlaybackShutdown()

    /**
     * Called whenever the play queue was edited (items were added, deleted or moved),
     * use this to e.g. update notification buttons or fragment ui.
     *
     *
     * May be called at any time.
     *
     */
    fun onPlayQueueEdited()
}
