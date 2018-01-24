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

import io.reactivex.disposables.Disposable;

import static org.schabi.newpipe.fragments.list.feed.FeedFragment.DEBUG;

/**
 * A Subscriber that manages the displaying of batches of {@link StreamInfoItem}s.
 * <p>
 *     On every request and on init, the FeedItemsSubscriber adds every item in the batch to the
 *     {@link InfoListAdapter} of the FeedFragment and marks the item as displayed.
 * </p>
 */
final class FeedItemsSubscriber implements Subscriber<List<StreamInfoItem>>, Disposable {

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final FeedFragment feedFragment;
    private final InfoListAdapter infoListAdapter;

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
            feedFragment.setItemDisplayed(item);
        }

        feedFragment.setLoadingFinished();
    }

    @Override
    public void onError(Throwable t) {
        feedFragment.onError(t);
    }

    @Override
    public void onComplete() {
        if (DEBUG) Log.d(TAG, "onComplete() All items displayed");

        feedFragment.setAllItemsDisplayed(true);
        feedFragment.showListFooter(false);
        feedFragment.hideLoading();

        if (infoListAdapter.getItemsList().isEmpty()) {
            feedFragment.showEmptyState();
        }
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
}
