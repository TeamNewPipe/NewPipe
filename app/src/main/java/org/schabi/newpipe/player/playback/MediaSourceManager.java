package org.schabi.newpipe.player.playback;

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

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.SerialDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;

import static org.schabi.newpipe.playlist.PlayQueue.DEBUG;

public class MediaSourceManager {
    private final static String TAG = "MediaSourceManager";

    // WINDOW_SIZE determines how many streams AFTER the current stream should be loaded.
    // The default value (1) ensures seamless playback under typical network settings.
    private final static int WINDOW_SIZE = 1;

    private final PlaybackListener playbackListener;
    private final PlayQueue playQueue;
    private final long expirationTimeMillis;
    private final TimeUnit expirationTimeUnit;

    // Process only the last load order when receiving a stream of load orders (lessens I/O)
    // The higher it is, the less loading occurs during rapid noncritical timeline changes
    // Not recommended to go below 100ms
    private final long loadDebounceMillis;
    private final PublishSubject<Long> debouncedLoadSignal;
    private final Disposable debouncedLoader;

    private DynamicConcatenatingMediaSource sources;

    private Subscription playQueueReactor;
    private CompositeDisposable loaderReactor;

    private boolean isBlocked;

    private SerialDisposable syncReactor;
    private PlayQueueItem syncedItem;
    private Set<PlayQueueItem> loadingItems;

    public MediaSourceManager(@NonNull final PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this(listener, playQueue,
                /*loadDebounceMillis=*/400L,
                /*expirationTimeMillis=*/2,
                /*expirationTimeUnit=*/TimeUnit.HOURS);
    }

    private MediaSourceManager(@NonNull final PlaybackListener listener,
                               @NonNull final PlayQueue playQueue,
                               final long loadDebounceMillis,
                               final long expirationTimeMillis,
                               @NonNull final TimeUnit expirationTimeUnit) {
        this.playbackListener = listener;
        this.playQueue = playQueue;
        this.loadDebounceMillis = loadDebounceMillis;
        this.expirationTimeMillis = expirationTimeMillis;
        this.expirationTimeUnit = expirationTimeUnit;

        this.loaderReactor = new CompositeDisposable();
        this.debouncedLoadSignal = PublishSubject.create();
        this.debouncedLoader = getDebouncedLoader();

        this.sources = new DynamicConcatenatingMediaSource();

        this.syncReactor = new SerialDisposable();
        this.loadingItems = Collections.synchronizedSet(new HashSet<>());

        if (playQueue.getBroadcastReceiver() != null) {
            playQueue.getBroadcastReceiver()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getReactor());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Exposed Methods
    //////////////////////////////////////////////////////////////////////////*/
    /**
     * Dispose the manager and releases all message buses and loaders.
     * */
    public void dispose() {
        if (debouncedLoadSignal != null) debouncedLoadSignal.onComplete();
        if (debouncedLoader != null) debouncedLoader.dispose();
        if (playQueueReactor != null) playQueueReactor.cancel();
        if (loaderReactor != null) loaderReactor.dispose();
        if (syncReactor != null) syncReactor.dispose();
        if (sources != null) sources.releaseSource();

        playQueueReactor = null;
        loaderReactor = null;
        syncReactor = null;
        syncedItem = null;
        sources = null;
    }

    /**
     * Loads the current playing stream and the streams within its windowSize bound.
     *
     * Unblocks the player once the item at the current index is loaded.
     * */
    public void load() {
        loadDebounced();
    }

    /**
     * Blocks the player and repopulate the sources.
     *
     * Does not ensure the player is unblocked and should be done explicitly
     * through {@link #load() load}.
     * */
    public void reset() {
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
                if (playQueueReactor != null) playQueueReactor.cancel();
                playQueueReactor = d;
                playQueueReactor.request(1);
            }

            @Override
            public void onNext(@NonNull PlayQueueEvent playQueueMessage) {
                if (playQueueReactor != null) onPlayQueueChanged(playQueueMessage);
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
                loadImmediate(); // low frequency, critical events
                break;
            case APPEND:
            case REMOVE:
            case SELECT:
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
        if (playQueueReactor != null) playQueueReactor.request(1);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playback Locking
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isPlayQueueReady() {
        if (playQueue == null) return false;

        final boolean isWindowLoaded = playQueue.size() - playQueue.getIndex() > WINDOW_SIZE;
        return playQueue.isComplete() || isWindowLoaded;
    }

    private boolean isPlaybackReady() {
        if (sources == null || playQueue == null || sources.getSize() != playQueue.size()) {
            return false;
        }

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
        if (isBlocked) return;

        playbackListener.block();
        resetSources();

        isBlocked = true;
    }

    private void tryUnblock() {
        if (isPlayQueueReady() && isPlaybackReady() && isBlocked && sources != null) {
            isBlocked = false;
            playbackListener.unblock(sources);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Metadata Synchronization TODO: maybe this should be a separate manager
    //////////////////////////////////////////////////////////////////////////*/

    private void sync() {
        final PlayQueueItem currentItem = playQueue.getItem();
        if (isBlocked || currentItem == null) return;

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

    private void syncInternal(@android.support.annotation.NonNull final PlayQueueItem item,
                              @Nullable final StreamInfo info) {
        if (playQueue == null || playbackListener == null) return;
        // Ensure the current item is up to date with the play queue
        if (playQueue.getItem() == item && playQueue.getItem() == syncedItem) {
            playbackListener.sync(syncedItem, info);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Loading
    //////////////////////////////////////////////////////////////////////////*/

    private Disposable getDebouncedLoader() {
        return debouncedLoadSignal
                .debounce(loadDebounceMillis, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timestamp -> loadImmediate());
    }

    private void loadDebounced() {
        debouncedLoadSignal.onNext(System.currentTimeMillis());
    }

    private void loadImmediate() {
        // The current item has higher priority
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem(currentIndex);
        if (currentItem == null) return;
        loadItem(currentItem);

        // The rest are just for seamless playback
        final int leftBound = currentIndex + 1;
        final int rightLimit = leftBound + WINDOW_SIZE;
        final int rightBound = Math.min(playQueue.size(), rightLimit);
        final List<PlayQueueItem> items = new ArrayList<>(
                playQueue.getStreams().subList(leftBound,rightBound));

        // Do a round robin
        final int excess = rightLimit - playQueue.size();
        if (excess >= 0) {
            items.addAll(playQueue.getStreams().subList(0, Math.min(playQueue.size(), excess)));
        }

        for (final PlayQueueItem item: items) loadItem(item);
    }

    private void loadItem(@Nullable final PlayQueueItem item) {
        if (sources == null || item == null) return;

        final int index = playQueue.indexOf(item);
        if (index > sources.getSize() - 1) return;

        final Consumer<ManagedMediaSource> onDone = mediaSource -> {
            if (DEBUG) Log.d(TAG, " Loaded: [" + item.getTitle() +
                    "] with url: " + item.getUrl());

            if (isCorrectionNeeded(item)) update(playQueue.indexOf(item), mediaSource);

            loadingItems.remove(item);
            tryUnblock();
            sync();
        };

        if (!loadingItems.contains(item) && isCorrectionNeeded(item)) {
            if (DEBUG) Log.d(TAG, "Loading: [" + item.getTitle() +
                    "] with url: " + item.getUrl());

            loadingItems.add(item);
            final Disposable loader = getLoadedMediaSource(item)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(onDone);
            loaderReactor.add(loader);
        }

        tryUnblock();
        sync();
    }

    private Single<ManagedMediaSource> getLoadedMediaSource(@NonNull final PlayQueueItem stream) {
        return stream.getStream().map(streamInfo -> {
            if (playbackListener == null) {
                return new FailedMediaSource(stream, new IllegalStateException(
                        "MediaSourceManager playback listener unavailable"));
            }

            final MediaSource source = playbackListener.sourceOf(stream, streamInfo);
            if (source == null) {
                final Exception exception = new IllegalStateException(
                        "Unable to resolve source from stream info." +
                                " URL: " + stream.getUrl() +
                                ", audio count: " + streamInfo.audio_streams.size() +
                                ", video count: " + streamInfo.video_only_streams.size() +
                                streamInfo.video_streams.size());
                return new FailedMediaSource(stream, new IllegalStateException(exception));
            }

            final long expiration = System.currentTimeMillis() +
                    TimeUnit.MILLISECONDS.convert(expirationTimeMillis, expirationTimeUnit);
            return new LoadedMediaSource(source, stream, expiration);
        }).onErrorReturn(throwable -> new FailedMediaSource(stream, throwable));
    }

    private boolean isCorrectionNeeded(@NonNull final PlayQueueItem item) {
        if (playQueue == null || sources == null) return false;

        final int index = playQueue.indexOf(item);
        if (index == -1 || index >= sources.getSize()) return false;

        final MediaSource mediaSource = sources.getMediaSource(index);
        return !(mediaSource instanceof ManagedMediaSource) ||
                ((ManagedMediaSource) mediaSource).canReplace(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Playlist Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private void resetSources() {
        if (this.sources != null) this.sources.releaseSource();
        this.sources = new DynamicConcatenatingMediaSource();
    }

    private void populateSources() {
        if (sources == null || sources.getSize() >= playQueue.size()) return;

        for (int index = sources.getSize() - 1; index < playQueue.size(); index++) {
            emplace(index, new PlaceholderMediaSource());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Playlist Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Places a {@link MediaSource} into the {@link DynamicConcatenatingMediaSource}
     * with position * in respect to the play queue only if no {@link MediaSource}
     * already exists at the given index.
     * */
    private void emplace(final int index, final MediaSource source) {
        if (sources == null) return;
        if (index < 0 || index < sources.getSize()) return;

        sources.addMediaSource(index, source);
    }

    /**
     * Removes a {@link MediaSource} from {@link DynamicConcatenatingMediaSource}
     * at the given index. If this index is out of bound, then the removal is ignored.
     * */
    private void remove(final int index) {
        if (sources == null) return;
        if (index < 0 || index > sources.getSize()) return;

        sources.removeMediaSource(index);
    }

    /**
     * Moves a {@link MediaSource} in {@link DynamicConcatenatingMediaSource}
     * from the given source index to the target index. If either index is out of bound,
     * then the call is ignored.
     * */
    private void move(final int source, final int target) {
        if (sources == null) return;
        if (source < 0 || target < 0) return;
        if (source >= sources.getSize() || target >= sources.getSize()) return;

        sources.moveMediaSource(source, target);
    }

    /**
     * Updates the {@link MediaSource} in {@link DynamicConcatenatingMediaSource}
     * at the given index with a given {@link MediaSource}. If the index is out of bound,
     * then the replacement is ignored.
     * */
    private void update(final int index, final MediaSource source) {
        if (sources == null) return;
        if (index < 0 || index >= sources.getSize()) return;

        sources.addMediaSource(index + 1, source, () -> {
            if (sources != null) sources.removeMediaSource(index);
        });
    }
}
