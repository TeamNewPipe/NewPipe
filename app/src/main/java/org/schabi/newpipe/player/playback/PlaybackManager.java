package org.schabi.newpipe.player.playback;

import android.support.annotation.Nullable;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;
import org.schabi.newpipe.playlist.events.UpdateEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class PlaybackManager {
    private final String TAG = "PlaybackManager@" + Integer.toHexString(hashCode());
    // One-side rolling window size for default loading
    // Effectively loads WINDOW_SIZE * 2 streams
    private static final int WINDOW_SIZE = 3;

    private final PlaybackListener playbackListener;
    private final PlayQueue playQueue;

    private DynamicConcatenatingMediaSource sources;
    // sourceToQueueIndex maps media source index to play queue index
    // Invariant 1: this list is sorted in ascending order
    // Invariant 2: this list contains no duplicates
    private List<Integer> sourceToQueueIndex;

    private Subscription playQueueReactor;
    private Disposable syncReactor;
    private CompositeDisposable disposables;

    private boolean isBlocked;
    private boolean hasReset;

    public PlaybackManager(@NonNull final PlaybackListener listener,
                           @NonNull final PlayQueue playQueue) {
        this.playbackListener = listener;
        this.playQueue = playQueue;

        this.disposables = new CompositeDisposable();

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

    public int expectedTimelineSize() {
        return sources.getSize();
    }

    public void dispose() {
        if (playQueueReactor != null) playQueueReactor.cancel();
        if (disposables != null) disposables.dispose();
        if (syncReactor != null) syncReactor.dispose();
        if (sources != null) sources.releaseSource();
        if (sourceToQueueIndex != null) sourceToQueueIndex.clear();

        playQueueReactor = null;
        disposables = null;
        syncReactor = null;
        sources = null;
        sourceToQueueIndex = null;
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
                    case INIT:
                        tryBlock();
                        resetSources();
                        break;
                    case APPEND:
                        break;
                    case SELECT:
                        if (isBlocked) break;
                        if (isCurrentIndexLoaded()) sync(); else tryBlock();
                        break;
                    case REMOVE:
                        final RemoveEvent removeEvent = (RemoveEvent) event;
                        if (!removeEvent.isCurrent()) {
                            remove(removeEvent.index());
                            break;
                        }
                    case UPDATE:
                    case REORDER:
                        tryBlock();
                        resetSources();
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
            if (hasReset) {
                playbackListener.prepare(sources);
                hasReset = false;
            }

            isBlocked = false;
            playbackListener.unblock();
            return true;
        }
        return false;
    }

    private void sync() {
        final PlayQueueItem currentItem = playQueue.getCurrent();

        final Consumer<StreamInfo> syncPlayback = new Consumer<StreamInfo>() {
            @Override
            public void accept(StreamInfo streamInfo) throws Exception {
                playbackListener.sync(streamInfo, currentItem.getSortedQualityIndex());
            }
        };

        currentItem.getStream().subscribe(syncPlayback);
    }

    private void load() {
        // The current item has higher priority
        final int currentIndex = playQueue.getIndex();
        final PlayQueueItem currentItem = playQueue.get(currentIndex);
        if (currentItem != null) load(currentItem);
        else return;

        // The rest are just for seamless playback
        final int leftBound = Math.max(0, currentIndex - WINDOW_SIZE);
        final int rightBound = Math.min(playQueue.size(), currentIndex + WINDOW_SIZE);
        final List<PlayQueueItem> items = playQueue.getStreams().subList(leftBound, rightBound);
        for (final PlayQueueItem item: items) {
            load(item);
        }
    }

    private void load(@Nullable final PlayQueueItem item) {
        if (item == null) return;

        item.getStream().subscribe(new SingleObserver<StreamInfo>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                if (disposables == null) {
                    d.dispose();
                    return;
                }

                disposables.add(d);
            }

            @Override
            public void onSuccess(@NonNull StreamInfo streamInfo) {
                final MediaSource source = playbackListener.sourceOf(streamInfo, item.getSortedQualityIndex());
                insert(playQueue.indexOf(item), source);
                if (tryUnblock()) sync();
            }

            @Override
            public void onError(@NonNull Throwable e) {
                playQueue.remove(playQueue.indexOf(item));
                load();
            }
        });
    }

    private void resetSources() {
        if (this.disposables != null) this.disposables.clear();
        if (this.sources != null) this.sources.releaseSource();
        if (this.sourceToQueueIndex != null) this.sourceToQueueIndex.clear();

        this.sources = new DynamicConcatenatingMediaSource();
        this.hasReset = true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Media Source List Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    // Insert source into playlist with position in respect to the play queue
    // If the play queue index already exists, then the insert is ignored
    private void insert(final int queueIndex, final MediaSource source) {
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
}
