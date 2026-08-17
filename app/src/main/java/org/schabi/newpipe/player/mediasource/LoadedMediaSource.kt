@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.mediasource

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

class LoadedMediaSource(
    source: MediaSource,
    tag: MediaItemTag,
    val stream: PlayQueueItem,
    private val expireTimestamp: Long
) : WrappingMediaSource(source), ManagedMediaSource {

    private val mediaItem: MediaItem = tag.withExtras(this).asMediaItem()

    private val isExpired: Boolean
        get() = System.currentTimeMillis() >= expireTimestamp

    override fun getMediaItem(): MediaItem = mediaItem

    override fun shouldBeReplacedWith(newIdentity: PlayQueueItem, isInterruptable: Boolean): Boolean {
        return newIdentity != stream || (isInterruptable && isExpired)
    }

    override fun isStreamEqual(stream: PlayQueueItem): Boolean {
        return this.stream == stream
    }
}
