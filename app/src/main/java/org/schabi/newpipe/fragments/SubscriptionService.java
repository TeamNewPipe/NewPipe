package org.schabi.newpipe.fragments;

import android.content.Context;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
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
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
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

    private static final int SUBSCRIPTION_DEBOUNCE_INTERVAL = 1000;
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

    private ConnectableFlowable<Map<SubscriptionEntity, ChannelInfo>> subscription;

    private SubscriptionService(Context context) {
        db = NewPipeDatabase.getInstance( context );
        subscription = getSubscriptionInfos();
    }

    private ConnectableFlowable<Map<SubscriptionEntity, ChannelInfo>> getSubscriptionInfos() {
        final Flowable<List<SubscriptionEntity>> subscriptions = db.subscriptionDAO().findAll()
                // Use only the latest change per interval
                .debounce(SUBSCRIPTION_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS);

        // Concat merges nested observables into a single one
        return Flowable.concat(subscriptions.map(getMapper()))
                .share()            // Share allows multiple subscribers on the same observable
                .replay();          // Replay synchronizes subscribers to the last emitted result
    }

    private Function<List<SubscriptionEntity>, Flowable<Map<SubscriptionEntity, ChannelInfo>>> getMapper() {
        return new Function<List<SubscriptionEntity>, Flowable<Map<SubscriptionEntity, ChannelInfo>>>() {
            @Override
            public Flowable<Map<SubscriptionEntity, ChannelInfo>> apply(@NonNull List<SubscriptionEntity> subscriptionEntities) throws Exception {

                List<Flowable<Map<SubscriptionEntity, ChannelInfo>>> result = new ArrayList<>();
                /* Ensures the resulting observation is nonempty when there is an emission */
                result.add( Flowable.just( Collections.<SubscriptionEntity, ChannelInfo>emptyMap() ) );

                for (final SubscriptionEntity subscription : subscriptionEntities) {
                    final StreamingService service = getService(subscription.getServiceId());

                    if (service != null) {
                        result.add(
                                /* Set the subscription scheduler to use IO here for concurrency */
                                Flowable.fromCallable(extract(subscription)).subscribeOn(Schedulers.io())
                        );
                    }
                }

                /* Whenever there is an emission, emit the latest version of every observable as a combined result */
                return Flowable.combineLatest( result, getCombiner() );
            }
        };
    }

    private Callable<Map<SubscriptionEntity, ChannelInfo>> extract(final SubscriptionEntity subscription) {
        return new Callable<Map<SubscriptionEntity, ChannelInfo>>() {
            @Override
            public Map<SubscriptionEntity, ChannelInfo> call() throws Exception {
                final int serviceId = subscription.getServiceId();
                final String url = subscription.getUrl();

                final StreamingService service = getService( serviceId );

                Map<SubscriptionEntity, ChannelInfo> result = new HashMap<>();
                if (service != null) {
                    final ChannelExtractor extractor = service.getChannelExtractorInstance(url, 0);
                    /* Need to keep track of both subscription and channel info since
                    * channel info does not contain web page url */
                    result.put(subscription, ChannelInfo.getInfo(extractor));
                }

                return result;
            }
        };
    }

    private Function<Object[], Map<SubscriptionEntity, ChannelInfo>> getCombiner() {
        return new Function<Object[], Map<SubscriptionEntity, ChannelInfo>>() {
            @Override
            public Map<SubscriptionEntity, ChannelInfo> apply(@NonNull Object[] maps) throws Exception {
                Map<SubscriptionEntity, ChannelInfo> result = new HashMap<>();
                for (final Object map : maps) {
                    // type erasure? WTF java
                    if (map instanceof Map<?, ?>) {
                        result.putAll( (Map<SubscriptionEntity, ChannelInfo>) map );
                    }
                }
                return result;
            }
        };
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
    public ConnectableFlowable<Map<SubscriptionEntity, ChannelInfo>> getSubscription() {
        return subscription;
    }

    /** Returns the database access interface for subscription table. */
    public SubscriptionDAO subscriptionTable() {
        return db.subscriptionDAO();
    }

    private StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }
}
