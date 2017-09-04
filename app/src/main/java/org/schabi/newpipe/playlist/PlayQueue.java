package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.playlist.events.AppendEvent;
import org.schabi.newpipe.playlist.events.InitEvent;
import org.schabi.newpipe.playlist.events.NextEvent;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;
import org.schabi.newpipe.playlist.events.SelectEvent;
import org.schabi.newpipe.playlist.events.SwapEvent;

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
    public static final boolean DEBUG = true;

    private final List<PlayQueueItem> streams;
    private final AtomicInteger queueIndex;

    private final BehaviorSubject<PlayQueueMessage> eventBroadcast;
    private final Flowable<PlayQueueMessage> broadcastReceiver;
    private Subscription reportingReactor;

    PlayQueue() {
        this(0, Collections.<PlayQueueItem>emptyList());
    }

    PlayQueue(final int index, final List<PlayQueueItem> startWith) {
        streams = Collections.synchronizedList(new ArrayList<PlayQueueItem>());
        streams.addAll(startWith);

        queueIndex = new AtomicInteger(97);

        eventBroadcast = BehaviorSubject.create();
        broadcastReceiver = eventBroadcast
                .startWith(new InitEvent())
                .toFlowable(BackpressureStrategy.BUFFER);

        if (DEBUG) broadcastReceiver.subscribe(getSelfReporter());
    }

    // a queue is complete if it has loaded all items in an external playlist
    // single stream or local queues are always complete
    public abstract boolean isComplete();

    // load partial queue in the background, does nothing if the queue is complete
    public abstract void fetch();

    public abstract PlayQueueItem get(int index);

    public void dispose() {
        eventBroadcast.onComplete();

        if (reportingReactor != null) reportingReactor.cancel();
        reportingReactor = null;
    }

    public PlayQueueItem getCurrent() {
        return streams.get(getIndex());
    }

    public int size() {
        return streams.size();
    }

    @NonNull
    public List<PlayQueueItem> getStreams() {
        return Collections.unmodifiableList(streams);
    }

    @NonNull
    public Flowable<PlayQueueMessage> getBroadcastReceiver() {
        return broadcastReceiver;
    }

    private void broadcast(final PlayQueueMessage event) {
        eventBroadcast.onNext(event);
    }

    public int indexOf(final PlayQueueItem item) {
        // reference equality, can't think of a better way to do this
        // todo: better than this
        return streams.indexOf(item);
    }

    public int getIndex() {
        return queueIndex.get();
    }

    public void setIndex(final int index) {
        queueIndex.set(Math.min(Math.max(0, index), streams.size() - 1));
        broadcast(new SelectEvent(index));
    }

    public void incrementIndex() {
        final int index = queueIndex.incrementAndGet();
        broadcast(new NextEvent(index));
    }

    protected void append(final PlayQueueItem item) {
        streams.add(item);
        broadcast(new AppendEvent(1));
    }

    protected void append(final Collection<PlayQueueItem> items) {
        streams.addAll(items);
        broadcast(new AppendEvent(items.size()));
    }

    public void remove(final int index) {
        if (index >= streams.size()) return;

        streams.remove(index);
        broadcast(new RemoveEvent(index));
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

            broadcast(new SwapEvent(source, target));
        }
    }

    protected StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
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

