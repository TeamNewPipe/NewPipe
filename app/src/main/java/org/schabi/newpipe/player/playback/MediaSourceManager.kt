@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.collection.ArraySet
import androidx.media3.exoplayer.source.MediaSource
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediasource.FailedMediaSource
import org.schabi.newpipe.player.mediasource.FailedMediaSource.MediaSourceResolutionException
import org.schabi.newpipe.player.mediasource.FailedMediaSource.StreamInfoLoadException
import org.schabi.newpipe.player.mediasource.LoadedMediaSource
import org.schabi.newpipe.player.mediasource.ManagedMediaSource
import org.schabi.newpipe.player.mediasource.ManagedMediaSourcePlaylist
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueEvent
import org.schabi.newpipe.player.playqueue.PlayQueueEvent.*
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.ServiceHelper.getCacheExpirationMillis

class MediaSourceManager @JvmOverloads constructor(
    private val playbackListener: PlaybackListener,
    private val playQueue: PlayQueue,
    private val loadDebounceMillis: Long = 400L,
    private val playbackNearEndGapMillis: Long = TimeUnit.SECONDS.toMillis(30),
    private val progressUpdateIntervalMillis: Long = TimeUnit.SECONDS.toMillis(2)
) {
    private val tag = "MediaSourceManager@${Integer.toHexString(hashCode())}"

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val debouncedSignal = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val loadingItems = mutableSetOf<PlayQueueItem>()
    private val isBlocked = AtomicBoolean(false)
    private var playlist = ManagedMediaSourcePlaylist()
    private val removeMediaSourceHandler = Handler(Looper.getMainLooper())

    private val loaderJobs = mutableMapOf<PlayQueueItem, Job>()

    init {
        requireNotNull(playQueue.broadcastReceiver) { "Play Queue has not been initialized." }
        require(playbackNearEndGapMillis >= progressUpdateIntervalMillis) {
            "Playback end gap=[$playbackNearEndGapMillis ms] must be longer than update interval=[$progressUpdateIntervalMillis ms] for them to be useful."
        }

        managerScope.launch {
            playQueue.broadcastReceiver?.collect { event ->
                onPlayQueueChanged(event)
            }
        }

        managerScope.launch {
            val nearEndIntervalSignal = flow {
                while (true) {
                    delay(progressUpdateIntervalMillis)
                    if (playbackListener.isApproachingPlaybackEdge(playbackNearEndGapMillis)) {
                        emit(System.currentTimeMillis())
                    }
                }
            }

            @OptIn(FlowPreview::class)
            merge(debouncedSignal, nearEndIntervalSignal)
                .debounce(loadDebounceMillis)
                .collect {
                    loadImmediate()
                }
        }
    }

    fun dispose() {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "dispose() called.")
        }
        managerScope.cancel()
        removeMediaSourceHandler.removeCallbacksAndMessages(null)
    }

    private fun onPlayQueueChanged(event: PlayQueueEvent) {
        if (playQueue.isEmpty && playQueue.isComplete()) {
            playbackListener.onPlaybackShutdown()
            return
        }

        // Event specific action
        when (event.type()) {
            Type.INIT, Type.ERROR -> maybeBlock()

            Type.APPEND -> populateSources()

            Type.SELECT -> maybeRenewCurrentIndex()

            Type.REMOVE -> {
                val removeEvent = event as RemoveEvent
                playlist.remove(removeEvent.removeIndex)
            }

            Type.MOVE -> {
                val moveEvent = event as MoveEvent
                playlist.move(moveEvent.fromIndex, moveEvent.toIndex)
            }

            Type.REORDER -> {
                val reorderEvent = event as ReorderEvent
                playlist.move(reorderEvent.fromSelectedIndex, reorderEvent.toSelectedIndex)
            }

            else -> {}
        }

        // Loading and Syncing
        when (event.type()) {
            Type.INIT, Type.REORDER, Type.ERROR, Type.SELECT -> loadImmediate()
            else -> loadDebounced()
        }

        // update ui and notification
        when (event.type()) {
            Type.APPEND, Type.REMOVE, Type.MOVE, Type.REORDER -> playbackListener.onPlayQueueEdited()
            else -> {}
        }

        if (!isPlayQueueReady()) {
            maybeBlock()
            playQueue.fetch()
        }
    }

    private fun isPlayQueueReady(): Boolean {
        val isWindowLoaded = playQueue.size() - playQueue.index > WINDOW_SIZE
        return playQueue.isComplete() || isWindowLoaded
    }

    private fun isPlaybackReady(): Boolean {
        if (playlist.size() != playQueue.size()) {
            return false
        }

        val mediaSource = playlist.get(playQueue.index)
        val playQueueItem = playQueue.item
        if (mediaSource == null || playQueueItem == null) {
            return false
        }

        return mediaSource.isStreamEqual(playQueueItem)
    }

    private fun maybeBlock() {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "maybeBlock() called.")
        }

        if (isBlocked.get()) {
            return
        }

        playbackListener.onPlaybackBlock()
        resetSources()
        isBlocked.set(true)
    }

    private fun maybeUnblock(): Boolean {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "maybeUnblock() called.")
        }

        if (isBlocked.get()) {
            isBlocked.set(false)
            playbackListener.onPlaybackUnblock(playlist.parentMediaSource)
            return true
        }

        return false
    }

    private fun maybeSync(wasBlocked: Boolean) {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "maybeSync() called.")
        }

        val currentItem = playQueue.item
        if (isBlocked.get() || currentItem == null) {
            return
        }

        playbackListener.onPlaybackSynchronize(currentItem, wasBlocked)
    }

    @Synchronized
    private fun maybeSynchronizePlayer() {
        if (isPlayQueueReady() && isPlaybackReady()) {
            val isBlockReleased = maybeUnblock()
            maybeSync(isBlockReleased)
        }
    }

    private fun loadDebounced() {
        debouncedSignal.tryEmit(System.currentTimeMillis())
    }

    private fun loadImmediate() {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "MediaSource - loadImmediate() called")
        }
        val itemsToLoad = getItemsToLoad(playQueue) ?: return

        maybeClearLoaders()

        maybeLoadItem(itemsToLoad.center)
        for (item in itemsToLoad.neighbors) {
            maybeLoadItem(item)
        }
    }

    private fun maybeLoadItem(item: PlayQueueItem) {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "maybeLoadItem() called.")
        }
        if (playQueue.indexOf(item) >= playlist.size()) {
            return
        }

        if (!loadingItems.contains(item) && isCorrectionNeeded(item)) {
            if (PlayQueue.DEBUG) {
                Log.d(tag, "MediaSource - Loading=[${item.title}] with url=[${item.url}]")
            }

            loadingItems.add(item)
            val job = managerScope.launch {
                val mediaSource = getLoadedMediaSource(item)
                onMediaSourceReceived(item, mediaSource)
            }
            loaderJobs[item] = job
        }
    }

    private suspend fun getLoadedMediaSource(stream: PlayQueueItem): ManagedMediaSource {
        return try {
            val streamInfo = stream.getStream()
            val source = playbackListener.sourceOf(stream, streamInfo)
            if (source != null) {
                val tag = MediaItemTag.from(source.mediaItem)
                if (tag != null) {
                    val serviceId = streamInfo.serviceId
                    val expiration = System.currentTimeMillis() + getCacheExpirationMillis(serviceId)
                    LoadedMediaSource(source, tag, stream, expiration) as ManagedMediaSource
                } else {
                    createResolutionExceptionSource(stream, streamInfo)
                }
            } else {
                createResolutionExceptionSource(stream, streamInfo)
            }
        } catch (throwable: Throwable) {
            if (throwable is ExtractionException) {
                FailedMediaSource.of(stream, StreamInfoLoadException(throwable))
            } else {
                val allowRetryIn = TimeUnit.SECONDS.toMillis(3)
                FailedMediaSource.of(stream, Exception(throwable), allowRetryIn)
            }
        }
    }

    private fun createResolutionExceptionSource(stream: PlayQueueItem, streamInfo: org.schabi.newpipe.extractor.stream.StreamInfo): ManagedMediaSource {
        val message = "Unable to resolve source from stream info. " +
            "URL: ${stream.url}, audio count: ${streamInfo.audioStreams.size}, " +
            "video count: ${streamInfo.videoOnlyStreams.size}, ${streamInfo.videoStreams.size}"
        return FailedMediaSource.of(stream, MediaSourceResolutionException(message))
    }

    private fun onMediaSourceReceived(item: PlayQueueItem, mediaSource: ManagedMediaSource) {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "MediaSource - Loaded=[${item.title}] with url=[${item.url}]")
        }

        loadingItems.remove(item)
        loaderJobs.remove(item)

        val itemIndex = playQueue.indexOf(item)
        // Only update the playlist timeline for items at the current index or after.
        if (isCorrectionNeeded(item)) {
            if (PlayQueue.DEBUG) {
                Log.d(tag, "MediaSource - Updating index=[$itemIndex] with title=[${item.title}] at url=[${item.url}]")
            }
            playlist.update(itemIndex, mediaSource, removeMediaSourceHandler) {
                maybeSynchronizePlayer()
            }
        }
    }

    private fun isCorrectionNeeded(item: PlayQueueItem): Boolean {
        val index = playQueue.indexOf(item)
        val mediaSource = playlist.get(index)
        return mediaSource != null && mediaSource.shouldBeReplacedWith(
            item,
            index != playQueue.index
        )
    }

    private fun maybeRenewCurrentIndex() {
        val currentIndex = playQueue.index
        val currentItem = playQueue.item
        val currentSource = playlist.get(currentIndex)
        if (currentItem == null || currentSource == null) {
            return
        }

        if (!currentSource.shouldBeReplacedWith(currentItem, true)) {
            maybeSynchronizePlayer()
            return
        }

        if (PlayQueue.DEBUG) {
            Log.d(tag, "MediaSource - Reloading currently playing, index=[$currentIndex], item=[${currentItem.title}]")
        }
        playlist.invalidate(currentIndex, removeMediaSourceHandler) {
            loadImmediate()
        }
    }

    private fun maybeClearLoaders() {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "MediaSource - maybeClearLoaders() called.")
        }
        if (!loadingItems.contains(playQueue.item) && loaderJobs.size > MAXIMUM_LOADER_SIZE) {
            loaderJobs.values.forEach { it.cancel() }
            loaderJobs.clear()
            loadingItems.clear()
        }
    }

    private fun resetSources() {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "resetSources() called.")
        }
        playlist = ManagedMediaSourcePlaylist()
    }

    private fun populateSources() {
        if (PlayQueue.DEBUG) {
            Log.d(tag, "populateSources() called.")
        }
        while (playlist.size() < playQueue.size()) {
            playlist.expand()
        }
    }

    private class ItemsToLoad(
        val center: PlayQueueItem,
        val neighbors: Collection<PlayQueueItem>
    )

    companion object {
        private const val WINDOW_SIZE = 1
        private const val MAXIMUM_LOADER_SIZE = WINDOW_SIZE * 2 + 1

        private fun getItemsToLoad(playQueue: PlayQueue): ItemsToLoad? {
            val currentIndex = playQueue.index
            val currentItem = playQueue.getItem(currentIndex) ?: return null

            val leftBound = (currentIndex - WINDOW_SIZE).coerceAtLeast(0)
            val rightLimit = currentIndex + WINDOW_SIZE + 1
            val rightBound = playQueue.size().coerceAtMost(rightLimit)

            val neighbors = ArraySet<PlayQueueItem>()
            neighbors.addAll(playQueue.getStreams().subList(leftBound, rightBound))

            val excess = rightLimit - playQueue.size()
            if (excess >= 0) {
                neighbors.addAll(playQueue.getStreams().subList(0, playQueue.size().coerceAtMost(excess)))
            }
            neighbors.remove(currentItem)

            return ItemsToLoad(currentItem, neighbors)
        }
    }
}
