package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.BehaviorSubject;

public abstract class PlayQueue {
    private final String TAG = "PlayQueue@" + Integer.toHexString(hashCode());

    private List<PlayQueueItem> streams;
    private AtomicInteger queueIndex;

    private BehaviorSubject<PlayQueueEvent> changeBroadcast;
    private Flowable<PlayQueueEvent> playQueueFlowable;

    PlayQueue(final int index) {
        streams = Collections.synchronizedList(new ArrayList<PlayQueueItem>());
        queueIndex = new AtomicInteger(index);

        changeBroadcast = BehaviorSubject.create();
        playQueueFlowable = changeBroadcast.startWith(PlayQueueEvent.INIT).toFlowable(BackpressureStrategy.BUFFER);
    }

    // a queue is complete if it has loaded all items in an external playlist
    // single stream or local queues are always complete
    public abstract boolean isComplete();

    // load in the background the item at index, may do nothing if the queue is incomplete
    public abstract void load(int index);

    // load partial queue in the background, does nothing if the queue is complete
    public abstract void fetch();

    // returns a Rx Future to the stream info of the play queue item at index
    // may return an empty of the queue is incomplete
    public abstract PlayQueueItem get(int index);

    public abstract void dispose();

    public int size() {
        return streams.size();
    }

    @NonNull
    public List<PlayQueueItem> getStreams() {
        return Collections.unmodifiableList(streams);
    }

    @NonNull
    public Flowable<PlayQueueEvent> getPlayQueueFlowable() {
        return playQueueFlowable;
    }

    private void broadcast(final PlayQueueEvent event) {
        changeBroadcast.onNext(event);
    }

    public int getIndex() {
        return queueIndex.get();
    }

    public void setIndex(final int index) {
        queueIndex.set(index);
        broadcast(PlayQueueEvent.SELECT);
    }

    public void incrementIndex() {
        queueIndex.incrementAndGet();
        broadcast(PlayQueueEvent.NEXT);
    }

    protected void append(final PlayQueueItem item) {
        streams.add(item);
        broadcast(PlayQueueEvent.APPEND);
    }

    protected void append(final Collection<PlayQueueItem> items) {
        streams.addAll(items);
        broadcast(PlayQueueEvent.APPEND);
    }

    public void remove(final int index) {
        if (index >= streams.size()) return;
        final boolean isCurrent = index == queueIndex.get();

        streams.remove(index);

        if (isCurrent) {
            broadcast(PlayQueueEvent.REMOVE_CURRENT);
        } else {
            broadcast(PlayQueueEvent.REMOVE);
        }
    }

    protected void clear() {
        if (!streams.isEmpty()) {
            streams.clear();
            broadcast(PlayQueueEvent.CLEAR);
        }
    }

    protected void swap(final int source, final int target) {
        final List<PlayQueueItem> items = streams;
        if (source < items.size() && target < items.size()) {
            // Swap two items
            final PlayQueueItem sourceItem = items.get(source);
            final PlayQueueItem targetItem = items.get(target);

            items.set(target, sourceItem);
            items.set(source, targetItem);

            // If the current playing index is one of the swapped indices, change that as well
            final int index = queueIndex.get();
            if (index == source || index == target) {
                final int newIndex = index == source ? target : source;
                queueIndex.set(newIndex);
            }

            broadcast(PlayQueueEvent.SWAP);
        }
    }

    protected StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }
}

