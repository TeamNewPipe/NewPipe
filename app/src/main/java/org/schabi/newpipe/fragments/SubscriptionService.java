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

public class SubscriptionService {
    protected final String TAG = "SubscriptionService@" + Integer.toHexString(hashCode());

    private static SubscriptionService sInstance;

    private static final Object LOCK = new Object();
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
        final Flowable<List<SubscriptionEntity>> subscriptions = db.subscriptionDAO().findAll();

        // Concat merges nested observables into a single one
        return Flowable.concat(subscriptions.map(getMapper()))
                // TODO: debounce should be on UI side, not here
                .debounce(1000, TimeUnit.MILLISECONDS) // Use only the latest change per second
                .share()            // Share allows multiple subscribers on the same observable
                .replay();          // Replay synchronizes subscribers to the last emitted result
    }

    private Function<List<SubscriptionEntity>, Flowable<Map<SubscriptionEntity, ChannelInfo>>> getMapper() {
        return new Function<List<SubscriptionEntity>, Flowable<Map<SubscriptionEntity, ChannelInfo>>>() {
            @Override
            public Flowable<Map<SubscriptionEntity, ChannelInfo>> apply(@NonNull List<SubscriptionEntity> subscriptionEntities) throws Exception {

                List<Flowable<Map<SubscriptionEntity, ChannelInfo>>> result = new ArrayList<>();

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
                    // WTF java
                    result.putAll( (Map<SubscriptionEntity, ChannelInfo>) map );
                }
                return result;
            }
        };
    }

    public ConnectableFlowable<Map<SubscriptionEntity, ChannelInfo>> getSubscription() {
        return subscription;
    }

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
