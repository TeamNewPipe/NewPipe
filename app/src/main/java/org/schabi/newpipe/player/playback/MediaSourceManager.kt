package org.schabi.newpipe.player.playback

import android.os.Handler
import android.util.Log
import androidx.collection.ArraySet
import com.google.android.exoplayer2.source.MediaSource
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.internal.subscriptions.EmptySubscription
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediasource.FailedMediaSource
import org.schabi.newpipe.player.mediasource.FailedMediaSource.MediaSourceResolutionException
import org.schabi.newpipe.player.mediasource.FailedMediaSource.StreamInfoLoadException
import org.schabi.newpipe.player.mediasource.LoadedMediaSource
import org.schabi.newpipe.player.mediasource.ManagedMediaSource
import org.schabi.newpipe.player.mediasource.ManagedMediaSourcePlaylist
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.events.MoveEvent
import org.schabi.newpipe.player.playqueue.events.PlayQueueEvent
import org.schabi.newpipe.player.playqueue.events.PlayQueueEventType
import org.schabi.newpipe.player.playqueue.events.RemoveEvent
import org.schabi.newpipe.player.playqueue.events.ReorderEvent
import org.schabi.newpipe.util.ServiceHelper
import java.util.Collections
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

class MediaSourceManager private constructor(listener: PlaybackListener,
                                             playQueue: PlayQueue,
                                             loadDebounceMillis: Long,
                                             playbackNearEndGapMillis: Long,
                                             progressUpdateIntervalMillis: Long) {
    private val TAG: String = "MediaSourceManager@" + hashCode()
    private val playbackListener: PlaybackListener
    private val playQueue: PlayQueue

    /**
     * Determines the gap time between the playback position and the playback duration which
     * the [.getEdgeIntervalSignal] begins to request loading.
     *
     * @see .progressUpdateIntervalMillis
     */
    private val playbackNearEndGapMillis: Long

    /**
     * Determines the interval which the [.getEdgeIntervalSignal] waits for between
     * each request for loading, once [.playbackNearEndGapMillis] has reached.
     */
    private val progressUpdateIntervalMillis: Long
    private val nearEndIntervalSignal: Observable<Long>

    /**
     * Process only the last load order when receiving a stream of load orders (lessens I/O).
     *
     *
     * The higher it is, the less loading occurs during rapid noncritical timeline changes.
     *
     *
     *
     * Not recommended to go below 100ms.
     *
     *
     * @see .loadDebounced
     */
    private val loadDebounceMillis: Long
    private val debouncedLoader: Disposable
    private val debouncedSignal: PublishSubject<Long>
    private var playQueueReactor: Subscription
    private val loaderReactor: CompositeDisposable
    private val loadingItems: MutableSet<PlayQueueItem?>
    private val isBlocked: AtomicBoolean
    private var playlist: ManagedMediaSourcePlaylist
    private val removeMediaSourceHandler: Handler = Handler()

    constructor(listener: PlaybackListener,
                playQueue: PlayQueue) : this(listener, playQueue, 400L,  /*playbackNearEndGapMillis=*/
            TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS),  /*progressUpdateIntervalMillis*/
            TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS))

    init {
        if (playQueue.getBroadcastReceiver() == null) {
            throw IllegalArgumentException("Play Queue has not been initialized.")
        }
        if (playbackNearEndGapMillis < progressUpdateIntervalMillis) {
            throw IllegalArgumentException(("Playback end gap=[" + playbackNearEndGapMillis
                    + " ms] must be longer than update interval=[ " + progressUpdateIntervalMillis
                    + " ms] for them to be useful."))
        }
        playbackListener = listener
        this.playQueue = playQueue
        this.playbackNearEndGapMillis = playbackNearEndGapMillis
        this.progressUpdateIntervalMillis = progressUpdateIntervalMillis
        nearEndIntervalSignal = edgeIntervalSignal
        this.loadDebounceMillis = loadDebounceMillis
        debouncedSignal = PublishSubject.create()
        debouncedLoader = getDebouncedLoader()
        playQueueReactor = EmptySubscription.INSTANCE
        loaderReactor = CompositeDisposable()
        isBlocked = AtomicBoolean(false)
        playlist = ManagedMediaSourcePlaylist()
        loadingItems = Collections.synchronizedSet(ArraySet())
        playQueue.getBroadcastReceiver()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(reactor)
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Exposed Methods
    ////////////////////////////////////////////////////////////////////////// */
    /**
     * Dispose the manager and releases all message buses and loaders.
     */
    fun dispose() {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "close() called.")
        }
        debouncedSignal.onComplete()
        debouncedLoader.dispose()
        playQueueReactor.cancel()
        loaderReactor.dispose()
    }

    private val reactor: Subscriber<PlayQueueEvent?>
        /*//////////////////////////////////////////////////////////////////////////
    // Event Reactor
    ////////////////////////////////////////////////////////////////////////// */private get() {
            return object : Subscriber<PlayQueueEvent> {
                public override fun onSubscribe(d: Subscription) {
                    playQueueReactor.cancel()
                    playQueueReactor = d
                    playQueueReactor.request(1)
                }

                public override fun onNext(playQueueMessage: PlayQueueEvent) {
                    onPlayQueueChanged(playQueueMessage)
                }

                public override fun onError(e: Throwable) {}
                public override fun onComplete() {}
            }
        }

    private fun onPlayQueueChanged(event: PlayQueueEvent) {
        if (playQueue.isEmpty() && playQueue.isComplete()) {
            playbackListener.onPlaybackShutdown()
            return
        }
        when (event.type()) {
            PlayQueueEventType.INIT, PlayQueueEventType.ERROR -> {
                maybeBlock()
                populateSources()
            }

            PlayQueueEventType.APPEND -> populateSources()
            PlayQueueEventType.SELECT -> maybeRenewCurrentIndex()
            PlayQueueEventType.REMOVE -> {
                val removeEvent: RemoveEvent = event as RemoveEvent
                playlist.remove(removeEvent.getRemoveIndex())
            }

            PlayQueueEventType.MOVE -> {
                val moveEvent: MoveEvent = event as MoveEvent
                playlist.move(moveEvent.getFromIndex(), moveEvent.getToIndex())
            }

            PlayQueueEventType.REORDER -> {
                // Need to move to ensure the playing index from play queue matches that of
                // the source timeline, and then window correction can take care of the rest
                val reorderEvent: ReorderEvent = event as ReorderEvent
                playlist.move(reorderEvent.getFromSelectedIndex(),
                        reorderEvent.getToSelectedIndex())
            }

            PlayQueueEventType.RECOVERY -> {}
            else -> {}
        }
        when (event.type()) {
            PlayQueueEventType.INIT, PlayQueueEventType.REORDER, PlayQueueEventType.ERROR, PlayQueueEventType.SELECT -> loadImmediate() // low frequency, critical events
            PlayQueueEventType.APPEND, PlayQueueEventType.REMOVE, PlayQueueEventType.MOVE, PlayQueueEventType.RECOVERY -> loadDebounced() // high frequency or noncritical events
            else -> loadDebounced()
        }
        when (event.type()) {
            PlayQueueEventType.APPEND, PlayQueueEventType.REMOVE, PlayQueueEventType.MOVE, PlayQueueEventType.REORDER -> playbackListener.onPlayQueueEdited()
        }
        if (!isPlayQueueReady) {
            maybeBlock()
            playQueue.fetch()
        }
        playQueueReactor.request(1)
    }

    private val isPlayQueueReady: Boolean
        /*//////////////////////////////////////////////////////////////////////////
    // Playback Locking
    ////////////////////////////////////////////////////////////////////////// */private get() {
            val isWindowLoaded: Boolean = playQueue.size() - playQueue.getIndex() > WINDOW_SIZE
            return playQueue.isComplete() || isWindowLoaded
        }
    private val isPlaybackReady: Boolean
        private get() {
            if (playlist.size() != playQueue.size()) {
                return false
            }
            val mediaSource: ManagedMediaSource? = playlist.get(playQueue.getIndex())
            val playQueueItem: PlayQueueItem? = playQueue.getItem()
            if (mediaSource == null || playQueueItem == null) {
                return false
            }
            return mediaSource.isStreamEqual(playQueueItem)
        }

    private fun maybeBlock() {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "maybeBlock() called.")
        }
        if (isBlocked.get()) {
            return
        }
        playbackListener.onPlaybackBlock()
        resetSources()
        isBlocked.set(true)
    }

    private fun maybeUnblock(): Boolean {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "maybeUnblock() called.")
        }
        if (isBlocked.get()) {
            isBlocked.set(false)
            playbackListener.onPlaybackUnblock(playlist.getParentMediaSource())
            return true
        }
        return false
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Metadata Synchronization
    ////////////////////////////////////////////////////////////////////////// */
    private fun maybeSync(wasBlocked: Boolean) {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "maybeSync() called.")
        }
        val currentItem: PlayQueueItem? = playQueue.getItem()
        if (isBlocked.get() || currentItem == null) {
            return
        }
        playbackListener.onPlaybackSynchronize(currentItem, wasBlocked)
    }

    @Synchronized
    private fun maybeSynchronizePlayer() {
        if (isPlayQueueReady && isPlaybackReady) {
            val isBlockReleased: Boolean = maybeUnblock()
            maybeSync(isBlockReleased)
        }
    }

    private val edgeIntervalSignal: Observable<Long>
        /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Loading
    ////////////////////////////////////////////////////////////////////////// */private get() {
            return Observable.interval(progressUpdateIntervalMillis,
                    TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    .filter(Predicate({ ignored: Long? -> playbackListener.isApproachingPlaybackEdge(playbackNearEndGapMillis) }))
        }

    private fun getDebouncedLoader(): Disposable {
        return debouncedSignal.mergeWith(nearEndIntervalSignal)
                .debounce(loadDebounceMillis, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ timestamp: Long? -> loadImmediate() }))
    }

    private fun loadDebounced() {
        debouncedSignal.onNext(System.currentTimeMillis())
    }

    private fun loadImmediate() {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "MediaSource - loadImmediate() called")
        }
        val itemsToLoad: ItemsToLoad? = getItemsToLoad(playQueue)
        if (itemsToLoad == null) {
            return
        }

        // Evict the previous items being loaded to free up memory, before start loading new ones
        maybeClearLoaders()
        maybeLoadItem(itemsToLoad.center)
        for (item: PlayQueueItem in itemsToLoad.neighbors) {
            maybeLoadItem(item)
        }
    }

    private fun maybeLoadItem(item: PlayQueueItem) {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "maybeLoadItem() called.")
        }
        if (playQueue.indexOf(item) >= playlist.size()) {
            return
        }
        if (!loadingItems.contains(item) && isCorrectionNeeded(item)) {
            if (PlayQueue.Companion.DEBUG) {
                Log.d(TAG, ("MediaSource - Loading=[" + item.getTitle() + "] "
                        + "with url=[" + item.getUrl() + "]"))
            }
            loadingItems.add(item)
            val loader: Disposable = getLoadedMediaSource(item)
                    .observeOn(AndroidSchedulers.mainThread()) /* No exception handling since getLoadedMediaSource guarantees nonnull return */
                    .subscribe(Consumer({ mediaSource: ManagedMediaSource -> onMediaSourceReceived(item, mediaSource) }))
            loaderReactor.add(loader)
        }
    }

    private fun getLoadedMediaSource(stream: PlayQueueItem): Single<ManagedMediaSource> {
        return stream.getStream()
                .map<ManagedMediaSource>(io.reactivex.rxjava3.functions.Function<StreamInfo, ManagedMediaSource>({ streamInfo: StreamInfo ->
                    Optional
                            .ofNullable<MediaSource>(playbackListener.sourceOf(stream, streamInfo))
                            .flatMap<ManagedMediaSource>(java.util.function.Function<MediaSource, Optional<out ManagedMediaSource>>({ source: MediaSource ->
                                MediaItemTag.Companion.from(source.getMediaItem())
                                        .map<LoadedMediaSource>(java.util.function.Function<MediaItemTag, LoadedMediaSource>({ tag: MediaItemTag ->
                                            val serviceId: Int = streamInfo.getServiceId()
                                            val expiration: Long = (System.currentTimeMillis()
                                                    + ServiceHelper.getCacheExpirationMillis(serviceId))
                                            LoadedMediaSource(source, tag, stream,
                                                    expiration)
                                        }))
                            })
                            )
                            .orElseGet(Supplier<ManagedMediaSource>({
                                val message: String = ("Unable to resolve source from stream info. "
                                        + "URL: " + stream.getUrl()
                                        + ", audio count: " + streamInfo.getAudioStreams().size
                                        + ", video count: " + streamInfo.getVideoOnlyStreams().size
                                        + ", " + streamInfo.getVideoStreams().size)
                                FailedMediaSource.Companion.of(stream,
                                        MediaSourceResolutionException(message))
                            }))
                })
                )
                .onErrorReturn(io.reactivex.rxjava3.functions.Function<Throwable, ManagedMediaSource>({ throwable: Throwable? ->
                    if (throwable is ExtractionException) {
                        return@onErrorReturn FailedMediaSource.Companion.of(stream, StreamInfoLoadException(throwable))
                    }
                    // Non-source related error expected here (e.g. network),
                    // should allow retry shortly after the error.
                    val allowRetryIn: Long = TimeUnit.MILLISECONDS.convert(3,
                            TimeUnit.SECONDS)
                    FailedMediaSource.Companion.of(stream, Exception(throwable), allowRetryIn)
                }))
    }

    private fun onMediaSourceReceived(item: PlayQueueItem,
                                      mediaSource: ManagedMediaSource) {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, ("MediaSource - Loaded=[" + item.getTitle()
                    + "] with url=[" + item.getUrl() + "]"))
        }
        loadingItems.remove(item)
        val itemIndex: Int = playQueue.indexOf(item)
        // Only update the playlist timeline for items at the current index or after.
        if (isCorrectionNeeded(item)) {
            if (PlayQueue.Companion.DEBUG) {
                Log.d(TAG, ("MediaSource - Updating index=[" + itemIndex + "] with "
                        + "title=[" + item.getTitle() + "] at url=[" + item.getUrl() + "]"))
            }
            playlist.update(itemIndex, mediaSource, removeMediaSourceHandler, Runnable({ maybeSynchronizePlayer() }))
        }
    }

    /**
     * Checks if the corresponding MediaSource in
     * [com.google.android.exoplayer2.source.ConcatenatingMediaSource]
     * for a given [PlayQueueItem] needs replacement, either due to gapless playback
     * readiness or playlist desynchronization.
     *
     *
     * If the given [PlayQueueItem] is currently being played and is already loaded,
     * then correction is not only needed if the playlist is desynchronized. Otherwise, the
     * check depends on the status (e.g. expiration or placeholder) of the
     * [ManagedMediaSource].
     *
     *
     * @param item [PlayQueueItem] to check
     * @return whether a correction is needed
     */
    private fun isCorrectionNeeded(item: PlayQueueItem): Boolean {
        val index: Int = playQueue.indexOf(item)
        val mediaSource: ManagedMediaSource? = playlist.get(index)
        return mediaSource != null && mediaSource.shouldBeReplacedWith(item,
                index != playQueue.getIndex())
    }

    /**
     * Checks if the current playing index contains an expired [ManagedMediaSource].
     * If so, the expired source is replaced by a dummy [ManagedMediaSource] and
     * [.loadImmediate] is called to reload the current item.
     * <br></br><br></br>
     * If not, then the media source at the current index is ready for playback, and
     * [.maybeSynchronizePlayer] is called.
     * <br></br><br></br>
     * Under both cases, [.maybeSync] will be called to ensure the listener
     * is up-to-date.
     */
    private fun maybeRenewCurrentIndex() {
        val currentIndex: Int = playQueue.getIndex()
        val currentItem: PlayQueueItem? = playQueue.getItem()
        val currentSource: ManagedMediaSource? = playlist.get(currentIndex)
        if (currentItem == null || currentSource == null) {
            return
        }
        if (!currentSource.shouldBeReplacedWith(currentItem, true)) {
            maybeSynchronizePlayer()
            return
        }
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, ("MediaSource - Reloading currently playing, "
                    + "index=[" + currentIndex + "], item=[" + currentItem.getTitle() + "]"))
        }
        playlist.invalidate(currentIndex, removeMediaSourceHandler, Runnable({ loadImmediate() }))
    }

    private fun maybeClearLoaders() {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "MediaSource - maybeClearLoaders() called.")
        }
        if ((!loadingItems.contains(playQueue.getItem())
                        && loaderReactor.size() > MAXIMUM_LOADER_SIZE)) {
            loaderReactor.clear()
            loadingItems.clear()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Playlist Helpers
    ////////////////////////////////////////////////////////////////////////// */
    private fun resetSources() {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "resetSources() called.")
        }
        playlist = ManagedMediaSourcePlaylist()
    }

    private fun populateSources() {
        if (PlayQueue.Companion.DEBUG) {
            Log.d(TAG, "populateSources() called.")
        }
        while (playlist.size() < playQueue.size()) {
            playlist.expand()
        }
    }

    private class ItemsToLoad internal constructor(val center: PlayQueueItem,
                                                   val neighbors: Collection<PlayQueueItem>)

    companion object {
        /**
         * Determines how many streams before and after the current stream should be loaded.
         * The default value (1) ensures seamless playback under typical network settings.
         *
         *
         * The streams after the current will be loaded into the playlist timeline while the
         * streams before will only be cached for future usage.
         *
         *
         * @see .onMediaSourceReceived
         */
        private val WINDOW_SIZE: Int = 1

        /**
         * Determines the maximum number of disposables allowed in the [.loaderReactor].
         * Once exceeded, new calls to [.loadImmediate] will evict all disposables in the
         * [.loaderReactor] in order to load a new set of items.
         *
         * @see .loadImmediate
         * @see .maybeLoadItem
         */
        private val MAXIMUM_LOADER_SIZE: Int = WINDOW_SIZE * 2 + 1

        /*//////////////////////////////////////////////////////////////////////////
    // Manager Helpers
    ////////////////////////////////////////////////////////////////////////// */
        private fun getItemsToLoad(playQueue: PlayQueue): ItemsToLoad? {
            // The current item has higher priority
            val currentIndex: Int = playQueue.getIndex()
            val currentItem: PlayQueueItem? = playQueue.getItem(currentIndex)
            if (currentItem == null) {
                return null
            }

            // The rest are just for seamless playback
            // Although timeline is not updated prior to the current index, these sources are still
            // loaded into the cache for faster retrieval at a potentially later time.
            val leftBound: Int = max(0.0, (currentIndex - WINDOW_SIZE).toDouble()).toInt()
            val rightLimit: Int = currentIndex + WINDOW_SIZE + 1
            val rightBound: Int = min(playQueue.size().toDouble(), rightLimit.toDouble()).toInt()
            val neighbors: MutableSet<PlayQueueItem?> = ArraySet(
                    playQueue.getStreams().subList(leftBound, rightBound))

            // Do a round robin
            val excess: Int = rightLimit - playQueue.size()
            if (excess >= 0) {
                neighbors.addAll(playQueue.getStreams()
                        .subList(0, min(playQueue.size().toDouble(), excess.toDouble()).toInt()))
            }
            neighbors.remove(currentItem)
            return ItemsToLoad(currentItem, neighbors)
        }
    }
}
