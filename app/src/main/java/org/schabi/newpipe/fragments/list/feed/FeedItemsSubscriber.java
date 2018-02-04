package org.schabi.newpipe.fragments.list.feed;

/*
 * Created by wojcik.online on 2018-01-22.
 */

import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoListAdapter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.disposables.Disposable;

import static org.schabi.newpipe.fragments.list.feed.FeedFragment.DEBUG;

/**
 * A Subscriber that manages the displaying of batches of {@link StreamInfoItem}s.
 * <p>
 *     On every request and on init, the FeedItemsSubscriber adds every item in the batch to the
 *     {@link InfoListAdapter} of the FeedFragment.
 * </p>
 */
final class FeedItemsSubscriber implements Subscriber<List<StreamInfoItem>>, Disposable {

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final FeedFragment feedFragment;
    private final InfoListAdapter infoListAdapter;

    private AtomicReference<FeedItemsState> feedItemsState =
            new AtomicReference<>(FeedItemsState.INITIALIZING);

    private Subscription thisSubscription;

    /**
     * Creates a Subscriber that will manage displaying batches of items.
     * @param feedFragment    The current {@link FeedFragment}
     * @param infoListAdapter The {@link InfoListAdapter} of the current FeedFragment
     * @see #requestNext()
     */
    FeedItemsSubscriber(FeedFragment feedFragment, InfoListAdapter infoListAdapter) {
        this.feedFragment = feedFragment;
        this.infoListAdapter = infoListAdapter;
    }

    /**
     * Requests to display the next batch of items.
     */
    void requestNext() {
        if (DEBUG) Log.d(TAG, "requestNext(); thisSubscription = " + thisSubscription);
        if (thisSubscription != null) {
            thisSubscription.request(1);
        }
    }

    /**
     * @return {@code true} if there are more items available to be displayed.<br>
     *         {@code false} if all items have already been displayed.
     */
    boolean areMoreItemsAvailable() {
        FeedItemsState feedItemsState = this.feedItemsState.get();
        return feedItemsState != FeedItemsState.ALL_ITEMS_DISPLAYED;
    }

    /**
     * @return {@code true} if the user has requested to display more items
     *         by interacting with the feed.<br>
     *         {@code false} otherwise.
     */
    boolean haveItemsBeenRequested() {
        FeedItemsState feedItemsState = this.feedItemsState.get();
        return feedItemsState == FeedItemsState.MORE_ITEMS_AVAILABLE
                || feedItemsState == FeedItemsState.ALL_ITEMS_DISPLAYED;
    }


    @Override
    public void onSubscribe(Subscription subscription) {
        if (DEBUG) Log.d(TAG, "onSubscribe()");
        thisSubscription = subscription;
        requestNext();
    }

    @Override
    public void onNext(List<StreamInfoItem> items) {
        if (DEBUG) Log.d(TAG, "onNext(items = [" + items.size() + "])");
        for (StreamInfoItem item: items) {
            infoListAdapter.addInfoItem(item);
        }

        switchFeedItemsState();
    }

    public void onError(Throwable t) {
        feedFragment.onError(t);
    }

    @Override
    public void onComplete() {
        if (DEBUG) Log.d(TAG, "onComplete() All items displayed");
        feedItemsState.set(FeedItemsState.ALL_ITEMS_DISPLAYED);

        if (infoListAdapter.getItemsList().isEmpty()) {
            feedFragment.showEmptyState();
        }
        feedFragment.updateViewState();
    }

    @Override
    public void dispose() {
        if (DEBUG) Log.d(TAG, "dispose(); thisSubscription = " + thisSubscription);
        if (thisSubscription != null) {
            thisSubscription.cancel();
            thisSubscription = null;
        }
    }

    @Override
    public boolean isDisposed() {
        return thisSubscription == null;
    }

    private void switchFeedItemsState() {
        FeedItemsState prevState = feedItemsState.get();

        boolean switched;
        switched = feedItemsState.compareAndSet(FeedItemsState.INITIALIZING,
                                                FeedItemsState.DISPLAYED_FIRST_BATCH)

                || feedItemsState.compareAndSet(FeedItemsState.DISPLAYED_FIRST_BATCH,
                                                FeedItemsState.MORE_ITEMS_AVAILABLE);

        FeedItemsState thenState = feedItemsState.get();
        if (DEBUG) Log.d(TAG, "switchFeedItemsState(); " + prevState + " -> " + thenState
                + " (" + switched + ")");
    }

    private enum FeedItemsState {
        /** The subscriber didn't have a chance to do anything, yet. */
        INITIALIZING,

        /** Displayed just the first batch of items that is requested automatically.  */
        DISPLAYED_FIRST_BATCH,

        /** Displayed more that the first batch, but there are still more item available. */
        MORE_ITEMS_AVAILABLE,

        /** Displayed all available items. */
        ALL_ITEMS_DISPLAYED,
    }
}
