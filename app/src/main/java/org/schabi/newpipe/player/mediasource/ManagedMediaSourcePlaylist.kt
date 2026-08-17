@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.mediasource

import android.os.Handler
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import org.schabi.newpipe.player.mediaitem.MediaItemTag

class ManagedMediaSourcePlaylist {
    val parentMediaSource = ConcatenatingMediaSource(
        /*isPlaylistAtomic=*/
        false,
        ShuffleOrder.UnshuffledShuffleOrder(0)
    )

    fun size(): Int = parentMediaSource.size

    fun get(index: Int): ManagedMediaSource? {
        if (index < 0 || index >= size()) {
            return null
        }

        return MediaItemTag.from(parentMediaSource.getMediaSource(index).mediaItem)
            ?.getMaybeExtras(ManagedMediaSource::class.java)
    }

    @Synchronized
    fun expand() {
        append(PlaceholderMediaSource.COPY)
    }

    @Synchronized
    fun append(source: ManagedMediaSource) {
        parentMediaSource.addMediaSource(source)
    }

    @Synchronized
    fun remove(index: Int) {
        if (index < 0 || index >= parentMediaSource.size) {
            return
        }

        parentMediaSource.removeMediaSource(index)
    }

    @Synchronized
    fun move(source: Int, target: Int) {
        if (source < 0 || target < 0) {
            return
        }
        if (source >= parentMediaSource.size || target >= parentMediaSource.size) {
            return
        }

        parentMediaSource.moveMediaSource(source, target)
    }

    @Synchronized
    fun invalidate(
        index: Int,
        handler: Handler? = null,
        finalizingAction: Runnable? = null
    ) {
        if (get(index) === PlaceholderMediaSource.COPY) {
            return
        }
        update(index, PlaceholderMediaSource.COPY, handler, finalizingAction)
    }

    @Synchronized
    fun update(index: Int, source: ManagedMediaSource) {
        update(index, source, null, null)
    }

    @Synchronized
    fun update(
        index: Int,
        source: ManagedMediaSource,
        handler: Handler?,
        finalizingAction: Runnable?
    ) {
        if (index < 0 || index >= parentMediaSource.size) {
            return
        }

        parentMediaSource.addMediaSource(index + 1, source)
        if (handler != null && finalizingAction != null) {
            parentMediaSource.removeMediaSource(index, handler, finalizingAction)
        } else {
            parentMediaSource.removeMediaSource(index)
        }
    }
}
