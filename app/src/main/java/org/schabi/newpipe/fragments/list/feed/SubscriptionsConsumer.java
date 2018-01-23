package org.schabi.newpipe.fragments.list.feed;

import android.util.Log;

import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;

import static org.schabi.newpipe.MainActivity.DEBUG;

/*
 * Created by wojcik.online on 2018-01-21.
 */

final class SubscriptionsConsumer implements Consumer<List<SubscriptionEntity>> {

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final SubscriptionService subscriptionService = SubscriptionService.getInstance();

    private final FeedFragment feedFragment;

    SubscriptionsConsumer(FeedFragment feedFragment) {
        this.feedFragment = feedFragment;
    }

    @Override
    public void accept(List<SubscriptionEntity> subscriptionEntities) throws Exception  {
        if (DEBUG) Log.d(TAG, "Loading newest item from every subscribed channel.");

        final List<StreamInfoItem> newItems = new ArrayList<>(subscriptionEntities.size());

        for (SubscriptionEntity subscriptionEntity : subscriptionEntities) {
            if (DEBUG) Log.d(TAG, "Loading channel info for " + subscriptionEntity.getName() + ".");
            ChannelInfo channelInfo = subscriptionService.getChannelInfo(subscriptionEntity)
                    .onErrorComplete()
                    .blockingGet();

            if (channelInfo == null) {
                continue;
            }

            if (DEBUG) Log.d(TAG, "Loading newest item for " + channelInfo.getName() + ".");
            List<InfoItem> streams = channelInfo.getRelatedStreams();

            if (!streams.isEmpty()) {
                InfoItem item = streams.get(0);
                if (item instanceof StreamInfoItem) {
                    if (!feedFragment.isItemAlreadyLoaded(getItemIdent(item))) {
                        newItems.add((StreamInfoItem) item);
                    }
                }
            }
        }

        Collections.sort(newItems, (item1, item2) -> item1.getUploadDate().compareTo(item2.getUploadDate()));

        if (DEBUG) Log.d(TAG, "Finished loading newest items.");

        AndroidSchedulers.mainThread().scheduleDirect(() -> feedFragment.handleResult(newItems));
    }

    private String getItemIdent(final InfoItem item) {
        return item.getServiceId() + item.getUrl();
    }
}
