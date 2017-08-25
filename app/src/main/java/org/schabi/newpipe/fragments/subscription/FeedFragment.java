package org.schabi.newpipe.fragments.subscription;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jakewharton.rxbinding2.view.RxView;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.fragments.BaseFragment;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Flowable;
import io.reactivex.MaybeObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

import static org.schabi.newpipe.report.UserAction.REQUESTED_CHANNEL;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class FeedFragment extends BaseFragment {
    private static final String VIEW_STATE_KEY = "view_state_key";
    private static final String INFO_ITEMS_KEY = "info_items_key";

    private static final int FEED_LOAD_SIZE = 4;
    private static final int LOAD_ITEM_DEBOUNCE_INTERVAL = 500;

    private final String TAG = "FeedFragment@" + Integer.toHexString(hashCode());

    private View inflatedView;
    private View emptyPanel;
    private View loadItemFooter;

    private InfoListAdapter infoListAdapter;
    private RecyclerView resultRecyclerView;

    private Parcelable viewState;
    private AtomicBoolean retainFeedItems;

    private SubscriptionEngine subscriptionEngine;

    private Disposable loadItemObserver;
    private Disposable subscriptionObserver;
    private Subscription feedSubscriber;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        subscriptionEngine = SubscriptionEngine.getInstance(getContext());

        retainFeedItems = new AtomicBoolean(false);

        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(getActivity());
        }

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
                final List<InfoItem> feedInfos = Arrays.asList(infoItems);
                infoListAdapter.addInfoItemList( feedInfos );
            }

            // Already displayed feed items survive configuration changes
            retainFeedItems.set(true);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        if (inflatedView == null) {
            inflatedView = inflater.inflate(R.layout.fragment_subscription, container, false);
        }
        return inflatedView;
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

    @Override
    public void onDestroyView() {
        // Do not monitor for updates when user is not viewing the feed fragment.
        // This is a waste of bandwidth.
        if (loadItemObserver != null) loadItemObserver.dispose();
        if (subscriptionObserver != null) subscriptionObserver.dispose();
        if (feedSubscriber != null) feedSubscriber.cancel();

        loadItemObserver = null;
        subscriptionObserver = null;
        feedSubscriber = null;

        loadItemFooter = null;

        // Retain the already displayed items for backstack pops
        retainFeedItems.set(true);

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        subscriptionEngine = null;

        super.onDestroy();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "], inflater = [" + inflater + "]");
        super.onCreateOptionsMenu(menu, inflater);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
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

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        if (infoListAdapter == null) return;

        animateView(errorPanel, false, 200);
        animateView(loadingProgressBar, true, 200);

        emptyPanel = rootView.findViewById(R.id.empty_panel);

        resultRecyclerView = rootView.findViewById(R.id.result_list_view);
        resultRecyclerView.setLayoutManager(new LinearLayoutManager(activity));

        loadItemFooter = activity.getLayoutInflater().inflate(R.layout.load_item_footer, resultRecyclerView, false);
        infoListAdapter.setFooter(loadItemFooter);
        infoListAdapter.showFooter(false);
        infoListAdapter.setOnStreamInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(int serviceId, String url, String title) {
                NavigationHelper.openVideoDetailFragment(getFragmentManager(), serviceId, url, title);
            }
        });

        resultRecyclerView.setAdapter(infoListAdapter);
        resultRecyclerView.addOnScrollListener(getOnScrollListener());

        if (viewState != null) {
            resultRecyclerView.getLayoutManager().onRestoreInstanceState(viewState);
            viewState = null;
        }

        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle(R.string.fragment_whats_new);

        populateFeed();
    }

    private void resetFragment() {
        if (subscriptionObserver != null) subscriptionObserver.dispose();
        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();
    }

    @Override
    protected void reloadContent() {
        resetFragment();
        populateFeed();
    }

    @Override
    protected void setErrorMessage(String message, boolean showRetryButton) {
        super.setErrorMessage(message, showRetryButton);

        resetFragment();
    }

    /**
     * Changes the state of the load item footer.
     *
     * If the current state of the feed is loaded, this displays the load item button and
     * starts its reactor.
     *
     * Otherwise, show a spinner in place of the loader button. */
    private void setLoader(final boolean isLoaded) {
        if (loadItemFooter == null) return;

        if (loadItemObserver != null) loadItemObserver.dispose();

        if (isLoaded) {
            loadItemObserver = getLoadItemObserver(loadItemFooter);
        }

        loadItemFooter.findViewById(R.id.paginate_progress_bar).setVisibility(isLoaded ? View.GONE : View.VISIBLE);
        loadItemFooter.findViewById(R.id.load_more_text).setVisibility(isLoaded ? View.VISIBLE : View.GONE);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Feeds Loader
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Responsible for reacting to subscription database updates and displaying feeds.
     *
     * Upon each update, the feed info list is cleared unless the fragment is
     * recently recovered from a configuration change or backstack.
     *
     * All existing and pending feed requests are dropped.
     *
     * The newly received list of subscriptions is then transformed into a
     * flowable, reacting to pulling requests.
     *
     * Pulled requests are transformed first into ChannelInfo, then Stream Info items and
     * displayed on the feed fragment.
     **/
    private void populateFeed() {
        final Consumer<List<SubscriptionEntity>> consumer = new Consumer<List<SubscriptionEntity>>() {
            @Override
            public void accept(@NonNull List<SubscriptionEntity> subscriptionEntities) throws Exception {
                animateView(loadingProgressBar, false, 200);

                if (subscriptionEntities.isEmpty()) {
                    infoListAdapter.clearStreamItemList();
                    emptyPanel.setVisibility(View.VISIBLE);
                } else {
                    emptyPanel.setVisibility(View.INVISIBLE);
                }

                // show progress bar on receiving a non-empty updated list of subscriptions
                if (!retainFeedItems.get() && !subscriptionEntities.isEmpty()) {
                    infoListAdapter.clearStreamItemList();
                    animateView(loadingProgressBar, true, 200);
                }

                retainFeedItems.set(false);
                Flowable.fromIterable(subscriptionEntities)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(getSubscriptionObserver());
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable exception) throws Exception {
                onRxError(exception, "Subscription Database Reactor");
            }
        };

        if (subscriptionObserver != null) subscriptionObserver.dispose();
        subscriptionObserver = subscriptionEngine.getSubscription()
                .onErrorReturnItem(Collections.<SubscriptionEntity>emptyList())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(consumer, onError);
    }

    /**
     * Responsible for reacting to user pulling request and starting a request for new feed stream.
     *
     * On initialization, it automatically requests the amount of feed needed to display
     * a minimum amount required (FEED_LOAD_SIZE).
     *
     * Upon receiving a user pull, it creates a Single Observer to fetch the ChannelInfo
     * containing the feed streams.
     **/
    private Subscriber<SubscriptionEntity> getSubscriptionObserver() {
        return new Subscriber<SubscriptionEntity>() {
            @Override
            public void onSubscribe(Subscription s) {
                if (feedSubscriber != null) feedSubscriber.cancel();
                feedSubscriber = s;

                final int requestSize = FEED_LOAD_SIZE - infoListAdapter.getItemsList().size();
                if (requestSize > 0) {
                    requestFeed(requestSize);
                } else {
                    setLoader(true);
                }

                animateView(loadingProgressBar, false, 200);
                // Footer spinner persists until subscription list is exhausted.
                infoListAdapter.showFooter(true);
            }

            @Override
            public void onNext(SubscriptionEntity subscriptionEntity) {
                setLoader(false);

                subscriptionEngine.getChannelInfo(subscriptionEntity)
                        .observeOn(AndroidSchedulers.mainThread())
                        .onErrorComplete()
                        .subscribe(getChannelInfoObserver());
            }

            @Override
            public void onError(Throwable exception) {
                onRxError(exception, "Feed Pull Reactor");
            }

            @Override
            public void onComplete() {
                infoListAdapter.showFooter(false);
            }
        };
    }

    /**
     * On each request, a subscription item from the updated table is transformed
     * into a ChannelInfo, containing the latest streams from the channel.
     *
     * Currently, the feed uses the first into from the list of streams.
     *
     * If chosen feed already displayed, then we request another feed from another
     * subscription, until the subscription table runs out of new items.
     *
     * This Observer is self-contained and will dispose itself when complete. However, this
     * does not obey the fragment lifecycle and may continue running in the background
     * until it is complete. This is done due to RxJava2 no longer propagate errors once
     * an observer is unsubscribed while the thread process is still running.
     *
     * To solve the above issue, we can either set a global RxJava Error Handler, or
     * manage exceptions case by case. This should be done if the current implementation is
     * too costly when dealing with larger subscription sets.
     **/
    private MaybeObserver<ChannelInfo> getChannelInfoObserver() {
        return new MaybeObserver<ChannelInfo>() {
            Disposable observer;
            @Override
            public void onSubscribe(Disposable d) {
                observer = d;
            }

            // Called only when response is non-empty
            @Override
            public void onSuccess(ChannelInfo channelInfo) {
                emptyPanel.setVisibility(View.INVISIBLE);

                if (infoListAdapter == null || channelInfo.related_streams.isEmpty()) return;

                final InfoItem item = channelInfo.related_streams.get(0);
                // Keep requesting new items if the current one already exists
                if (!doesItemExist(infoListAdapter.getItemsList(), item)) {
                    infoListAdapter.addInfoItem(item);
                } else {
                    requestFeed(1);
                }
                onDone();
            }

            @Override
            public void onError(Throwable exception) {
                onRxError(exception, "Feed Display Reactor");
                onDone();
            }

            // Called only when response is empty
            @Override
            public void onComplete() {
                onDone();
            }

            private void onDone() {
                setLoader(true);

                observer.dispose();
                observer = null;
            }
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

    private void requestFeed(final int count) {
        if (feedSubscriber == null) return;

        feedSubscriber.request(count);
    }

    private Disposable getLoadItemObserver(@NonNull final View itemLoader) {
        final Consumer<Object> onNext = new Consumer<Object>() {
            @Override
            public void accept(Object o) throws Exception {
                requestFeed(FEED_LOAD_SIZE);
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                onRxError(throwable, "Load Button Reactor");
            }
        };

        return RxView.clicks(itemLoader)
                .debounce(LOAD_ITEM_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS)
                .subscribe(onNext, onError);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    private void onRxError(final Throwable exception, final String tag) {
        if (exception instanceof IOException) {
            onRecoverableError(R.string.network_error);
        } else {
            onUnrecoverableError(exception, tag);
        }
    }

    private void onRecoverableError(int messageId) {
        if (!this.isAdded()) return;

        if (DEBUG) Log.d(TAG, "onError() called with: messageId = [" + messageId + "]");
        setErrorMessage(getString(messageId), true);
    }

    private void onUnrecoverableError(Throwable exception, final String tag) {
        if (DEBUG) Log.d(TAG, "onUnrecoverableError() called with: exception = [" + exception + "]");
        ErrorActivity.reportError(getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(REQUESTED_CHANNEL, "Feed", tag, R.string.general_error));

        activity.finish();
    }
}
