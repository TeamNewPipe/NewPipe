package org.schabi.newpipe.player.mediasource

import android.os.Handler
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ShuffleOrder.UnshuffledShuffleOrder
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import java.util.Optional
import java.util.function.Function

class ManagedMediaSourcePlaylist() {
    private val internalSource: ConcatenatingMediaSource

    init {
        internalSource = ConcatenatingMediaSource( /*isPlaylistAtomic=*/false,
                UnshuffledShuffleOrder(0))
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Delegations
    ////////////////////////////////////////////////////////////////////////// */
    fun size(): Int {
        return internalSource.getSize()
    }

    /**
     * Returns the [ManagedMediaSource] at the given index of the playlist.
     * If the index is invalid, then null is returned.
     *
     * @param index index of [ManagedMediaSource] to get from the playlist
     * @return the [ManagedMediaSource] at the given index of the playlist
     */
    operator fun get(index: Int): ManagedMediaSource? {
        if (index < 0 || index >= size()) {
            return null
        }
        return MediaItemTag.Companion.from(internalSource.getMediaSource(index).getMediaItem())
                .flatMap<ManagedMediaSource?>(Function<MediaItemTag, Optional<out ManagedMediaSource?>?>({ tag: MediaItemTag -> tag.getMaybeExtras<ManagedMediaSource>(ManagedMediaSource::class.java) }))
                .orElse(null)
    }

    fun getParentMediaSource(): ConcatenatingMediaSource {
        return internalSource
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Manipulation
    ////////////////////////////////////////////////////////////////////////// */
    /**
     * Expands the [ConcatenatingMediaSource] by appending it with a
     * [PlaceholderMediaSource].
     *
     * @see .append
     */
    @Synchronized
    fun expand() {
        append(PlaceholderMediaSource.Companion.COPY)
    }

    /**
     * Appends a [ManagedMediaSource] to the end of [ConcatenatingMediaSource].
     *
     * @see ConcatenatingMediaSource.addMediaSource
     *
     * @param source [ManagedMediaSource] to append
     */
    @Synchronized
    fun append(source: ManagedMediaSource) {
        internalSource.addMediaSource(source)
    }

    /**
     * Removes a [ManagedMediaSource] from [ConcatenatingMediaSource]
     * at the given index. If this index is out of bound, then the removal is ignored.
     *
     * @see ConcatenatingMediaSource.removeMediaSource
     * @param index of [ManagedMediaSource] to be removed
     */
    @Synchronized
    fun remove(index: Int) {
        if (index < 0 || index > internalSource.getSize()) {
            return
        }
        internalSource.removeMediaSource(index)
    }

    /**
     * Moves a [ManagedMediaSource] in [ConcatenatingMediaSource]
     * from the given source index to the target index. If either index is out of bound,
     * then the call is ignored.
     *
     * @see ConcatenatingMediaSource.moveMediaSource
     * @param source original index of [ManagedMediaSource]
     * @param target new index of [ManagedMediaSource]
     */
    @Synchronized
    fun move(source: Int, target: Int) {
        if (source < 0 || target < 0) {
            return
        }
        if (source >= internalSource.getSize() || target >= internalSource.getSize()) {
            return
        }
        internalSource.moveMediaSource(source, target)
    }

    /**
     * Invalidates the [ManagedMediaSource] at the given index by replacing it
     * with a [PlaceholderMediaSource].
     *
     * @see .update
     * @param index            index of [ManagedMediaSource] to invalidate
     * @param handler          the [Handler] to run `finalizingAction`
     * @param finalizingAction a [Runnable] which is executed immediately
     * after the media source has been removed from the playlist
     */
    @Synchronized
    fun invalidate(index: Int,
                   handler: Handler?,
                   finalizingAction: Runnable?) {
        if (get(index) === PlaceholderMediaSource.Companion.COPY) {
            return
        }
        update(index, PlaceholderMediaSource.Companion.COPY, handler, finalizingAction)
    }

    /**
     * Updates the [ManagedMediaSource] in [ConcatenatingMediaSource]
     * at the given index with a given [ManagedMediaSource].
     *
     * @see .update
     * @param index  index of [ManagedMediaSource] to update
     * @param source new [ManagedMediaSource] to use
     */
    @Synchronized
    fun update(index: Int, source: ManagedMediaSource) {
        update(index, source, null,  /*doNothing=*/null)
    }

    /**
     * Updates the [ManagedMediaSource] in [ConcatenatingMediaSource]
     * at the given index with a given [ManagedMediaSource]. If the index is out of bound,
     * then the replacement is ignored.
     *
     * @see ConcatenatingMediaSource.addMediaSource
     *
     * @see ConcatenatingMediaSource.removeMediaSource
     * @param index            index of [ManagedMediaSource] to update
     * @param source           new [ManagedMediaSource] to use
     * @param handler          the [Handler] to run `finalizingAction`
     * @param finalizingAction a [Runnable] which is executed immediately
     * after the media source has been removed from the playlist
     */
    @Synchronized
    fun update(index: Int, source: ManagedMediaSource,
               handler: Handler?,
               finalizingAction: Runnable?) {
        if (index < 0 || index >= internalSource.getSize()) {
            return
        }

        // Add and remove are sequential on the same thread, therefore here, the exoplayer
        // message queue must receive and process add before remove, effectively treating them
        // as atomic.

        // Since the finalizing action occurs strictly after the timeline has completed
        // all its changes on the playback thread, thus, it is possible, in the meantime,
        // other calls that modifies the playlist media source occur in between. This makes
        // it unsafe to call remove as the finalizing action of add.
        internalSource.addMediaSource(index + 1, source)

        // Because of the above race condition, it is thus only safe to synchronize the player
        // in the finalizing action AFTER the removal is complete and the timeline has changed.
        internalSource.removeMediaSource(index, (handler)!!, (finalizingAction)!!)
    }
}
