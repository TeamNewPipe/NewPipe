package org.schabi.newpipe.player.mediasource

import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.WrappingMediaSource
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediasource.LoadedMediaSource
import org.schabi.newpipe.player.mediasource.ManagedMediaSource
import org.schabi.newpipe.player.mediasource.ManagedMediaSourcePlaylist
import org.schabi.newpipe.player.playqueue.PlayQueueItem

class LoadedMediaSource(source: MediaSource,
                        tag: MediaItemTag,
                        private val stream: PlayQueueItem,
                        private val expireTimestamp: Long) : WrappingMediaSource(source), ManagedMediaSource {
    private val mediaItem: MediaItem

    /**
     * Uses a [WrappingMediaSource] to wrap one child [MediaSource]s
     * containing actual media. This wrapper [LoadedMediaSource] holds the expiration
     * timestamp as a [ManagedMediaSource] to allow explicit playlist management under
     * [ManagedMediaSourcePlaylist].
     *
     * @param source            The child media source with actual media.
     * @param tag               Metadata for the child media source.
     * @param stream            The queue item associated with the media source.
     * @param expireTimestamp   The timestamp when the media source expires and might not be
     * available for playback.
     */
    init {
        mediaItem = tag.withExtras(this).asMediaItem()
    }

    fun getStream(): PlayQueueItem {
        return stream
    }

    private fun isExpired(): Boolean {
        return System.currentTimeMillis() >= expireTimestamp
    }

    public override fun getMediaItem(): MediaItem {
        return mediaItem
    }

    public override fun shouldBeReplacedWith(newIdentity: PlayQueueItem,
                                             isInterruptable: Boolean): Boolean {
        return newIdentity !== stream || (isInterruptable && isExpired())
    }

    public override fun isStreamEqual(otherStream: PlayQueueItem): Boolean {
        return stream === otherStream
    }
}
