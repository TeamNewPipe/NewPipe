package org.schabi.newpipe.fragments.list.feed;

/*
 * Created by wojcik.online on 2018-01-21.
 */

import android.util.Log;

import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.fragments.list.feed.FeedFragment.DEBUG;

/**
 * A wrapper for the {@link SubscriptionService} that maps the
 * list of {@link SubscriptionEntity}s into a {@link FeedInfo}.
 *
 * Every time the subscriptions change and on every subscription, the service:
 * <ul>
 *     <li>Gets the list of subscriptions.</li>
 *     <li>Loads all {@link StreamInfoItem}s of every subscribed channel.</li>
 *     <li>Discards all items older than {@link #oldestUploadDate}.</li>
 *     <li>Sorts the items by upload time.</li>
 * </ul>
 */
final class FeedSubscriptionService {

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final SubscriptionService subscriptionService = SubscriptionService.getInstance();

    private static FeedSubscriptionService instance;

    private final Calendar oldestUploadDate;

    private final Observable<FeedInfo> feedInfoObservable;

    private boolean areSubscriptionsBeingLoaded = true;

    private FeedSubscriptionService() {
        feedInfoObservable = subscriptionService.getSubscription()
                .toObservable()
                .map(this::getFeedInfoFromSubscriptions)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());

        oldestUploadDate = Calendar.getInstance();
        oldestUploadDate.add(Calendar.MONTH, -1);
    }

    /**
     * @return
     */
    public static FeedSubscriptionService getInstance() {
        if (instance == null) {
            instance = new FeedSubscriptionService();
        }

        return instance;
    }

    /**
     * @return
     */
    Observable<FeedInfo> getFeedInfoObservable() {
        return feedInfoObservable;
    }

    /**
     * @return
     */
    boolean areSubscriptionsBeingLoaded() {
        return areSubscriptionsBeingLoaded;
    }

    private FeedInfo getFeedInfoFromSubscriptions(List<SubscriptionEntity> subscriptionEntities)
            throws Exception {
        if (DEBUG) Log.d(TAG, "getFeedInfoFromSubscriptions(subscriptionEntities = ["
                + subscriptionEntities.size() +  "])");

        try {
            areSubscriptionsBeingLoaded = true;
            return loadFeedInfo(subscriptionEntities);
        } finally {
            areSubscriptionsBeingLoaded = false;
        }
    }

    private FeedInfo loadFeedInfo(List<SubscriptionEntity> subscriptionEntities) throws Exception {
        final FeedInfoCreator feedInfoCreator = new FeedInfoCreator(subscriptionEntities.size());

        for (SubscriptionEntity subscriptionEntity : subscriptionEntities) {
            loadNewItemsFromSubscription(feedInfoCreator, subscriptionEntity);
        }

        return feedInfoCreator.getFeedInfo();
    }

    private void loadNewItemsFromSubscription(FeedInfoCreator feedInfoCreator,
                                              SubscriptionEntity subscriptionEntity)
            throws Exception {
        if (DEBUG) Log.d(TAG, "Loading channel info for: " + subscriptionEntity.getName());

        ChannelInfo channelInfo =
                getMaybeValue(subscriptionService.getChannelInfo(subscriptionEntity));

        if (channelInfo == null) {
            return;
        }

        if (DEBUG) Log.d(TAG, "Loading newest items for " + channelInfo.getName() + ".");
        List<InfoItem> streams = channelInfo.getRelatedStreams();

        for (InfoItem item : streams) {
            if (item instanceof StreamInfoItem) {
                StreamInfoItem streamItem = (StreamInfoItem) item;

                if (streamItem.getUploadDate() == null) {
                    feedInfoCreator.add(streamItem);
                    if (streamItem.getStreamType() != StreamType.LIVE_STREAM) {
                        // If a service doesn't provide a parsed upload date, include only
                        // the first element unless it is a live stream.
                        break;
                    }
                } else if (streamItem.getUploadDate().compareTo(oldestUploadDate) >= 0) {
                    feedInfoCreator.add(streamItem);
                } else {
                    // Once an item is older then the oldestUploadDate,
                    // all following items will be older, too.
                    break;
                }
            }
        }
    }

    private <T> T getMaybeValue(Maybe<T> maybe) throws Exception {
        final Exception[] maybeInnerException = {null};
        T value = maybe.onErrorComplete(throwable -> {
                    if (throwable instanceof Exception) {
                        maybeInnerException[0] = (Exception) throwable;
                        return true;
                    } else return false;
                })
                .blockingGet();

        if (maybeInnerException[0] != null) {
            throw maybeInnerException[0];
        }

        return value;
    }

    /**
     * @param item The item in question.
     * @return An identifier used to keep track of items across different runs.
     */
    private static String getItemIdent(final InfoItem item) {
        return item.getServiceId() + item.getUrl();
    }

    private class FeedInfoCreator {
        private static final int EXPECTED_ITEMS_PER_SUBSCRIPTION = 5;
        private final List<StreamInfoItem> infoItems;

        FeedInfoCreator(int subscriptionCount) {
            infoItems = new ArrayList<>(EXPECTED_ITEMS_PER_SUBSCRIPTION * subscriptionCount);
        }

        void add(StreamInfoItem streamInfoItem) {
            infoItems.add(streamInfoItem);
        }

        FeedInfo getFeedInfo() {
            sortItems();
            long feedInfoHash = calculateFeedInfoHash();

            return new FeedInfo(Calendar.getInstance(), infoItems, feedInfoHash);
        }

        private void sortItems() {
            Collections.sort(infoItems, (item1, item2) -> {
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
        }

        private long calculateFeedInfoHash() {
            long feedInfoHash = 1;
            for (InfoItem item : infoItems) {
                feedInfoHash = 31 * feedInfoHash + getItemIdent(item).hashCode();
            }
            return feedInfoHash;
        }
    }
}
