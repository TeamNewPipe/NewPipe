package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.playlist.events.AppendEvent;
import org.schabi.newpipe.playlist.events.ErrorEvent;
import org.schabi.newpipe.playlist.events.InitEvent;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;
import org.schabi.newpipe.playlist.events.RemoveEvent;
import org.schabi.newpipe.playlist.events.ReorderEvent;
import org.schabi.newpipe.playlist.events.SelectEvent;

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

/**
 * PlayQueue is responsible for keeping track of a list of streams and the index of
 * the stream that should be currently playing.
 *
 * This class contains basic manipulation of a playlist while also functions as a
 * message bus, providing all listeners with new updates to the play queue.
 *
 * This class can be serialized for passing intents, but in order to start the
 * message bus, it must be initialized.
 * */
public abstract class PlayQueue implements Serializable {
    private final String TAG = "PlayQueue@" + Integer.toHexString(hashCode());

    public static final boolean DEBUG = true;

    private ArrayList<PlayQueueItem> backup;
    private ArrayList<PlayQueueItem> streams;
    private final AtomicInteger queueIndex;

    private transient BehaviorSubject<PlayQueueMessage> eventBroadcast;
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

    /**
     * Initializes the play queue message buses.
     *
     * Also starts a self reporter for logging if debug mode is enabled.
     * */
    public void init() {
        eventBroadcast = BehaviorSubject.create();

        broadcastReceiver = eventBroadcast.toFlowable(BackpressureStrategy.BUFFER)
                .observeOn(AndroidSchedulers.mainThread())
                .startWith(new InitEvent());

        if (DEBUG) broadcastReceiver.subscribe(getSelfReporter());
    }

    /**
     * Dispose this play queue by stopping all message buses and clearing the playlist.
     * */
    public void dispose() {
        if (backup != null) backup.clear();
        if (streams != null) streams.clear();

        if (eventBroadcast != null) eventBroadcast.onComplete();
        if (reportingReactor != null) reportingReactor.cancel();

        broadcastReceiver = null;
        reportingReactor = null;
    }

    /**
     * Checks if the queue is complete.
     *
     * A queue is complete if it has loaded all items in an external playlist
     * single stream or local queues are always complete.
     * */
    public abstract boolean isComplete();

    /**
     * Load partial queue in the background, does nothing if the queue is complete.
     * */
    public abstract void fetch();

    /*//////////////////////////////////////////////////////////////////////////
    // Readonly ops
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Returns the current index that should be played.
     * */
    public int getIndex() {
        return queueIndex.get();
    }

    /**
     * Returns the current item that should be played.
     * */
    public PlayQueueItem getItem() {
        return getItem(getIndex());
    }

    /**
     * Returns the item at the given index.
     * May throw {@link IndexOutOfBoundsException}.
     * */
    public PlayQueueItem getItem(int index) {
        if (index >= streams.size() || streams.get(index) == null) return null;
        return streams.get(index);
    }

    /**
     * Returns the index of the given item using referential equality.
     * May be null despite play queue contains identical item.
     * */
    public int indexOf(final PlayQueueItem item) {
        // referential equality, can't think of a better way to do this
        // todo: better than this
        return streams.indexOf(item);
    }

    /**
     * Returns the current size of play queue.
     * */
    public int size() {
        return streams.size();
    }

    /**
     * Checks if the play queue is empty.
     * */
    public boolean isEmpty() {
        return streams.isEmpty();
    }

    /**
     * Determines if the current play queue is shuffled.
     * */
    public boolean isShuffled() {
        return backup != null;
    }

    /**
     * Returns an immutable view of the play queue.
     * */
    @NonNull
    public List<PlayQueueItem> getStreams() {
        return Collections.unmodifiableList(streams);
    }

    /**
     * Returns the play queue's update broadcast.
     * May be null if the play queue message bus is not initialized.
     * */
    @NonNull
    public Flowable<PlayQueueMessage> getBroadcastReceiver() {
        return broadcastReceiver;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Write ops
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Changes the current playing index to a new index.
     *
     * This method is guarded using in a circular manner for index exceeding the play queue size.
     *
     * Will emit a {@link SelectEvent} if the index is not the current playing index.
     * */
    public synchronized void setIndex(final int index) {
        if (index == getIndex()) return;

        final int oldIndex = getIndex();

        int newIndex = index;
        if (index < 0) newIndex = 0;
        if (index >= streams.size()) newIndex = isComplete() ? index % streams.size() : streams.size() - 1;

        queueIndex.set(newIndex);
        broadcast(new SelectEvent(oldIndex, newIndex));
    }

    /**
     * Changes the current playing index by an offset amount.
     *
     * Will emit a {@link SelectEvent} if offset is non-zero.
     * */
    public synchronized void offsetIndex(final int offset) {
        setIndex(getIndex() + offset);
    }

    /**
     * Appends the given {@link PlayQueueItem}s to the current play queue.
     *
     * Will emit a {@link AppendEvent} on any given context.
     * */
    public synchronized void append(final PlayQueueItem... items) {
        streams.addAll(Arrays.asList(items));
        broadcast(new AppendEvent(items.length));
    }

    /**
     * Appends the given {@link PlayQueueItem}s to the current play queue.
     *
     * Will emit a {@link AppendEvent} on any given context.
     * */
    public synchronized void append(final Collection<PlayQueueItem> items) {
        streams.addAll(items);
        broadcast(new AppendEvent(items.size()));
    }

    /**
     * Removes the item at the given index from the play queue.
     *
     * The current playing index will decrement if it is greater than the index being removed.
     * On cases where the current playing index exceeds the playlist range, it is set to 0.
     *
     * Will emit a {@link RemoveEvent} if the index is within the play queue index range.
     * */
    public synchronized void remove(final int index) {
        if (index >= streams.size() || index < 0) return;
        removeInternal(index);
        broadcast(new RemoveEvent(index));
    }

    /**
     * Report an exception for the item at the current index in order to remove it.
     *
     * This is done as a separate event as the underlying manager may have
     * different implementation regarding exceptions.
     * */
    public synchronized void error() {
        final int index = getIndex();
        removeInternal(index);
        broadcast(new ErrorEvent(index));
    }

    private synchronized void removeInternal(final int index) {
        final int currentIndex = queueIndex.get();

        if (currentIndex > index) {
            queueIndex.decrementAndGet();
        } else if (currentIndex >= size()) {
            queueIndex.set(0);
        }

        streams.remove(index);
    }

    /**
     * Shuffles the current play queue.
     *
     * This method first backs up the existing play queue and item being played.
     * Then a newly shuffled play queue will be generated along with the index of
     * the previously playing item.
     *
     * Will emit a {@link ReorderEvent} in any context.
     * */
    public synchronized void shuffle() {
        backup = new ArrayList<>(streams);
        final PlayQueueItem current = getItem();
        Collections.shuffle(streams);
        queueIndex.set(streams.indexOf(current));

        broadcast(new ReorderEvent());
    }

    /**
     * Unshuffles the current play queue if a backup play queue exists.
     *
     * This method undoes shuffling and index will be set to the previously playing item.
     *
     * Will emit a {@link ReorderEvent} if a backup exists.
     * */
    public synchronized void unshuffle() {
        if (backup == null) return;
        final PlayQueueItem current = getItem();
        streams.clear();
        streams = backup;
        backup = null;
        queueIndex.set(streams.indexOf(current));

        broadcast(new ReorderEvent());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Rx Broadcast
    //////////////////////////////////////////////////////////////////////////*/

    private void broadcast(final PlayQueueMessage event) {
        if (eventBroadcast != null) {
            eventBroadcast.onNext(event);
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
                Log.d(TAG, "Broadcast is shutting down.");
            }
        };
    }
}

