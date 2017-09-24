package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.playlist.events.AppendEvent;
import org.schabi.newpipe.playlist.events.InitEvent;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;
import org.schabi.newpipe.playlist.events.ReorderEvent;
import org.schabi.newpipe.playlist.events.SelectEvent;
import org.schabi.newpipe.playlist.events.UpdateEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.BehaviorSubject;

public abstract class PlayQueue implements Serializable {
    private final String TAG = "PlayQueue@" + Integer.toHexString(hashCode());

    public static final boolean DEBUG = true;

    private ArrayList<PlayQueueItem> backup;
    private ArrayList<PlayQueueItem> streams;
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
                indexEventBroadcast.toFlowable(BackpressureStrategy.BUFFER)
        ).observeOn(AndroidSchedulers.mainThread()).startWith(new InitEvent());

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
        // referential equality, can't think of a better way to do this
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
        if (index == getIndex()) return;

        int newIndex = index;
        if (index < 0) newIndex = 0;
        if (index >= streams.size()) newIndex = isComplete() ? index % streams.size() : streams.size() - 1;

        queueIndex.set(newIndex);
        indexEventBroadcast.onNext(new SelectEvent(newIndex));
    }

    public synchronized void offsetIndex(final int offset) {
        setIndex(getIndex() + offset);
    }

    public synchronized void updateIndex(final int index, final int selectedQuality) {
        if (index < 0 || index >= streams.size()) return;

        get(index).setSortedQualityIndex(selectedQuality);
        broadcast(new UpdateEvent(index));
    }

    protected synchronized void append(final PlayQueueItem... items) {
        streams.addAll(Arrays.asList(items));
        broadcast(new AppendEvent(items.length));
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

    public synchronized void shuffle() {
        backup = new ArrayList<>(streams);
        final PlayQueueItem current = getCurrent();
        Collections.shuffle(streams);
        queueIndex.set(streams.indexOf(current));

        broadcast(new ReorderEvent(true));
    }

    public synchronized void unshuffle() {
        if (backup == null) return;
        final PlayQueueItem current = getCurrent();
        streams.clear();
        streams = backup;
        queueIndex.set(streams.indexOf(current));

        broadcast(new ReorderEvent(false));
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

