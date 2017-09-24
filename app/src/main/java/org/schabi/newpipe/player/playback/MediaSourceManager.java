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
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class MediaSourceManager implements DeferredMediaSource.Callback {
    private final String TAG = "MediaSourceManager@" + Integer.toHexString(hashCode());
    // One-side rolling window size for default loading
    // Effectively loads WINDOW_SIZE * 2 + 1 streams, should be at least 1 to ensure gapless playback
    // todo: inject this parameter, allow user settings perhaps
    private static final int WINDOW_SIZE = 2;

    private PlaybackListener playbackListener;
    private PlayQueue playQueue;

    private DynamicConcatenatingMediaSource sources;
    // sourceToQueueIndex maps media source index to play queue index
    // Invariant 1: this list is sorted in ascending order
    // Invariant 2: this list contains no duplicates
    private List<Integer> sourceToQueueIndex;

    private Subscription playQueueReactor;
    private Disposable syncReactor;

    private boolean isBlocked;

    public MediaSourceManager(@NonNull final PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this.playbackListener = listener;
        this.playQueue = playQueue;

        this.sources = new DynamicConcatenatingMediaSource();
        this.sourceToQueueIndex = Collections.synchronizedList(new ArrayList<Integer>());

        playQueue.getBroadcastReceiver()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getReactor());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Exposed Methods
    //////////////////////////////////////////////////////////////////////////*/

    /*
    * Returns the media source index of the currently playing stream.
    * */
    public int getCurrentSourceIndex() {
        return sourceToQueueIndex.indexOf(playQueue.getIndex());
    }

    public int getQueueIndexOf(final int sourceIndex) {
        if (sourceIndex < 0 || sourceIndex >= sourceToQueueIndex.size()) return -1;
        return sourceToQueueIndex.get(sourceIndex);
    }

    public void dispose() {
        if (playQueueReactor != null) playQueueReactor.cancel();
        if (syncReactor != null) syncReactor.dispose();
        if (sources != null) sources.releaseSource();
        if (sourceToQueueIndex != null) sourceToQueueIndex.clear();

        playQueueReactor = null;
        syncReactor = null;
        sources = null;
        sourceToQueueIndex = null;
        playbackListener = null;
        playQueue = null;
    }

    public void load() {
        // The current item has higher priority
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.get(currentIndex);
        if (currentItem == null) return;
        load(currentItem);

        // The rest are just for seamless playback
        final int leftBound = Math.max(0, currentIndex - WINDOW_SIZE);
        final int rightLimit = currentIndex + WINDOW_SIZE + 1;
        final int rightBound = Math.min(playQueue.size(), rightLimit);
        final List<PlayQueueItem> items = new ArrayList<>(playQueue.getStreams().subList(leftBound, rightBound));

        final int excess = rightLimit - playQueue.size();
        if (excess >= 0) items.addAll(playQueue.getStreams().subList(0, Math.min(playQueue.size(), excess)));

        for (final PlayQueueItem item: items) load(item);
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
            public void onNext(@NonNull PlayQueueMessage event) {
                // why no pattern matching in Java =(
                switch (event.type()) {
                    case APPEND:
                        populateSources();
                        break;
                    case SELECT:
                        if (isCurrentIndexLoaded()) {
                            sync();
                        }
                        break;
                    case REMOVE:
                        final RemoveEvent removeEvent = (RemoveEvent) event;
                        if (!removeEvent.isCurrent()) {
                            remove(removeEvent.index());
                            break;
                        }
                    case INIT:
                    case REORDER:
                        tryBlock();
                        resetSources();
                        populateSources();
                        break;
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

            @Override
            public void onError(@NonNull Throwable e) {}

            @Override
            public void onComplete() {}
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Internal Helpers
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isPlayQueueReady() {
        return playQueue.isComplete() || playQueue.size() - playQueue.getIndex() > WINDOW_SIZE;
    }

    private boolean isCurrentIndexLoaded() {
        return getCurrentSourceIndex() != -1;
    }

    private boolean tryBlock() {
        if (!isBlocked) {
            playbackListener.block();
            isBlocked = true;
            return true;
        }
        return false;
    }

    private boolean tryUnblock() {
        if (isPlayQueueReady() && isCurrentIndexLoaded() && isBlocked) {
            isBlocked = false;
            playbackListener.unblock(sources);
            return true;
        }
        return false;
    }

    private void sync() {
        final PlayQueueItem currentItem = playQueue.getCurrent();

        final Consumer<StreamInfo> syncPlayback = new Consumer<StreamInfo>() {
            @Override
            public void accept(StreamInfo streamInfo) throws Exception {
                playbackListener.sync(streamInfo);
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Log.e(TAG, "Sync error:", throwable);
                playbackListener.sync(null);
            }
        };

        currentItem.getStream().subscribe(syncPlayback, onError);
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
        if (this.sourceToQueueIndex != null) this.sourceToQueueIndex.clear();

        this.sources = new DynamicConcatenatingMediaSource();
    }

    private void populateSources() {
        for (final PlayQueueItem item : playQueue.getStreams()) {
            insert(playQueue.indexOf(item), new DeferredMediaSource(item, this));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Media Source List Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    // Insert source into playlist with position in respect to the play queue
    // If the play queue index already exists, then the insert is ignored
    private void insert(final int queueIndex, final DeferredMediaSource source) {
        if (queueIndex < 0) return;

        int pos = Collections.binarySearch(sourceToQueueIndex, queueIndex);
        if (pos < 0) {
            final int sourceIndex = -pos-1;
            sourceToQueueIndex.add(sourceIndex, queueIndex);
            sources.addMediaSource(sourceIndex, source);
        }
    }

    private void remove(final int queueIndex) {
        if (queueIndex < 0) return;

        final int sourceIndex = sourceToQueueIndex.indexOf(queueIndex);
        if (sourceIndex == -1) return;

        sourceToQueueIndex.remove(sourceIndex);
        sources.removeMediaSource(sourceIndex);

        // Will be slow on really large arrays, fast enough for typical use case
        for (int i = sourceIndex; i < sourceToQueueIndex.size(); i++) {
            sourceToQueueIndex.set(i, sourceToQueueIndex.get(i) - 1);
        }
    }

    @Override
    public MediaSource sourceOf(StreamInfo info) {
        return playbackListener.sourceOf(info);
    }
}
