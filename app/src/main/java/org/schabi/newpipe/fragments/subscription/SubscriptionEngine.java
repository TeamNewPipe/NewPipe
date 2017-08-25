package org.schabi.newpipe.fragments.subscription;

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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Scheduler;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/** Subscription Service singleton:
 *  Provides a basis for channel Subscriptions.
 *  Provides access to subscription table in database as well as
 *  up-to-date observations on the subscribed channels
 *  */
public class SubscriptionEngine {

    private static SubscriptionEngine sInstance;
    private static final Object LOCK = new Object();

    public static SubscriptionEngine getInstance(Context context) {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new SubscriptionEngine(context);
                }
            }
        }
        return sInstance;
    }

    protected final String TAG = "SubscriptionEngine@" + Integer.toHexString(hashCode());
    private static final int SUBSCRIPTION_DEBOUNCE_INTERVAL = 500;
    private static final int SUBSCRIPTION_THREAD_POOL_SIZE = 4;

    private AppDatabase db;
    private Flowable<List<SubscriptionEntity>> subscription;

    private Scheduler subscriptionScheduler;

    private SubscriptionEngine(Context context) {
        db = NewPipeDatabase.getInstance( context );
        subscription = getSubscriptionInfos();

        final Executor subscriptionExecutor = Executors.newFixedThreadPool(SUBSCRIPTION_THREAD_POOL_SIZE);
        subscriptionScheduler = Schedulers.from(subscriptionExecutor);
    }

    /** Part of subscription observation pipeline
     * @see SubscriptionEngine#getSubscription()
     */
    private Flowable<List<SubscriptionEntity>> getSubscriptionInfos() {
        return subscriptionTable().findAll()
                // Wait for a period of infrequent updates and return the latest update
                .debounce(SUBSCRIPTION_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS)
                .share()            // Share allows multiple subscribers on the same observable
                .replay(1)          // Replay synchronizes subscribers to the last emitted result
                .autoConnect();
    }

    /**
     * Provides an observer to the latest update to the subscription table.
     *
     *  This observer may be subscribed multiple times, where each subscriber obtains
     *  the latest synchronized changes available, effectively share the same data
     *  across all subscribers.
     *
     *  This observer has a debounce cooldown, meaning if multiple updates are observed
     *  in the cooldown interval, only the latest changes are emitted to the subscribers.
     *  This reduces the amount of observations caused by frequent updates to the database.
     *  */
    @android.support.annotation.NonNull
    public Flowable<List<SubscriptionEntity>> getSubscription() {
        return subscription;
    }

    public Maybe<ChannelInfo> getChannelInfo(final SubscriptionEntity subscriptionEntity) {
        final StreamingService service = getService(subscriptionEntity.getServiceId());
        if (service == null) return Maybe.empty();

        final String url = subscriptionEntity.getUrl();
        final Callable<ChannelInfo> callable = new Callable<ChannelInfo>() {
            @Override
            public ChannelInfo call() throws Exception {
                final ChannelExtractor extractor = service.getChannelExtractorInstance(url, 0);
                return ChannelInfo.getInfo(extractor);
            }
        };

        return Maybe.fromCallable(callable).subscribeOn(subscriptionScheduler);
    }

    private StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }

    /** Returns the database access interface for subscription table. */
    public SubscriptionDAO subscriptionTable() {
        return db.subscriptionDAO();
    }

    public Completable updateChannelInfo(final int serviceId,
                                          final String channelUrl,
                                          final ChannelInfo info) {
        final Function<List<SubscriptionEntity>, CompletableSource> update = new Function<List<SubscriptionEntity>, CompletableSource>() {
            @Override
            public CompletableSource apply(@NonNull List<SubscriptionEntity> subscriptionEntities) throws Exception {
                if (subscriptionEntities.size() == 1) {
                    SubscriptionEntity subscription = subscriptionEntities.get(0);

                    // Subscriber count changes very often, making this check almost unnecessary.
                    // Consider removing it later.
                    if (isSubscriptionUpToDate(channelUrl, info, subscription)) {
                        subscription.setData(info.channel_name, info.avatar_url, "", info.subscriberCount);

                        return update(subscription);
                    }
                }

                return Completable.complete();
            }
        };

        return subscriptionTable().findAll(serviceId, channelUrl)
                .firstOrError()
                .flatMapCompletable(update);
    }

    private Completable update(final SubscriptionEntity updatedSubscription) {
        return Completable.fromRunnable(new Runnable() {
            @Override
            public void run() {
                subscriptionTable().update(updatedSubscription);
            }
        });
    }

    private boolean isSubscriptionUpToDate(final String channelUrl,
                                           final ChannelInfo info,
                                           final SubscriptionEntity entity) {
        return channelUrl.equals( entity.getUrl() ) &&
                info.service_id == entity.getServiceId() &&
                info.channel_name.equals( entity.getTitle() ) &&
                info.avatar_url.equals( entity.getThumbnailUrl() ) &&
                info.subscriberCount == entity.getSubscriberCount();
    }
}
