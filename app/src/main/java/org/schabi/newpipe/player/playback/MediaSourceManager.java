package org.schabi.newpipe.player.playback;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediasource.FailedMediaSource;
import org.schabi.newpipe.player.mediasource.LoadedMediaSource;
import org.schabi.newpipe.player.mediasource.ManagedMediaSource;
import org.schabi.newpipe.player.mediasource.ManagedMediaSourcePlaylist;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.events.MoveEvent;
import org.schabi.newpipe.player.playqueue.events.PlayQueueEvent;
import org.schabi.newpipe.player.playqueue.events.RemoveEvent;
import org.schabi.newpipe.player.playqueue.events.ReorderEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.internal.subscriptions.EmptySubscription;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

import static org.schabi.newpipe.BuildConfig.DEBUG;
import static org.schabi.newpipe.player.mediasource.FailedMediaSource.MediaSourceResolutionException;
import static org.schabi.newpipe.player.mediasource.FailedMediaSource.StreamInfoLoadException;
import static org.schabi.newpipe.util.ServiceHelper.getCacheExpirationMillis;

public class MediaSourceManager {
    @NonNull
    private final String TAG = "MediaSourceManager@" + hashCode();

    /**
     * Determines how many streams before and after the current stream should be loaded.
     * The default value (1) ensures seamless playback under typical network settings.
     * <p>
     * The streams after the current will be loaded into the playlist timeline while the
     * streams before will only be cached for future usage.
     * </p>
     *
     * @see #onMediaSourceReceived(PlayQueueItem, ManagedMediaSource)
     */
    private static final int WINDOW_SIZE = 1;

    /**
     * Determines the maximum number of disposables allowed in the {@link #loaderReactor}.
     * Once exceeded, new calls to {@link #loadImmediate()} will evict all disposables in the
     * {@link #loaderReactor} in order to load a new set of items.
     *
     * @see #loadImmediate()
     * @see #maybeLoadItem(PlayQueueItem)
     */
    private static final int MAXIMUM_LOADER_SIZE = WINDOW_SIZE * 2 + 1;

    @NonNull
    private final PlaybackListener playbackListener;
    @NonNull
    private final PlayQueue playQueue;

    /**
     * Determines the gap time between the playback position and the playback duration which
     * the {@link #getEdgeIntervalSignal()} begins to request loading.
     *
     * @see #progressUpdateIntervalMillis
     */
    private final long playbackNearEndGapMillis;

    /**
     * Determines the interval which the {@link #getEdgeIntervalSignal()} waits for between
     * each request for loading, once {@link #playbackNearEndGapMillis} has reached.
     */
    private final long progressUpdateIntervalMillis;

    @NonNull
    private final Observable<Long> nearEndIntervalSignal;

    /**
     * Process only the last load order when receiving a stream of load orders (lessens I/O).
     * <p>
     * The higher it is, the less loading occurs during rapid noncritical timeline changes.
     * </p>
     * <p>
     * Not recommended to go below 100ms.
     * </p>
     *
     * @see #loadDebounced()
     */
    private final long loadDebounceMillis;

    @NonNull
    private final Disposable debouncedLoader;
    @NonNull
    private final PublishSubject<Long> debouncedSignal;

    @NonNull
    private Subscription playQueueReactor;

    @NonNull
    private final CompositeDisposable loaderReactor;
    @NonNull
    private final Set<PlayQueueItem> loadingItems;

    @NonNull
    private final AtomicBoolean isBlocked;

    @NonNull
    private ManagedMediaSourcePlaylist playlist;

    private final Handler removeMediaSourceHandler = new Handler();

    public MediaSourceManager(@NonNull final PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this(listener, playQueue, 400L,
                /*playbackNearEndGapMillis=*/TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS),
                /*progressUpdateIntervalMillis*/TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS));
    }

    private MediaSourceManager(@NonNull final PlaybackListener listener,
                               @NonNull final PlayQueue playQueue,
                               final long loadDebounceMillis,
                               final long playbackNearEndGapMillis,
                               final long progressUpdateIntervalMillis) {
        if (playQueue.getBroadcastReceiver() == null) {
            throw new IllegalArgumentException("Play Queue has not been initialized.");
        }
        if (playbackNearEndGapMillis < progressUpdateIntervalMillis) {
            throw new IllegalArgumentException("Playback end gap=[" + playbackNearEndGapMillis
                    + " ms] must be longer than update interval=[ " + progressUpdateIntervalMillis
                    + " ms] for them to be useful.");
        }

        this.playbackListener = listener;
        this.playQueue = playQueue;

        this.playbackNearEndGapMillis = playbackNearEndGapMillis;
        this.progressUpdateIntervalMillis = progressUpdateIntervalMillis;
        this.nearEndIntervalSignal = getEdgeIntervalSignal();

        this.loadDebounceMillis = loadDebounceMillis;
        this.debouncedSignal = PublishSubject.create();
        this.debouncedLoader = getDebouncedLoader();

        this.playQueueReactor = EmptySubscription.INSTANCE;
        this.loaderReactor = new CompositeDisposable();

        this.isBlocked = new AtomicBoolean(false);

        this.playlist = new ManagedMediaSourcePlaylist();

        this.loadingItems = Collections.synchronizedSet(new ArraySet<>());

        playQueue.getBroadcastReceiver()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getReactor());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Exposed Methods
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Dispose the manager and releases all message buses and loaders.
     */
    public void dispose() {
        if (DEBUG) {
            Log.d(TAG, "close() called.");
        }

        debouncedSignal.onComplete();
        debouncedLoader.dispose();

        playQueueReactor.cancel();
        loaderReactor.dispose();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Event Reactor
    //////////////////////////////////////////////////////////////////////////*/

    private Subscriber<PlayQueueEvent> getReactor() {
        return new Subscriber<>() {
            @Override
            public void onSubscribe(@NonNull final Subscription d) {
                playQueueReactor.cancel();
                playQueueReactor = d;
                playQueueReactor.request(1);
            }

            @Override
            public void onNext(@NonNull final PlayQueueEvent playQueueMessage) {
                onPlayQueueChanged(playQueueMessage);
            }

            @Override
            public void onError(@NonNull final Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        };
    }

    private void onPlayQueueChanged(final PlayQueueEvent event) {
        if (playQueue.isEmpty() && playQueue.isComplete()) {
            playbackListener.onPlaybackShutdown();
            return;
        }

        // Event specific action
        switch (event.type()) {
            case INIT:
            case ERROR:
                maybeBlock();
            case APPEND:
                populateSources();
                break;
            case SELECT:
                maybeRenewCurrentIndex();
                break;
            case REMOVE:
                final RemoveEvent removeEvent = (RemoveEvent) event;
                playlist.remove(removeEvent.getRemoveIndex());
                break;
            case MOVE:
                final MoveEvent moveEvent = (MoveEvent) event;
                playlist.move(moveEvent.getFromIndex(), moveEvent.getToIndex());
                break;
            case REORDER:
                // Need to move to ensure the playing index from play queue matches that of
                // the source timeline, and then window correction can take care of the rest
                final ReorderEvent reorderEvent = (ReorderEvent) event;
                playlist.move(reorderEvent.getFromSelectedIndex(),
                        reorderEvent.getToSelectedIndex());
                break;
            case RECOVERY:
            default:
                break;
        }

        // Loading and Syncing
        switch (event.type()) {
            case INIT: case REORDER: case ERROR: case SELECT:
                loadImmediate(); // low frequency, critical events
                break;
            case APPEND: case REMOVE: case MOVE: case RECOVERY:
            default:
                loadDebounced(); // high frequency or noncritical events
                break;
        }

        // update ui and notification
        switch (event.type()) {
            case APPEND: case REMOVE: case MOVE: case REORDER:
                playbackListener.onPlayQueueEdited();
        }

        if (!isPlayQueueReady()) {
            maybeBlock();
            playQueue.fetch();
        }
        playQueueReactor.request(1);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playback Locking
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isPlayQueueReady() {
        final boolean isWindowLoaded = playQueue.size() - playQueue.getIndex() > WINDOW_SIZE;
        return playQueue.isComplete() || isWindowLoaded;
    }

    private boolean isPlaybackReady() {
        if (playlist.size() != playQueue.size()) {
            return false;
        }

        final ManagedMediaSource mediaSource = playlist.get(playQueue.getIndex());
        final PlayQueueItem playQueueItem = playQueue.getItem();
        if (mediaSource == null || playQueueItem == null) {
            return false;
        }

        return mediaSource.isStreamEqual(playQueueItem);
    }

    private void maybeBlock() {
        if (DEBUG) {
            Log.d(TAG, "maybeBlock() called.");
        }

        if (isBlocked.get()) {
            return;
        }

        playbackListener.onPlaybackBlock();
        resetSources();

        isBlocked.set(true);
    }

    private boolean maybeUnblock() {
        if (DEBUG) {
            Log.d(TAG, "maybeUnblock() called.");
        }

        if (isBlocked.get()) {
            isBlocked.set(false);
            playbackListener.onPlaybackUnblock(playlist.getParentMediaSource());
            return true;
        }

        return false;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Metadata Synchronization
    //////////////////////////////////////////////////////////////////////////*/

    private void maybeSync(final boolean wasBlocked) {
        if (DEBUG) {
            Log.d(TAG, "maybeSync() called.");
        }

        final PlayQueueItem currentItem = playQueue.getItem();
        if (isBlocked.get() || currentItem == null) {
            return;
        }

        playbackListener.onPlaybackSynchronize(currentItem, wasBlocked);
    }

    private synchronized void maybeSynchronizePlayer() {
        if (isPlayQueueReady() && isPlaybackReady()) {
            final boolean isBlockReleased = maybeUnblock();
            maybeSync(isBlockReleased);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Loading
    //////////////////////////////////////////////////////////////////////////*/

    private Observable<Long> getEdgeIntervalSignal() {
        return Observable.interval(progressUpdateIntervalMillis,
                                   TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .filter(ignored ->
                        playbackListener.isApproachingPlaybackEdge(playbackNearEndGapMillis));
    }

    private Disposable getDebouncedLoader() {
        return debouncedSignal.mergeWith(nearEndIntervalSignal)
                .debounce(loadDebounceMillis, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timestamp -> loadImmediate());
    }

    private void loadDebounced() {
        debouncedSignal.onNext(System.currentTimeMillis());
    }

    private void loadImmediate() {
        if (DEBUG) {
            Log.d(TAG, "MediaSource - loadImmediate() called");
        }
        final ItemsToLoad itemsToLoad = getItemsToLoad(playQueue);
        if (itemsToLoad == null) {
            return;
        }

        // Evict the previous items being loaded to free up memory, before start loading new ones
        maybeClearLoaders();

        maybeLoadItem(itemsToLoad.center);
        for (final PlayQueueItem item : itemsToLoad.neighbors) {
            maybeLoadItem(item);
        }
    }

    private void maybeLoadItem(@NonNull final PlayQueueItem item) {
        if (DEBUG) {
            Log.d(TAG, "maybeLoadItem() called.");
        }
        if (playQueue.indexOf(item) >= playlist.size()) {
            return;
        }

        if (!loadingItems.contains(item) && isCorrectionNeeded(item)) {
            if (DEBUG) {
                Log.d(TAG, "MediaSource - Loading=[" + item.getTitle() + "] "
                        + "with url=[" + item.getUrl() + "]");
            }

            loadingItems.add(item);
            final Disposable loader = getLoadedMediaSource(item)
                    .observeOn(AndroidSchedulers.mainThread())
                    /* No exception handling since getLoadedMediaSource guarantees nonnull return */
                    .subscribe(mediaSource -> onMediaSourceReceived(item, mediaSource));
            loaderReactor.add(loader);
        }
    }

    private Single<ManagedMediaSource> getLoadedMediaSource(@NonNull final PlayQueueItem stream) {
        return stream.getStream()
                .map(streamInfo -> Optional
                        .ofNullable(playbackListener.sourceOf(stream, streamInfo))
                        .<ManagedMediaSource>flatMap(source ->
                                MediaItemTag.from(source.getMediaItem())
                                        .map(tag -> {
                                            final int serviceId = streamInfo.getServiceId();
                                            final long expiration = System.currentTimeMillis()
                                                    + getCacheExpirationMillis(serviceId);
                                            return new LoadedMediaSource(source, tag, stream,
                                                    expiration);
                                        })
                        )
                        .orElseGet(() -> {
                            final String message = "Unable to resolve source from stream info. "
                                    + "URL: " + stream.getUrl()
                                    + ", audio count: " + streamInfo.getAudioStreams().size()
                                    + ", video count: " + streamInfo.getVideoOnlyStreams().size()
                                    + ", " + streamInfo.getVideoStreams().size();
                            return FailedMediaSource.of(stream,
                                    new MediaSourceResolutionException(message));
                        })
                )
                .onErrorReturn(throwable -> {
                    if (throwable instanceof ExtractionException) {
                        return FailedMediaSource.of(stream, new StreamInfoLoadException(throwable));
                    }
                    // Non-source related error expected here (e.g. network),
                    // should allow retry shortly after the error.
                    final long allowRetryIn = TimeUnit.MILLISECONDS.convert(3,
                            TimeUnit.SECONDS);
                    return FailedMediaSource.of(stream, new Exception(throwable), allowRetryIn);
                });
    }

    private void onMediaSourceReceived(@NonNull final PlayQueueItem item,
                                       @NonNull final ManagedMediaSource mediaSource) {
        if (DEBUG) {
            Log.d(TAG, "MediaSource - Loaded=[" + item.getTitle()
                    + "] with url=[" + item.getUrl() + "]");
        }

        loadingItems.remove(item);

        final int itemIndex = playQueue.indexOf(item);
        // Only update the playlist timeline for items at the current index or after.
        if (isCorrectionNeeded(item)) {
            if (DEBUG) {
                Log.d(TAG, "MediaSource - Updating index=[" + itemIndex + "] with "
                        + "title=[" + item.getTitle() + "] at url=[" + item.getUrl() + "]");
            }
            playlist.update(itemIndex, mediaSource, removeMediaSourceHandler,
                    this::maybeSynchronizePlayer);
        }
    }

    /**
     * Checks if the corresponding MediaSource in
     * {@link com.google.android.exoplayer2.source.ConcatenatingMediaSource}
     * for a given {@link PlayQueueItem} needs replacement, either due to gapless playback
     * readiness or playlist desynchronization.
     * <p>
     * If the given {@link PlayQueueItem} is currently being played and is already loaded,
     * then correction is not only needed if the playlist is desynchronized. Otherwise, the
     * check depends on the status (e.g. expiration or placeholder) of the
     * {@link ManagedMediaSource}.
     * </p>
     *
     * @param item {@link PlayQueueItem} to check
     * @return whether a correction is needed
     */
    private boolean isCorrectionNeeded(@NonNull final PlayQueueItem item) {
        final int index = playQueue.indexOf(item);
        final ManagedMediaSource mediaSource = playlist.get(index);
        return mediaSource != null && mediaSource.shouldBeReplacedWith(item,
                index != playQueue.getIndex());
    }

    /**
     * Checks if the current playing index contains an expired {@link ManagedMediaSource}.
     * If so, the expired source is replaced by a dummy {@link ManagedMediaSource} and
     * {@link #loadImmediate()} is called to reload the current item.
     * <br><br>
     * If not, then the media source at the current index is ready for playback, and
     * {@link #maybeSynchronizePlayer()} is called.
     * <br><br>
     * Under both cases, {@link #maybeSync(boolean)} will be called to ensure the listener
     * is up-to-date.
     */
    private void maybeRenewCurrentIndex() {
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem();
        final ManagedMediaSource currentSource = playlist.get(currentIndex);
        if (currentItem == null || currentSource == null) {
            return;
        }

        if (!currentSource.shouldBeReplacedWith(currentItem, true)) {
            maybeSynchronizePlayer();
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "MediaSource - Reloading currently playing, "
                    + "index=[" + currentIndex + "], item=[" + currentItem.getTitle() + "]");
        }
        playlist.invalidate(currentIndex, removeMediaSourceHandler, this::loadImmediate);
    }

    private void maybeClearLoaders() {
        if (DEBUG) {
            Log.d(TAG, "MediaSource - maybeClearLoaders() called.");
        }
        if (!loadingItems.contains(playQueue.getItem())
                && loaderReactor.size() > MAXIMUM_LOADER_SIZE) {
            loaderReactor.clear();
            loadingItems.clear();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Playlist Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private void resetSources() {
        if (DEBUG) {
            Log.d(TAG, "resetSources() called.");
        }
        playlist = new ManagedMediaSourcePlaylist();
    }

    private void populateSources() {
        if (DEBUG) {
            Log.d(TAG, "populateSources() called.");
        }
        while (playlist.size() < playQueue.size()) {
            playlist.expand();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Manager Helpers
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    private static ItemsToLoad getItemsToLoad(@NonNull final PlayQueue playQueue) {
        // The current item has higher priority
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem(currentIndex);
        if (currentItem == null) {
            return null;
        }

        // The rest are just for seamless playback
        // Although timeline is not updated prior to the current index, these sources are still
        // loaded into the cache for faster retrieval at a potentially later time.
        final int leftBound = Math.max(0, currentIndex - MediaSourceManager.WINDOW_SIZE);
        final int rightLimit = currentIndex + MediaSourceManager.WINDOW_SIZE + 1;
        final int rightBound = Math.min(playQueue.size(), rightLimit);
        final Set<PlayQueueItem> neighbors = new ArraySet<>(
                playQueue.getStreams().subList(leftBound, rightBound));

        // Do a round robin
        final int excess = rightLimit - playQueue.size();
        if (excess >= 0) {
            neighbors.addAll(playQueue.getStreams()
                    .subList(0, Math.min(playQueue.size(), excess)));
        }
        neighbors.remove(currentItem);

        return new ItemsToLoad(currentItem, neighbors);
    }

    private static class ItemsToLoad {
        @NonNull
        private final PlayQueueItem center;
        @NonNull
        private final Collection<PlayQueueItem> neighbors;

        ItemsToLoad(@NonNull final PlayQueueItem center,
                    @NonNull final Collection<PlayQueueItem> neighbors) {
            this.center = center;
            this.neighbors = neighbors;
        }
    }
}
