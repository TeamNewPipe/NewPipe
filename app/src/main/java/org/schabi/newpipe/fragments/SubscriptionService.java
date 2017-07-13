package org.schabi.newpipe.fragments;

import android.content.Context;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.annotations.NonNull;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/** Subscription Service singleton:
 *  Provides a basis for channel Subscriptions.
 *  Provides access to subscription table in database as well as
 *  up-to-date observations on the subscribed channels
 *  */
public class SubscriptionService {
    protected final String TAG = "SubscriptionService@" + Integer.toHexString(hashCode());

    private static final int SUBSCRIPTION_DEBOUNCE_INTERVAL = 500;
    private static final Object LOCK = new Object();

    private static SubscriptionService sInstance;

    public static SubscriptionService getInstance(Context context) {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new SubscriptionService(context);
                }
            }
        }
        return sInstance;
    }

    private AppDatabase db;

    private ConnectableFlowable<List<SubscriptionEntity>> subscription;

    private SubscriptionService(Context context) {
        db = NewPipeDatabase.getInstance( context );
        subscription = getSubscriptionInfos();
    }

    /** Part of subscription observation pipeline
     * @see SubscriptionService#getSubscription()
     */
    private ConnectableFlowable<List<SubscriptionEntity>> getSubscriptionInfos() {
        // Concat merges nested observables into a single one
        return subscriptionTable().findAll()
                .debounce(SUBSCRIPTION_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS)
                .share()            // Share allows multiple subscribers on the same observable
                .replay();          // Replay synchronizes subscribers to the last emitted result
    }

    /**
     * Provides an observer to the latest update to the subscription table.
     *  This update also triggers a pull from the subscription source, thus it emits
     *  the latest data from the web.
     *
     *  This observer may be subscribed multiple times, where each subscriber obtains
     *  the latest synchronized changes available, effectively share the same data
     *  across all subscribers.
     *
     *  This observer has a debounce cooldown, meaning if multiple updates are observed
     *  in the cooldown interval, only the latest changes are emitted to the subscribers.
     *  This prevents
     *  <b>Note</b>: web source data are pulled with maximum concurrency, which may result in
     *  use extra memory and CPU during updates, should the subscription list be too large.
     *  */
    @android.support.annotation.NonNull
    public ConnectableFlowable<List<SubscriptionEntity>> getSubscription() {
        return subscription;
    }

    /** Returns the database access interface for subscription table. */
    public SubscriptionDAO subscriptionTable() {
        return db.subscriptionDAO();
    }
}
