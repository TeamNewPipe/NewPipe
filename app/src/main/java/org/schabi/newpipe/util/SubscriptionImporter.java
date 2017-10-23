package org.schabi.newpipe.util;

import android.os.AsyncTask;

import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.fragments.subscription.SubscriptionService;

import java.util.List;

/**
 * Created by gui on 19/10/17.
 */

public class SubscriptionImporter extends AsyncTask<List<SubscriptionEntity>, Void, Void> {
    @Override
    protected Void doInBackground(List<SubscriptionEntity>... lists) {
        List<SubscriptionEntity> subscriptions = lists[0];
        SubscriptionService subscriptionService;
        subscriptionService = SubscriptionService.getInstance();
        for (SubscriptionEntity subscription : subscriptions) {
            try {
                subscriptionService.subscriptionTable().insert(subscription);
            } catch (RuntimeException e){
                continue;
            }
        }
        return null;
    }
}
