package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.search.OnScrollBelowItemsListener;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.LongConsumer;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.report.UserAction.REQUESTED_CHANNEL;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class FeedFragment extends BaseFragment {
    private static final String VIEW_STATE_KEY = "view_state_key";
    private static final String INFO_ITEMS_KEY = "info_items_key";

    private static final int INITIAL_FEED_SIZE = 8;
    private static final int SPINNER_DURATION = 3000;

    private final String TAG = "FeedFragment@" + Integer.toHexString(hashCode());

    private InfoListAdapter infoListAdapter;
    private RecyclerView resultRecyclerView;

    private Parcelable viewState;
    private List<InfoItem> feedInfos;

    /* Used for subscription following fragment lifecycle */
    private CompositeDisposable disposables;
    private SubscriptionService subscriptionService;
    private Subscription feedSubscriber;
    private Disposable timerDisposable;

    private AtomicLong pendingRequestCount;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscription, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        disposables = new CompositeDisposable();
        subscriptionService = SubscriptionService.getInstance(getContext());

        pendingRequestCount = new AtomicLong();

        if (savedInstanceState != null) {
            // Get recycler view state
            viewState = savedInstanceState.getParcelable(VIEW_STATE_KEY);

            // Deserialize and get recycler adapter list
            final Object[] serializedInfoItems = (Object[]) savedInstanceState.getSerializable(INFO_ITEMS_KEY);
            if (serializedInfoItems != null) {
                final InfoItem[] infoItems = Arrays.copyOf(
                        serializedInfoItems,
                        serializedInfoItems.length,
                        InfoItem[].class
                );
                feedInfos = Arrays.asList(infoItems);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (disposables != null) disposables.dispose();
        if (feedSubscriber != null) feedSubscriber.cancel();
        if (timerDisposable != null) timerDisposable.dispose();

        timerDisposable = null;
        feedSubscriber = null;
        subscriptionService = null;
        disposables = null;

        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (resultRecyclerView != null) {
            outState.putParcelable(
                    VIEW_STATE_KEY,
                    resultRecyclerView.getLayoutManager().onSaveInstanceState()
            );
        }

        if (infoListAdapter != null) {
            outState.putSerializable(INFO_ITEMS_KEY, infoListAdapter.getItemsList().toArray());
        }
    }


    private RecyclerView.OnScrollListener getOnScrollListener() {
        return new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    viewState = recyclerView.getLayoutManager().onSaveInstanceState();
                }
            }
        };
    }

    private RecyclerView.OnScrollListener getOnBottomListener() {
        return new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(RecyclerView recyclerView) {
                requestFeed(1, false);
            }
        };
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        resultRecyclerView = ((RecyclerView) rootView.findViewById(R.id.result_list_view));
        resultRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(getActivity());
            infoListAdapter.setFooter(activity.getLayoutInflater().inflate(R.layout.pignate_footer, resultRecyclerView, false));
            infoListAdapter.showFooter(false);
            infoListAdapter.setOnStreamInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
                @Override
                public void selected(int serviceId, String url, String title) {
                    NavigationHelper.openVideoDetailFragment(getParentFragment().getFragmentManager(), serviceId, url, title);
                }
            });
        }

        resultRecyclerView.setAdapter(infoListAdapter);
        resultRecyclerView.addOnScrollListener(getOnScrollListener());
        resultRecyclerView.addOnScrollListener(getOnBottomListener());

        populateView();
    }

    private Single<ChannelInfo> getChannelInfoFetcher(final SubscriptionEntity subscriptionEntity) {
        final StreamingService service = getService(subscriptionEntity.getServiceId());
        final String url = subscriptionEntity.getUrl();
        final Callable<ChannelInfo> callable = new Callable<ChannelInfo>() {
            @Override
            public ChannelInfo call() throws Exception {
                final ChannelExtractor extractor = service != null ? service.getChannelExtractorInstance(url, 0) : null;
                return ChannelInfo.getInfo(extractor);
            }
        };

        return Single.fromCallable(callable)
                .subscribeOn(subscriptionService.subscriptionScheduler())
                .observeOn(AndroidSchedulers.mainThread());

    }

    private StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }

    private void populateView() {
        resetFragment();

        animateView(loadingProgressBar, true, 200);
        animateView(errorPanel, false, 200);

        final Function<List<SubscriptionEntity>, List<SubscriptionEntity>> unroll = new Function<List<SubscriptionEntity>, List<SubscriptionEntity>>() {
            @Override
            public List<SubscriptionEntity> apply(@NonNull List<SubscriptionEntity> subscriptionEntities) throws Exception {
                return subscriptionEntities;
            }
        };

        final Function<SubscriptionEntity, Single<ChannelInfo>> toChannelInfo = new Function<SubscriptionEntity, Single<ChannelInfo>>() {
            @Override
            public Single<ChannelInfo> apply(@NonNull SubscriptionEntity subscriptionEntity) throws Exception {
                return getChannelInfoFetcher(subscriptionEntity);
            }
        };

        final LongConsumer addToPendingCount = new LongConsumer() {
            @Override
            public void accept(long t) throws Exception {
                pendingRequestCount.getAndAdd(t);
            }
        };

        subscriptionService.getSubscription()
                .flatMapIterable(unroll)
                .flatMapSingle(toChannelInfo)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnRequest(addToPendingCount)
                .subscribe(getChannelInfoSubscriber());
    }

    private Subscriber<ChannelInfo> getChannelInfoSubscriber() {
        return new Subscriber<ChannelInfo>() {
            @Override
            public void onSubscribe(Subscription s) {
                feedSubscriber = s;
                subscriptionService.getSubscription().connect();

                if (viewState != null && resultRecyclerView != null &&
                        feedInfos != null && infoListAdapter != null && !feedInfos.isEmpty()) {

                    infoListAdapter.addInfoItemList(feedInfos);
                    resultRecyclerView.getLayoutManager().onRestoreInstanceState(viewState);

                    final int pendingRequestCount = INITIAL_FEED_SIZE - feedInfos.size();
                    if (pendingRequestCount > 0) {
                        requestFeed(pendingRequestCount, true);
                        infoListAdapter.showFooter(true);
                    }

                } else {
                    requestFeed(INITIAL_FEED_SIZE, true);
                }
            }

            @Override
            public void onNext(final ChannelInfo channelInfo) {
                pendingRequestCount.getAndDecrement();

                if (infoListAdapter == null || channelInfo.related_streams.isEmpty()) return;

                animateView(loadingProgressBar, false, 200);

                final InfoItem item = channelInfo.related_streams.get(0);
                // Keep requesting new items if the current one already exists
                if (!doesItemExist(infoListAdapter.getItemsList(), item)) {
                    infoListAdapter.addInfoItem(item);
                    infoListAdapter.showFooter(true);
                    startLoadSpinnerTimer();
                } else if (feedSubscriber != null) {
                    requestFeed(1, false);
                }
            }

            @Override
            public void onError(Throwable exception) {
                if (exception instanceof IOException) {
                    onRecoverableError(R.string.network_error);
                } else {
                    onUnrecoverableError(exception);
                }
            }

            @Override
            public void onComplete() {}
        };
    }

    private boolean doesItemExist(final List<InfoItem> items, final InfoItem item) {
        for (final InfoItem existingItem: items) {
            if (existingItem.infoType() == item.infoType() &&
                    existingItem.getTitle().equals(item.getTitle()) &&
                    existingItem.getLink().equals(item.getLink())) return true;
        }
        return false;
    }

    private void startLoadSpinnerTimer() {
        final CompletableObserver timerObserver = new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {
                if (timerDisposable != null) timerDisposable.dispose();
                timerDisposable = d;
            }

            @Override
            public void onComplete() {
                if (infoListAdapter != null) {
                    infoListAdapter.showFooter(false);
                }
            }

            @Override
            public void onError(Throwable exception) {
                Log.e(TAG, "Timer exception", exception);
            }
        };

        Completable.timer(SPINNER_DURATION, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timerObserver);
    }

    private void requestFeed(final int count, final boolean isSubscriber) {
        if (feedSubscriber == null) return;

        if (isSubscriber || pendingRequestCount.get() < 1) {
            feedSubscriber.request(count);
        }
    }

    private void onRecoverableError(int messageId) {
        if (!this.isAdded()) return;

        if (DEBUG) Log.d(TAG, "onError() called with: messageId = [" + messageId + "]");
        setErrorMessage(getString(messageId), true);
    }

    private void onUnrecoverableError(Throwable exception) {
        if (DEBUG) Log.d(TAG, "onUnrecoverableError() called with: exception = [" + exception + "]");
        ErrorActivity.reportError(getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(REQUESTED_CHANNEL, "unknown", "unknown", R.string.general_error));

        activity.finish();
    }

    private void resetFragment() {
        disposables.clear();
        infoListAdapter.clearStreamItemList();
    }

    @Override
    protected void reloadContent() {
        populateView();
    }

    @Override
    protected void setErrorMessage(String message, boolean showRetryButton) {
        super.setErrorMessage(message, showRetryButton);

        disposables.clear();
        infoListAdapter.clearStreamItemList();
    }
}
