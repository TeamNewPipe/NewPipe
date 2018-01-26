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
import java.util.Calendar;
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
 *     <li>Discards all items older than {@link #oldestUploadDate}</li>
 *     <li>Sorts the items by publication time.</li>
 *     <li>Calls {@link FeedFragment#handleResult(List)}</li>
 * </ul>
 */
final class SubscriptionsObserver implements Observer<List<SubscriptionEntity>>, Disposable {

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final SubscriptionService subscriptionService = SubscriptionService.getInstance();

    private final FeedFragment feedFragment;

    private final Calendar oldestUploadDate;

    private Disposable thisDisposable;

    private boolean areItemsBeingLoaded;

    /**
     * Creates an Observer that will watch the subscriptions and load the items of each.
     * @param feedFragment The current {@link FeedFragment}
     */
    SubscriptionsObserver(FeedFragment feedFragment) {
        this.feedFragment = feedFragment;

        oldestUploadDate = Calendar.getInstance();
        oldestUploadDate.add(Calendar.MONTH, -1);
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
            if (item1.getUploadDate() != null && item2.getUploadDate() != null) {
                return item2.getUploadDate().compareTo(item1.getUploadDate());
            } else if (item1.getUploadDate() != null) {
                return 1;
            } else if (item2.getUploadDate() != null) {
                return -1;
            } else {
                return 0;
            }
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

                if (streamItem.getUploadDate() == null) {
                    // If a service doesn't provide a parsed upload date,
                    // include just the first element.
                    newItems.add(streamItem);
                    break;
                } else if (streamItem.getUploadDate().compareTo(oldestUploadDate) >= 0) {
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
