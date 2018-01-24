package org.schabi.newpipe.fragments.list.feed;

/*
 * Created by wojcik.online on 2018-01-21.
 */

import android.util.Log;

import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

import static org.schabi.newpipe.fragments.list.feed.FeedFragment.DEBUG;

/**
 * An Observer that watches the user's subscriptions.
 *
 * Every time the subscriptions change and on init, the SubscriptionsObserver:
 * <ul>
 *     <li>Loads all {@link StreamInfoItem}s of every subscribed channel</li>
 *     <li>Discards all items older than {@link PublicationTime#MAX_PUBLICATION_TIME}</li>
 *     <li>Sorts the items by publication time.</li>
 *     <li>Calls {@link FeedFragment#handleResult(List)}</li>
 * </ul>
 */
final class SubscriptionsObserver implements Observer<List<SubscriptionEntity>>, Disposable {

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final SubscriptionService subscriptionService = SubscriptionService.getInstance();

    private final FeedFragment feedFragment;

    private Disposable thisDisposable;

    private boolean areItemsBeingLoaded;

    /**
     * Creates an Observer that will watch the subscriptions and load the items of each.
     * @param feedFragment The current {@link FeedFragment}
     */
    SubscriptionsObserver(FeedFragment feedFragment) {
        this.feedFragment = feedFragment;
    }

    /**
     * @return Whether the Observer is busy; whether the item are currently being loaded.
     */
    boolean areItemsBeingLoaded() {
        return  areItemsBeingLoaded;
    }


    @Override
    public void onSubscribe(Disposable disposable) {
        if (DEBUG) Log.d(TAG, "onSubscribe()");
        thisDisposable = disposable;
    }

    @Override
    public void onNext(List<SubscriptionEntity> subscriptionEntities) {
        if (DEBUG) Log.d(TAG, "onNext(subscriptionEntities = [" + subscriptionEntities.size() +  "])");

        try {
            areItemsBeingLoaded = true;
            loadNewItems(subscriptionEntities);
        } finally {
            areItemsBeingLoaded = false;
        }
    }

    private void loadNewItems(List<SubscriptionEntity> subscriptionEntities) {
        final List<StreamInfoItem> newItems = new ArrayList<>(subscriptionEntities.size());

        for (SubscriptionEntity subscriptionEntity : subscriptionEntities) {
            loadNewItemsFromSubscription(newItems, subscriptionEntity);
        }

        Collections.sort(newItems, (item1, item2) -> {
            PublicationTime publicationTime1 = PublicationTime.parse(item1.getUploadDate());
            PublicationTime publicationTime2 = PublicationTime.parse(item2.getUploadDate());

            return publicationTime1.compareTo(publicationTime2);
        });

        if (DEBUG) Log.d(TAG, "Finished loading newest items.");

        AndroidSchedulers.mainThread().scheduleDirect(() -> feedFragment.handleResult(newItems));
    }

    private void loadNewItemsFromSubscription(List<StreamInfoItem> newItems,
                                              SubscriptionEntity subscriptionEntity) {
        if (DEBUG) Log.d(TAG, "Loading channel info for: " + subscriptionEntity.getName());
        ChannelInfo channelInfo = subscriptionService.getChannelInfo(subscriptionEntity)
                .onErrorComplete()
                .blockingGet();

        if (channelInfo == null) {
            return;
        }

        if (DEBUG) Log.d(TAG, "Loading newest items for " + channelInfo.getName() + ".");
        List<InfoItem> streams = channelInfo.getRelatedStreams();

        for (InfoItem item : streams) {
            if (item instanceof StreamInfoItem && !feedFragment.isItemAlreadyDisplayed(item)) {
                StreamInfoItem streamItem = (StreamInfoItem) item;
                PublicationTime publicationTime = PublicationTime.parse(streamItem.getUploadDate());

                if (publicationTime.compareTo(PublicationTime.MAX_PUBLICATION_TIME) <= 0) {
                    newItems.add(streamItem);
                } else {
                    // Once an item is older then the MAX_PUBLICATION_TIME,
                    // all following items will be older, too.
                    break;
                }
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        feedFragment.onError(throwable);
    }

    @Override
    public void onComplete() {
        // Do nothing.
    }

    @Override
    public void dispose() {
        if (DEBUG) Log.d(TAG, "dispose(); thisDisposable = " + thisDisposable);
        if (thisDisposable != null) {
            thisDisposable.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return thisDisposable != null && thisDisposable.isDisposed();
    }
}
