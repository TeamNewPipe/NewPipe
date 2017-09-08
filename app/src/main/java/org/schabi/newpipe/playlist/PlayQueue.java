package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.playlist.events.AppendEvent;
import org.schabi.newpipe.playlist.events.InitEvent;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;
import org.schabi.newpipe.playlist.events.SelectEvent;
import org.schabi.newpipe.playlist.events.MoveEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.BehaviorSubject;

public abstract class PlayQueue implements Serializable {
    private final String TAG = "PlayQueue@" + Integer.toHexString(hashCode());
    private final int INDEX_CHANGE_DEBOUNCE = 350;

    public static final boolean DEBUG = true;

    private final ArrayList<PlayQueueItem> streams;
    private final AtomicInteger queueIndex;

    private transient BehaviorSubject<PlayQueueMessage> streamsEventBroadcast;
    private transient BehaviorSubject<PlayQueueMessage> indexEventBroadcast;
    private transient Flowable<PlayQueueMessage> broadcastReceiver;
    private transient Subscription reportingReactor;

    PlayQueue() {
        this(0, Collections.<PlayQueueItem>emptyList());
    }

    PlayQueue(final int index, final List<PlayQueueItem> startWith) {
        streams = new ArrayList<>();
        streams.addAll(startWith);

        queueIndex = new AtomicInteger(index);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist actions
    //////////////////////////////////////////////////////////////////////////*/

    public void init() {
        streamsEventBroadcast = BehaviorSubject.create();
        indexEventBroadcast = BehaviorSubject.create();

        broadcastReceiver = Flowable.merge(
                streamsEventBroadcast.toFlowable(BackpressureStrategy.BUFFER),
                indexEventBroadcast.toFlowable(BackpressureStrategy.BUFFER).debounce(INDEX_CHANGE_DEBOUNCE, TimeUnit.MILLISECONDS)
        ).startWith(new InitEvent());

        if (DEBUG) broadcastReceiver.subscribe(getSelfReporter());
    }

    public void dispose() {
        streamsEventBroadcast.onComplete();

        if (reportingReactor != null) reportingReactor.cancel();
        reportingReactor = null;
    }

    // a queue is complete if it has loaded all items in an external playlist
    // single stream or local queues are always complete
    public abstract boolean isComplete();

    // load partial queue in the background, does nothing if the queue is complete
    public abstract void fetch();

    /*//////////////////////////////////////////////////////////////////////////
    // Readonly ops
    //////////////////////////////////////////////////////////////////////////*/

    public int getIndex() {
        return queueIndex.get();
    }

    public PlayQueueItem getCurrent() {
        return get(getIndex());
    }

    public PlayQueueItem get(int index) {
        if (index >= streams.size() || streams.get(index) == null) return null;
        return streams.get(index);
    }

    public int indexOf(final PlayQueueItem item) {
        // reference equality, can't think of a better way to do this
        // todo: better than this
        return streams.indexOf(item);
    }

    public int size() {
        return streams.size();
    }

    public boolean isEmpty() {
        return streams.isEmpty();
    }

    @NonNull
    public List<PlayQueueItem> getStreams() {
        return Collections.unmodifiableList(streams);
    }

    @NonNull
    public Flowable<PlayQueueMessage> getBroadcastReceiver() {
        return broadcastReceiver;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Write ops
    //////////////////////////////////////////////////////////////////////////*/

    public synchronized void setIndex(final int index) {
        if (index < 0 || index >= streams.size()) return;

        queueIndex.set(Math.min(Math.max(0, index), streams.size() - 1));
        indexEventBroadcast.onNext(new SelectEvent(index));
    }

    public synchronized void offsetIndex(final int offset) {
        setIndex(getIndex() + offset);
    }

    protected synchronized void append(final PlayQueueItem item) {
        streams.add(item);
        broadcast(new AppendEvent(1));
    }

    protected synchronized void append(final Collection<PlayQueueItem> items) {
        streams.addAll(items);
        broadcast(new AppendEvent(items.size()));
    }

    public synchronized void remove(final int index) {
        if (index >= streams.size() || index < 0) return;

        final boolean isCurrent = index == getIndex();

        streams.remove(index);
        // Nudge the index if it becomes larger than the queue size
        if (queueIndex.get() > size()) {
            queueIndex.set(size() - 1);
        }

        broadcast(new RemoveEvent(index, isCurrent));
    }

    protected synchronized void swap(final int source, final int target) {
        if (source < 0 || target < 0) return;

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

            broadcast(new MoveEvent(source, target));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Rx Broadcast
    //////////////////////////////////////////////////////////////////////////*/

    private void broadcast(final PlayQueueMessage event) {
        streamsEventBroadcast.onNext(event);
    }

    private Subscriber<PlayQueueMessage> getSelfReporter() {
        return new Subscriber<PlayQueueMessage>() {
            @Override
            public void onSubscribe(Subscription s) {
                if (reportingReactor != null) reportingReactor.cancel();
                reportingReactor = s;
                reportingReactor.request(1);
            }

            @Override
            public void onNext(PlayQueueMessage event) {
                Log.d(TAG, "Received broadcast: " + event.type().name() + ". Current index: " + getIndex() + ", play queue length: " + size() + ".");
                reportingReactor.request(1);
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "Received broadcast error", t);
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "Broadcast is shut down.");
            }
        };
    }
}

