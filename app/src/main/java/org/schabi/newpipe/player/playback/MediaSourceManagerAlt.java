package org.schabi.newpipe.player.playback;

import android.support.annotation.Nullable;

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
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

public class MediaSourceManagerAlt {
    // One-side rolling window size for default loading
    // Effectively loads windowSize * 2 + 1 streams per call to load, must be greater than 0
    private final int windowSize;
    private final PlaybackListener playbackListener;
    private final PlayQueue playQueue;

    // Process only the last load order when receiving a stream of load orders (lessens I/O)
    // The higher it is, the less loading occurs during rapid noncritical timeline changes
    // Not recommended to go below 100ms
    private final long loadDebounceMillis;
    private final PublishSubject<Long> debouncedLoadSignal;
    private final Disposable debouncedLoader;

    private DynamicConcatenatingMediaSource sources;

    private Subscription playQueueReactor;
    private CompositeDisposable loaderReactor;

    private PlayQueueItem syncedItem;

    private boolean isBlocked;

    public MediaSourceManagerAlt(@NonNull final PlaybackListener listener,
                                 @NonNull final PlayQueue playQueue) {
        this(listener, playQueue, 1, 400L);
    }

    private MediaSourceManagerAlt(@NonNull final PlaybackListener listener,
                                  @NonNull final PlayQueue playQueue,
                                  final int windowSize,
                                  final long loadDebounceMillis) {
        if (windowSize <= 0) {
            throw new UnsupportedOperationException("MediaSourceManager window size must be greater than 0");
        }

        this.playbackListener = listener;
        this.playQueue = playQueue;
        this.windowSize = windowSize;
        this.loadDebounceMillis = loadDebounceMillis;

        this.loaderReactor = new CompositeDisposable();
        this.debouncedLoadSignal = PublishSubject.create();
        this.debouncedLoader = getDebouncedLoader();

        this.sources = new DynamicConcatenatingMediaSource();

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
        if (debouncedLoadSignal != null) debouncedLoadSignal.onComplete();
        if (debouncedLoader != null) debouncedLoader.dispose();
        if (playQueueReactor != null) playQueueReactor.cancel();
        if (loaderReactor != null) loaderReactor.dispose();
        if (sources != null) sources.releaseSource();

        playQueueReactor = null;
        loaderReactor = null;
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
     * Does not ensure the player is unblocked and should be done explicitly through {@link #load() load}.
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
    // Internal Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isPlayQueueReady() {
        final boolean isWindowLoaded = playQueue.size() - playQueue.getIndex() > windowSize;
        return playQueue.isComplete() || isWindowLoaded;
    }

    private boolean tryBlock() {
        if (!isBlocked) {
            playbackListener.block();
            resetSources();
            isBlocked = true;
            return true;
        }
        return false;
    }

    private boolean tryUnblock() {
        if (isPlayQueueReady() && isBlocked && sources != null) {
            isBlocked = false;
            playbackListener.unblock(sources);
            return true;
        }
        return false;
    }

    private void sync(final PlayQueueItem item, final StreamInfo info) {
        final PlayQueueItem currentItem = playQueue.getItem();
        if (currentItem != item || syncedItem == item || playbackListener == null) return;

        syncedItem = currentItem;
        // Ensure the current item is up to date with the play queue
        if (playQueue.getItem() == currentItem && playQueue.getItem() == syncedItem) {
            playbackListener.sync(syncedItem, info);
        }
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
        final int leftBound = Math.max(0, currentIndex - windowSize);
        final int rightLimit = currentIndex + windowSize + 1;
        final int rightBound = Math.min(playQueue.size(), rightLimit);
        final List<PlayQueueItem> items = new ArrayList<>(playQueue.getStreams().subList(leftBound, rightBound));

        // Do a round robin
        final int excess = rightLimit - playQueue.size();
        if (excess >= 0) items.addAll(playQueue.getStreams().subList(0, Math.min(playQueue.size(), excess)));

        for (final PlayQueueItem item: items) loadItem(item);
    }

    private void loadItem(@Nullable final PlayQueueItem item) {
        if (sources == null || item == null) return;

        final int index = playQueue.indexOf(item);
        if (index > sources.getSize() - 1) return;

        if (((ManagedMediaSource) sources.getMediaSource(index)).canReplace()) {
            final Disposable loader = getMediaSource(item)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(mediaSource -> update(playQueue.indexOf(item), mediaSource));
            loaderReactor.add(loader);
        }

        tryUnblock();
        if (!isBlocked) {
            final MediaSource mediaSource = sources.getMediaSource(playQueue.indexOf(item));
            final StreamInfo info = mediaSource instanceof LoadedMediaSource ?
                    ((LoadedMediaSource) mediaSource).getStreamInfo() : null;
            sync(item, info);
        }
    }

    private void resetSources() {
        if (this.sources != null) this.sources.releaseSource();
        this.sources = new DynamicConcatenatingMediaSource();
    }

    private void populateSources() {
        if (sources == null) return;

        for (final PlayQueueItem item : playQueue.getStreams()) {
            insert(playQueue.indexOf(item), new PlaceholderMediaSource());
        }
    }

    private Disposable getDebouncedLoader() {
        return debouncedLoadSignal
                .debounce(loadDebounceMillis, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timestamp -> loadImmediate());
    }

    private Single<ManagedMediaSource> getMediaSource(@NonNull final PlayQueueItem stream) {
        return stream.getStream().map(streamInfo -> {
            if (playbackListener == null) {
                return new FailedMediaSource(stream, new IllegalStateException(
                        "MediaSourceManager playback listener unavailable"));
            }

            final MediaSource source = playbackListener.sourceOf(stream, streamInfo);
            if (source == null) {
                return new FailedMediaSource(stream, new IllegalStateException(
                        "MediaSource resolution is null"));
            }

            return new LoadedMediaSource(stream, streamInfo, source,
                    TimeUnit.MILLISECONDS.convert(2, TimeUnit.HOURS));
        }).onErrorReturn(throwable -> new FailedMediaSource(stream, throwable));
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Media Source List Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    private void update(final int queueIndex, final MediaSource source) {
        if (sources == null) return;
        if (queueIndex < 0 || queueIndex < sources.getSize()) return;

        sources.addMediaSource(queueIndex + 1, source);
        sources.removeMediaSource(queueIndex);
    }

    /**
     * Inserts a source into {@link DynamicConcatenatingMediaSource} with position
     * in respect to the play queue.
     *
     * If the play queue index already exists, then the insert is ignored.
     * */
    private void insert(final int queueIndex, final PlaceholderMediaSource source) {
        if (sources == null) return;
        if (queueIndex < 0 || queueIndex < sources.getSize()) return;

        sources.addMediaSource(queueIndex, source);
    }

    /**
     * Removes a source from {@link DynamicConcatenatingMediaSource} with the given play queue index.
     *
     * If the play queue index does not exist, the removal is ignored.
     * */
    private void remove(final int queueIndex) {
        if (sources == null) return;
        if (queueIndex < 0 || queueIndex > sources.getSize()) return;

        sources.removeMediaSource(queueIndex);
    }

    private void move(final int source, final int target) {
        if (sources == null) return;
        if (source < 0 || target < 0) return;
        if (source >= sources.getSize() || target >= sources.getSize()) return;

        sources.moveMediaSource(source, target);
    }
}
