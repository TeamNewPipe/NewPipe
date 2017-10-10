package org.schabi.newpipe.player.playback;

import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.mediasource.DeferredMediaSource;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.events.ErrorEvent;
import org.schabi.newpipe.playlist.events.MoveEvent;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.SerialDisposable;
import io.reactivex.functions.Consumer;

public class MediaSourceManager implements DeferredMediaSource.Callback {
    private final String TAG = "MediaSourceManager@" + Integer.toHexString(hashCode());
    // One-side rolling window size for default loading
    // Effectively loads WINDOW_SIZE * 2 + 1 streams, must be greater than 0
    // todo: inject this parameter, allow user settings perhaps
    private static final int WINDOW_SIZE = 1;

    private PlaybackListener playbackListener;
    private PlayQueue playQueue;

    private DynamicConcatenatingMediaSource sources;

    private Subscription playQueueReactor;
    private SerialDisposable syncReactor;

    private boolean isBlocked;

    public MediaSourceManager(@NonNull final PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this.playbackListener = listener;
        this.playQueue = playQueue;

        this.syncReactor = new SerialDisposable();

        this.sources = new DynamicConcatenatingMediaSource();

        playQueue.getBroadcastReceiver()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getReactor());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // DeferredMediaSource listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public MediaSource sourceOf(StreamInfo info) {
        return playbackListener.sourceOf(info);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Exposed Methods
    //////////////////////////////////////////////////////////////////////////*/
    /**
     * Dispose the manager and releases all message buses and loaders.
     * */
    public void dispose() {
        if (playQueueReactor != null) playQueueReactor.cancel();
        if (syncReactor != null) syncReactor.dispose();
        if (sources != null) sources.releaseSource();

        playQueueReactor = null;
        syncReactor = null;
        sources = null;
        playbackListener = null;
        playQueue = null;
    }

    /**
     * Loads the current playing stream and the streams within its WINDOW_SIZE bound.
     *
     * Unblocks the player once the item at the current index is loaded.
     * */
    public void load() {
        // The current item has higher priority
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.getItem(currentIndex);
        if (currentItem == null) return;
        load(currentItem);

        // The rest are just for seamless playback
        final int leftBound = Math.max(0, currentIndex - WINDOW_SIZE);
        final int rightLimit = currentIndex + WINDOW_SIZE + 1;
        final int rightBound = Math.min(playQueue.size(), rightLimit);
        final List<PlayQueueItem> items = new ArrayList<>(playQueue.getStreams().subList(leftBound, rightBound));

        // Do a round robin
        final int excess = rightLimit - playQueue.size();
        if (excess >= 0) items.addAll(playQueue.getStreams().subList(0, Math.min(playQueue.size(), excess)));

        for (final PlayQueueItem item: items) load(item);
    }

    /**
     * Blocks the player and repopulate the sources.
     *
     * Does not ensure the player is unblocked and should be done explicitly through {@link #load() load}.
     * */
    public void reset() {
        tryBlock();
        populateSources();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Event Reactor
    //////////////////////////////////////////////////////////////////////////*/

    private Subscriber<PlayQueueMessage> getReactor() {
        return new Subscriber<PlayQueueMessage>() {
            @Override
            public void onSubscribe(@NonNull Subscription d) {
                if (playQueueReactor != null) playQueueReactor.cancel();
                playQueueReactor = d;
                playQueueReactor.request(1);
            }

            @Override
            public void onNext(@NonNull PlayQueueMessage playQueueMessage) {
                onPlayQueueChanged(playQueueMessage);
            }

            @Override
            public void onError(@NonNull Throwable e) {}

            @Override
            public void onComplete() {}
        };
    }

    private void onPlayQueueChanged(final PlayQueueMessage event) {
        // why no pattern matching in Java =(
        switch (event.type()) {
            case INIT:
            case REORDER:
                reset();
                break;
            case APPEND:
                populateSources();
                break;
            case SELECT:
                sync();
                break;
            case REMOVE:
                final RemoveEvent removeEvent = (RemoveEvent) event;
                remove(removeEvent.index());
                break;
            case MOVE:
                final MoveEvent moveEvent = (MoveEvent) event;
                move(moveEvent.getFromIndex(), moveEvent.getToIndex());
                break;
            case ERROR:
            default:
                break;
        }

        if (!isPlayQueueReady()) {
            tryBlock();
            playQueue.fetch();
        } else if (playQueue.isEmpty()) {
            playbackListener.shutdown();
        } else {
            load(); // All event warrants a load
        }

        if (playQueueReactor != null) playQueueReactor.request(1);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Internal Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isPlayQueueReady() {
        return playQueue.isComplete() || playQueue.size() - playQueue.getIndex() > WINDOW_SIZE;
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

    private void sync() {
        final PlayQueueItem currentItem = playQueue.getItem();

        final Consumer<StreamInfo> syncPlayback = new Consumer<StreamInfo>() {
            @Override
            public void accept(StreamInfo streamInfo) throws Exception {
                playbackListener.sync(currentItem, streamInfo);
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Log.e(TAG, "Sync error:", throwable);
                playbackListener.sync(currentItem,null);
            }
        };

        syncReactor.set(currentItem.getStream().subscribe(syncPlayback, onError));
    }

    private void load(@Nullable final PlayQueueItem item) {
        if (item == null) return;

        final int index = playQueue.indexOf(item);
        if (index > sources.getSize() - 1) return;

        final DeferredMediaSource mediaSource = (DeferredMediaSource) sources.getMediaSource(playQueue.indexOf(item));
        if (mediaSource.state() == DeferredMediaSource.STATE_PREPARED) mediaSource.load();
        if (tryUnblock()) sync();
    }

    private void resetSources() {
        if (this.sources != null) this.sources.releaseSource();
        this.sources = new DynamicConcatenatingMediaSource();
    }

    private void populateSources() {
        if (sources == null) return;

        for (final PlayQueueItem item : playQueue.getStreams()) {
            insert(playQueue.indexOf(item), new DeferredMediaSource(item, this));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Media Source List Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Inserts a source into {@link DynamicConcatenatingMediaSource} with position
     * in respect to the play queue.
     *
     * If the play queue index already exists, then the insert is ignored.
     * */
    private void insert(final int queueIndex, final DeferredMediaSource source) {
        if (queueIndex < 0) return;

        sources.addMediaSource(queueIndex, source);
    }

    /**
     * Removes a source from {@link DynamicConcatenatingMediaSource} with the given play queue index.
     *
     * If the play queue index does not exist, the removal is ignored.
     * */
    private void remove(final int queueIndex) {
        if (queueIndex < 0) return;

        sources.removeMediaSource(queueIndex);
    }

    private void move(final int source, final int target) {
        if (source < 0 || target < 0) return;
        if (source >= sources.getSize() || target >= sources.getSize()) return;

        sources.moveMediaSource(source, target);
    }
}
