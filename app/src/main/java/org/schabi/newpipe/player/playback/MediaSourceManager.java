package org.schabi.newpipe.player.playback;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.mediasource.FailedMediaSource;
import org.schabi.newpipe.player.mediasource.LoadedMediaSource;
import org.schabi.newpipe.player.mediasource.ManagedMediaSource;
import org.schabi.newpipe.player.mediasource.PlaceholderMediaSource;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.events.MoveEvent;
import org.schabi.newpipe.playlist.events.PlayQueueEvent;
import org.schabi.newpipe.playlist.events.RemoveEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.SerialDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.subscriptions.EmptySubscription;
import io.reactivex.subjects.PublishSubject;

import static org.schabi.newpipe.playlist.PlayQueue.DEBUG;

public class MediaSourceManager {
    @NonNull private final static String TAG = "MediaSourceManager";

    /**
     * Determines how many streams before and after the current stream should be loaded.
     * The default value (1) ensures seamless playback under typical network settings.
     * <br><br>
     * The streams after the current will be loaded into the playlist timeline while the
     * streams before will only be cached for future usage.
     *
     * @see #onMediaSourceReceived(PlayQueueItem, ManagedMediaSource)
     * @see #update(int, MediaSource)
     * */
    private final static int WINDOW_SIZE = 1;

    @NonNull private final PlaybackListener playbackListener;
    @NonNull private final PlayQueue playQueue;

    /**
     * Determines how long NEIGHBOURING {@link LoadedMediaSource} window of a currently playing
     * {@link MediaSource} is allowed to stay in the playlist timeline. This is to ensure
     * the {@link StreamInfo} used in subsequent playback is up-to-date.
     * <br><br>
     * Once a {@link LoadedMediaSource} has expired, a new source will be reloaded to
     * replace the expired one on whereupon {@link #loadImmediate()} is called.
     *
     * @see #loadImmediate()
     * @see #isCorrectionNeeded(PlayQueueItem)
     * */
    private final long windowRefreshTimeMillis;

    /**
     * Process only the last load order when receiving a stream of load orders (lessens I/O).
     * <br><br>
     * The higher it is, the less loading occurs during rapid noncritical timeline changes.
     * <br><br>
     * Not recommended to go below 100ms.
     *
     * @see #loadDebounced()
     * */
    private final long loadDebounceMillis;
    @NonNull private final Disposable debouncedLoader;
    @NonNull private final PublishSubject<Long> debouncedSignal;

    @NonNull private Subscription playQueueReactor;

    /**
     * Determines the maximum number of disposables allowed in the {@link #loaderReactor}.
     * Once exceeded, new calls to {@link #loadImmediate()} will evict all disposables in the
     * {@link #loaderReactor} in order to load a new set of items.
     *
     * @see #loadImmediate()
     * @see #maybeLoadItem(PlayQueueItem)
     * */
    private final static int MAXIMUM_LOADER_SIZE = WINDOW_SIZE * 2 + 1;
    @NonNull private final CompositeDisposable loaderReactor;
    @NonNull private Set<PlayQueueItem> loadingItems;
    @NonNull private final SerialDisposable syncReactor;

    @NonNull private final AtomicBoolean isBlocked;

    @NonNull private DynamicConcatenatingMediaSource sources;

    @Nullable private PlayQueueItem syncedItem;

    public MediaSourceManager(@NonNull final PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this(listener, playQueue,
                /*loadDebounceMillis=*/400L,
                /*windowRefreshTimeMillis=*/TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES));
    }

    private MediaSourceManager(@NonNull final PlaybackListener listener,
                               @NonNull final PlayQueue playQueue,
                               final long loadDebounceMillis,
                               final long windowRefreshTimeMillis) {
        if (playQueue.getBroadcastReceiver() == null) {
            throw new IllegalArgumentException("Play Queue has not been initialized.");
        }

        this.playbackListener = listener;
        this.playQueue = playQueue;

        this.windowRefreshTimeMillis = windowRefreshTimeMillis;

        this.loadDebounceMillis = loadDebounceMillis;
        this.debouncedSignal = PublishSubject.create();
        this.debouncedLoader = getDebouncedLoader();

        this.playQueueReactor = EmptySubscription.INSTANCE;
        this.loaderReactor = new CompositeDisposable();
        this.syncReactor = new SerialDisposable();

        this.isBlocked = new AtomicBoolean(false);

        this.sources = new DynamicConcatenatingMediaSource();

        this.loadingItems = Collections.synchronizedSet(new HashSet<>());

        playQueue.getBroadcastReceiver()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getReactor());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Exposed Methods
    //////////////////////////////////////////////////////////////////////////*/
    /**
     * Dispose the manager and releases all message buses and loaders.
     * */
    public void dispose() {
        if (DEBUG) Log.d(TAG, "dispose() called.");

        debouncedSignal.onComplete();
        debouncedLoader.dispose();

        playQueueReactor.cancel();
        loaderReactor.dispose();
        syncReactor.dispose();
        sources.releaseSource();

        syncedItem = null;
    }

    /**
     * Loads the current playing stream and the streams within its windowSize bound.
     *
     * Unblocks the player once the item at the current index is loaded.
     * */
    public void load() {
        if (DEBUG) Log.d(TAG, "load() called.");
        loadDebounced();
    }

    /**
     * Blocks the player and repopulate the sources.
     *
     * Does not ensure the player is unblocked and should be done explicitly
     * through {@link #load() load}.
     * */
    public void reset() {
        if (DEBUG) Log.d(TAG, "reset() called.");

        tryBlock();

        syncedItem = null;
        populateSources();
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Event Reactor
    //////////////////////////////////////////////////////////////////////////*/

    private Subscriber<PlayQueueEvent> getReactor() {
        return new Subscriber<PlayQueueEvent>() {
            @Override
            public void onSubscribe(@NonNull Subscription d) {
                playQueueReactor.cancel();
                playQueueReactor = d;
                playQueueReactor.request(1);
            }

            @Override
            public void onNext(@NonNull PlayQueueEvent playQueueMessage) {
                onPlayQueueChanged(playQueueMessage);
            }

            @Override
            public void onError(@NonNull Throwable e) {}

            @Override
            public void onComplete() {}
        };
    }

    private void onPlayQueueChanged(final PlayQueueEvent event) {
        if (playQueue.isEmpty() && playQueue.isComplete()) {
            playbackListener.shutdown();
            return;
        }

        // Event specific action
        switch (event.type()) {
            case INIT:
            case REORDER:
            case ERROR:
                reset();
                break;
            case APPEND:
                populateSources();
                break;
            case REMOVE:
                final RemoveEvent removeEvent = (RemoveEvent) event;
                remove(removeEvent.getRemoveIndex());
                break;
            case MOVE:
                final MoveEvent moveEvent = (MoveEvent) event;
                move(moveEvent.getFromIndex(), moveEvent.getToIndex());
                break;
            case SELECT:
            case RECOVERY:
            default:
                break;
        }

        // Loading and Syncing
        switch (event.type()) {
            case INIT:
            case REORDER:
            case ERROR:
            case SELECT:
                loadImmediate(); // low frequency, critical events
                break;
            case APPEND:
            case REMOVE:
            case MOVE:
            case RECOVERY:
            default:
                loadDebounced(); // high frequency or noncritical events
                break;
        }

        if (!isPlayQueueReady()) {
            tryBlock();
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
        if (sources.getSize() != playQueue.size()) return false;

        final MediaSource mediaSource = sources.getMediaSource(playQueue.getIndex());
        final PlayQueueItem playQueueItem = playQueue.getItem();

        if (mediaSource instanceof LoadedMediaSource) {
            return playQueueItem == ((LoadedMediaSource) mediaSource).getStream();
        } else if (mediaSource instanceof FailedMediaSource) {
            return playQueueItem == ((FailedMediaSource) mediaSource).getStream();
        }
        return false;
    }

    private void tryBlock() {
        if (DEBUG) Log.d(TAG, "tryBlock() called.");

        if (isBlocked.get()) return;

        playbackListener.block();
        resetSources();

        isBlocked.set(true);
    }

    private void tryUnblock() {
        if (DEBUG) Log.d(TAG, "tryUnblock() called.");

        if (isPlayQueueReady() && isPlaybackReady() && isBlocked.get()) {
            isBlocked.set(false);
            playbackListener.unblock(sources);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Metadata Synchronization TODO: maybe this should be a separate manager
    //////////////////////////////////////////////////////////////////////////*/

    private void sync() {
        if (DEBUG) Log.d(TAG, "sync() called.");

        final PlayQueueItem currentItem = playQueue.getItem();
        if (isBlocked.get() || currentItem == null) return;

        final Consumer<StreamInfo> onSuccess = info -> syncInternal(currentItem, info);
        final Consumer<Throwable> onError = throwable -> syncInternal(currentItem, null);

        if (syncedItem != currentItem) {
            syncedItem = currentItem;
            final Disposable sync = currentItem.getStream()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(onSuccess, onError);
            syncReactor.set(sync);
        }
    }

    private void syncInternal(@NonNull final PlayQueueItem item,
                              @Nullable final StreamInfo info) {
        // Ensure the current item is up to date with the play queue
        if (playQueue.getItem() == item && playQueue.getItem() == syncedItem) {
            playbackListener.sync(item, info);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Loading
    //////////////////////////////////////////////////////////////////////////*/

    private Disposable getDebouncedLoader() {
        return debouncedSignal
                .debounce(loadDebounceMillis, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timestamp -> loadImmediate());
    }

    private void loadDebounced() {
        debouncedSignal.onNext(System.currentTimeMillis());
    }

    private void loadImmediate() {
        // The current item has higher priority
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem(currentIndex);
        if (currentItem == null) return;

        // Evict the items being loaded to free up memory
        if (!loadingItems.contains(currentItem) && loaderReactor.size() > MAXIMUM_LOADER_SIZE) {
            loaderReactor.clear();
            loadingItems.clear();
        }
        maybeLoadItem(currentItem);

        // The rest are just for seamless playback
        // Although timeline is not updated prior to the current index, these sources are still
        // loaded into the cache for faster retrieval at a potentially later time.
        final int leftBound = Math.max(0, currentIndex - WINDOW_SIZE);
        final int rightLimit = currentIndex + WINDOW_SIZE + 1;
        final int rightBound = Math.min(playQueue.size(), rightLimit);
        final List<PlayQueueItem> items = new ArrayList<>(
                playQueue.getStreams().subList(leftBound,rightBound));

        // Do a round robin
        final int excess = rightLimit - playQueue.size();
        if (excess >= 0) {
            items.addAll(playQueue.getStreams().subList(0, Math.min(playQueue.size(), excess)));
        }

        for (final PlayQueueItem item : items) {
            maybeLoadItem(item);
        }
    }

    private void maybeLoadItem(@NonNull final PlayQueueItem item) {
        if (DEBUG) Log.d(TAG, "maybeLoadItem() called.");
        if (playQueue.indexOf(item) >= sources.getSize()) return;

        if (!loadingItems.contains(item) && isCorrectionNeeded(item)) {
            if (DEBUG) Log.d(TAG, "MediaSource - Loading: [" + item.getTitle() +
                    "] with url: " + item.getUrl());

            loadingItems.add(item);
            final Disposable loader = getLoadedMediaSource(item)
                    .observeOn(AndroidSchedulers.mainThread())
                    /* No exception handling since getLoadedMediaSource guarantees nonnull return */
                    .subscribe(mediaSource -> onMediaSourceReceived(item, mediaSource));
            loaderReactor.add(loader);
        }

        tryUnblock();
        sync();
    }

    private Single<ManagedMediaSource> getLoadedMediaSource(@NonNull final PlayQueueItem stream) {
        return stream.getStream().map(streamInfo -> {
            final MediaSource source = playbackListener.sourceOf(stream, streamInfo);
            if (source == null) {
                final Exception exception = new IllegalStateException(
                        "Unable to resolve source from stream info." +
                                " URL: " + stream.getUrl() +
                                ", audio count: " + streamInfo.audio_streams.size() +
                                ", video count: " + streamInfo.video_only_streams.size() +
                                streamInfo.video_streams.size());
                return new FailedMediaSource(stream, exception);
            }

            final long expiration = System.currentTimeMillis() + windowRefreshTimeMillis;
            return new LoadedMediaSource(source, stream, expiration);
        }).onErrorReturn(throwable -> new FailedMediaSource(stream, throwable));
    }

    private void onMediaSourceReceived(@NonNull final PlayQueueItem item,
                                       @NonNull final ManagedMediaSource mediaSource) {
        if (DEBUG) Log.d(TAG, "MediaSource - Loaded: [" + item.getTitle() +
                "] with url: " + item.getUrl());

        final int itemIndex = playQueue.indexOf(item);
        // Only update the playlist timeline for items at the current index or after.
        if (itemIndex >= playQueue.getIndex() && isCorrectionNeeded(item)) {
            if (DEBUG) Log.d(TAG, "MediaSource - Updating: [" + item.getTitle() +
                    "] with url: " + item.getUrl());
            update(itemIndex, mediaSource);
        }

        loadingItems.remove(item);
        tryUnblock();
        sync();
    }

    /**
     * Checks if the corresponding MediaSource in {@link DynamicConcatenatingMediaSource}
     * for a given {@link PlayQueueItem} needs replacement, either due to gapless playback
     * readiness or playlist desynchronization.
     * <br><br>
     * If the given {@link PlayQueueItem} is currently being played and is already loaded,
     * then correction is not only needed if the playlist is desynchronized. Otherwise, the
     * check depends on the status (e.g. expiration or placeholder) of the
     * {@link ManagedMediaSource}.
     * */
    private boolean isCorrectionNeeded(@NonNull final PlayQueueItem item) {
        final int index = playQueue.indexOf(item);
        if (index == -1 || index >= sources.getSize()) return false;

        final ManagedMediaSource mediaSource = (ManagedMediaSource) sources.getMediaSource(index);

        if (index == playQueue.getIndex() && mediaSource instanceof LoadedMediaSource) {
            return item != ((LoadedMediaSource) mediaSource).getStream();
        } else {
            return mediaSource.canReplace(item);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Playlist Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private void resetSources() {
        if (DEBUG) Log.d(TAG, "resetSources() called.");

        this.sources.releaseSource();
        this.sources = new DynamicConcatenatingMediaSource();
    }

    private void populateSources() {
        if (DEBUG) Log.d(TAG, "populateSources() called.");
        if (sources.getSize() >= playQueue.size()) return;

        for (int index = sources.getSize() - 1; index < playQueue.size(); index++) {
            emplace(index, new PlaceholderMediaSource());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Playlist Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Places a {@link MediaSource} into the {@link DynamicConcatenatingMediaSource}
     * with position in respect to the play queue only if no {@link MediaSource}
     * already exists at the given index.
     * */
    private synchronized void emplace(final int index, @NonNull final MediaSource source) {
        if (index < sources.getSize()) return;

        sources.addMediaSource(index, source);
    }

    /**
     * Removes a {@link MediaSource} from {@link DynamicConcatenatingMediaSource}
     * at the given index. If this index is out of bound, then the removal is ignored.
     * */
    private synchronized void remove(final int index) {
        if (index < 0 || index > sources.getSize()) return;

        sources.removeMediaSource(index);
    }

    /**
     * Moves a {@link MediaSource} in {@link DynamicConcatenatingMediaSource}
     * from the given source index to the target index. If either index is out of bound,
     * then the call is ignored.
     * */
    private synchronized void move(final int source, final int target) {
        if (source < 0 || target < 0) return;
        if (source >= sources.getSize() || target >= sources.getSize()) return;

        sources.moveMediaSource(source, target);
    }

    /**
     * Updates the {@link MediaSource} in {@link DynamicConcatenatingMediaSource}
     * at the given index with a given {@link MediaSource}. If the index is out of bound,
     * then the replacement is ignored.
     * <br><br>
     * Not recommended to use on indices LESS THAN the currently playing index, since
     * this will modify the playback timeline prior to the index and may cause desynchronization
     * on the playing item between {@link PlayQueue} and {@link DynamicConcatenatingMediaSource}.
     * */
    private synchronized void update(final int index, @NonNull final MediaSource source) {
        if (index < 0 || index >= sources.getSize()) return;

        sources.addMediaSource(index + 1, source, () ->
                sources.removeMediaSource(index));
    }
}
