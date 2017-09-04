package org.schabi.newpipe.player;

import android.util.Log;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;
import org.schabi.newpipe.playlist.events.SwapEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.MaybeObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

class MediaSourceManager {
    private final String TAG = "MediaSourceManager@" + Integer.toHexString(hashCode());
    // One-side rolling window size for default loading
    // Effectively loads WINDOW_SIZE * 2 streams
    private static final int WINDOW_SIZE = 3;

    private final DynamicConcatenatingMediaSource sources;
    // sourceToQueueIndex maps media source index to play queue index
    // Invariant 1: this list is sorted in ascending order
    // Invariant 2: this list contains no duplicates
    private final List<Integer> sourceToQueueIndex;

    private final PlaybackListener playbackListener;
    private final PlayQueue playQueue;

    private Subscription playQueueReactor;
    private Subscription loadingReactor;
    private CompositeDisposable disposables;

    private boolean isBlocked;

    interface PlaybackListener {
        /*
        * Called when the initial video has been loaded.
        * Signals to the listener that the media source is prepared, and
        * the player is ready to go.
        * */
        void init();

        /*
        * Called when the stream at the current queue index is not ready yet.
        * Signals to the listener to block the player from playing anything.
        * */
        void block();

        /*
        * Called when the stream at the current queue index is ready.
        * Signals to the listener to resume the player.
        * May be called at any time, even when the player is unblocked.
        * */
        void unblock();

        /*
        * Called when the queue index is refreshed.
        * Signals to the listener to synchronize the player's window to the manager's
        * window.
        * */
        void sync(final int windowIndex, final long windowPos, final StreamInfo info);

        /*
        * Requests the listener to resolve a stream info into a media source respective
        * of the listener's implementation (background, popup or main video player),
        * */
        MediaSource sourceOf(final StreamInfo info);
    }

    MediaSourceManager(@NonNull final MediaSourceManager.PlaybackListener listener,
                       @NonNull final PlayQueue playQueue) {
        this.sources = new DynamicConcatenatingMediaSource();
        this.sourceToQueueIndex = Collections.synchronizedList(new ArrayList<Integer>());

        this.playbackListener = listener;
        this.playQueue = playQueue;

        disposables = new CompositeDisposable();

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
    int getCurrentSourceIndex() {
        return sourceToQueueIndex.indexOf(playQueue.getIndex());
    }

    @NonNull
    DynamicConcatenatingMediaSource getMediaSource() {
        return sources;
    }

    /*
    * Called when the player has seamlessly transitioned to another stream.
    * Currently only expecting transitioning to the next stream and updates
    * the play queue that a transition has occurred.
    * */
    void refresh(final int newSourceIndex) {
        if (newSourceIndex == getCurrentSourceIndex()) return;

        if (newSourceIndex == getCurrentSourceIndex() + 1) {
            playQueue.incrementIndex();
        } else {
            //something went wrong
            Log.e(TAG, "Refresh media failed, reloading.");
        }

        sync();
    }

    void report(final Exception error) {
        // ignore error checking for now, just remove the current index
        if (error != null && !isBlocked) {
            doBlock();
        }

        final int index = playQueue.getIndex();
        remove(index);
        playQueue.remove(index);
        tryUnblock();
        sync();
    }

    void dispose() {
        if (loadingReactor != null) loadingReactor.cancel();
        if (playQueueReactor != null) playQueueReactor.cancel();
        if (disposables != null) disposables.dispose();

        loadingReactor = null;
        playQueueReactor = null;
        disposables = null;
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
                        init();
                        break;
                    case APPEND:
                        load();
                        break;
                    case SELECT:
                        onSelect();
                        break;
                    case REMOVE:
                        final RemoveEvent removeEvent = (RemoveEvent) event;
                        remove(removeEvent.index());
                        break;
                    case SWAP:
                        final SwapEvent swapEvent = (SwapEvent) event;
                        swap(swapEvent.getFrom(), swapEvent.getTo());
                        break;
                    case NEXT:
                    default:
                        break;
                }

                if (!isPlayQueueReady() && !isBlocked) {
                    doBlock();
                    playQueue.fetch();
                }
                if (playQueueReactor != null) playQueueReactor.request(1);
            }

            @Override
            public void onError(@NonNull Throwable e) {}

            @Override
            public void onComplete() {
                dispose();
            }
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

    private void doBlock() {
        playbackListener.block();
        isBlocked = true;
    }

    private void tryUnblock() {
        if (isPlayQueueReady() && isCurrentIndexLoaded() && isBlocked) {
            isBlocked = false;
            playbackListener.unblock();
        }
    }

    /*
    * Responds to a SELECT event.
    * When a change occur, the manager prepares by loading more.
    * If the current item has not been fully loaded,
    * */
    private void onSelect() {
        if (isCurrentIndexLoaded()) {
            sync();
        } else if (!isBlocked) {
            doBlock();
        }

        load();
    }

    private void sync() {
        final Consumer<StreamInfo> onSuccess = new Consumer<StreamInfo>() {
            @Override
            public void accept(StreamInfo streamInfo) throws Exception {
                playbackListener.sync(getCurrentSourceIndex(), 0L, streamInfo);
            }
        };

        playQueue.getCurrent().getStream().subscribe(onSuccess);
    }

    private void load() {
        final int currentIndex = playQueue.getIndex();
        load(playQueue.get(currentIndex));

        final int leftBound = Math.max(0, currentIndex - WINDOW_SIZE);
        final int rightBound = Math.min(playQueue.size(), currentIndex + WINDOW_SIZE);
        final List<PlayQueueItem> items = playQueue.getStreams().subList(leftBound, rightBound);
        for (final PlayQueueItem item: items) {
            load(item);
        }
    }

    private void init() {
        final PlayQueueItem init = playQueue.getCurrent();

        init.getStream().subscribe(new MaybeObserver<StreamInfo>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                if (disposables != null) {
                    disposables.add(d);
                } else {
                    d.dispose();
                }
            }

            @Override
            public void onSuccess(@NonNull StreamInfo streamInfo) {
                final MediaSource source = playbackListener.sourceOf(streamInfo);
                insert(playQueue.indexOf(init), source);

                if (getCurrentSourceIndex() != -1) {
                    playbackListener.init();
                    sync();
                    load();
                } else {
                    init();
                }
            }

            @Override
            public void onError(@NonNull Throwable e) {
                playQueue.remove(playQueue.indexOf(init));
                init();
            }

            @Override
            public void onComplete() {
                playQueue.remove(playQueue.indexOf(init));
                init();
            }
        });
    }

    private void load(final PlayQueueItem item) {
        item.getStream().subscribe(new MaybeObserver<StreamInfo>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                if (disposables != null) {
                    disposables.add(d);
                } else {
                    d.dispose();
                }
            }

            @Override
            public void onSuccess(@NonNull StreamInfo streamInfo) {
                final MediaSource source = playbackListener.sourceOf(streamInfo);
                insert(playQueue.indexOf(item), source);
                tryUnblock();
            }

            @Override
            public void onError(@NonNull Throwable e) {
                playQueue.remove(playQueue.indexOf(item));
                load();
            }

            @Override
            public void onComplete() {
                playQueue.remove(playQueue.indexOf(item));
                load();
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Media Source List Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    public void replace(final int queueIndex, final MediaSource source) {
        if (queueIndex < 0) return;

        final int sourceIndex = sourceToQueueIndex.indexOf(queueIndex);
        if (sourceIndex != -1) {
            // Add the source after the one to remove, so the window will remain the same in the player
            sources.addMediaSource(sourceIndex + 1, source);
            sources.removeMediaSource(sourceIndex);
        }
    }

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
        if (sourceIndex != -1) {
            sourceToQueueIndex.remove(sourceIndex);
            sources.removeMediaSource(sourceIndex);
            // Will be slow on really large arrays, fast enough for typical use case
            for (int i = sourceIndex; i < sourceToQueueIndex.size(); i++) {
                sourceToQueueIndex.set(i, sourceToQueueIndex.get(i) - 1);
            }
        }
    }

    private void swap(final int source, final int target) {
        final int sourceIndex = sourceToQueueIndex.indexOf(source);
        final int targetIndex = sourceToQueueIndex.indexOf(target);

        if (sourceIndex != -1 && targetIndex != -1) {
            sources.moveMediaSource(sourceIndex, targetIndex);
        } else if (sourceIndex != -1) {
            remove(sourceIndex);
        } else if (targetIndex != -1) {
            remove(targetIndex);
        }
    }
}
