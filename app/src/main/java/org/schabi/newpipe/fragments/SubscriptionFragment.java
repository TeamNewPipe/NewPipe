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

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.report.UserAction.REQUESTED_CHANNEL;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class SubscriptionFragment extends BaseFragment {
    private static final String VIEW_STATE_KEY = "view_state_key";
    private final String TAG = "SubscriptionFragment@" + Integer.toHexString(hashCode());

    private View inflatedView;
    private View emptyPanel;
    private View headerRootLayout;
    private View whatsNewView;

    private InfoListAdapter infoListAdapter;
    private RecyclerView resultRecyclerView;
    private Parcelable viewState;

    /* Used for independent events */
    private CompositeDisposable disposables;
    private SubscriptionService subscriptionService;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        disposables = new CompositeDisposable();
        subscriptionService = SubscriptionService.getInstance( getContext() );

        if (savedInstanceState != null) {
            viewState = savedInstanceState.getParcelable(VIEW_STATE_KEY);
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

        outState.putParcelable(VIEW_STATE_KEY, viewState);
    }

    @Override
    public void onDestroyView() {
        if (disposables != null) disposables.clear();

        headerRootLayout = null;
        whatsNewView = null;

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (disposables != null) disposables.dispose();
        disposables = null;

        subscriptionService = null;

        super.onDestroy();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Views
    ///////////////////////////////////////////////////////////////////////////

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

    private View.OnClickListener getWhatsNewOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavigationHelper.openWhatsNewFragment(getParentFragment().getFragmentManager());
            }
        };
    }

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        emptyPanel = rootView.findViewById(R.id.empty_panel);

        resultRecyclerView = rootView.findViewById(R.id.result_list_view);
        resultRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        resultRecyclerView.addOnScrollListener(getOnScrollListener());

        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(getActivity());
            infoListAdapter.setFooter(activity.getLayoutInflater().inflate(R.layout.pignate_footer, resultRecyclerView, false));
            infoListAdapter.showFooter(false);
            infoListAdapter.setOnChannelInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
                @Override
                public void selected(int serviceId, String url, String title) {
                    /* Requires the parent fragment to find holder for fragment replacement */
                    NavigationHelper.openChannelFragment(getParentFragment().getFragmentManager(), serviceId, url, title);
                }
            });
        }

        headerRootLayout = activity.getLayoutInflater().inflate(R.layout.subscription_header, resultRecyclerView, false);
        infoListAdapter.setHeader(headerRootLayout);

        whatsNewView = headerRootLayout.findViewById(R.id.whatsNew);
        whatsNewView.setOnClickListener(getWhatsNewOnClickListener());

        resultRecyclerView.setAdapter(infoListAdapter);

        populateView();
    }

    @Override
    protected void reloadContent() {
        populateView();
    }

    @Override
    protected void setErrorMessage(String message, boolean showRetryButton) {
        super.setErrorMessage(message, showRetryButton);
        resetFragment();
    }

    private void resetFragment() {
        if (disposables != null) disposables.clear();
        if (infoListAdapter != null) infoListAdapter.clearStreamItemList();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private void populateView() {
        resetFragment();

        animateView(loadingProgressBar, true, 200);
        animateView(errorPanel, false, 200);

        subscriptionService.getSubscription().toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionObserver());
    }

    private Observer<List<SubscriptionEntity>> getSubscriptionObserver() {
        return new Observer<List<SubscriptionEntity>>() {
            @Override
            public void onSubscribe(Disposable d) {
                animateView(loadingProgressBar, true, 200);

                disposables.add( d );
            }

            @Override
            public void onNext(List<SubscriptionEntity> subscriptions) {
                animateView(loadingProgressBar, true, 200);

                infoListAdapter.clearStreamItemList();
                infoListAdapter.addInfoItemList( getSubscriptionItems(subscriptions) );

                animateView(loadingProgressBar, false, 200);

                emptyPanel.setVisibility(subscriptions.isEmpty() ? View.VISIBLE : View.INVISIBLE);

                if (viewState != null && resultRecyclerView != null) {
                    resultRecyclerView.getLayoutManager().onRestoreInstanceState(viewState);
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
            public void onComplete() {

            }
        };
    }

    private List<InfoItem> getSubscriptionItems(List<SubscriptionEntity> subscriptions) {
        List<InfoItem> items = new ArrayList<>();
        for (final SubscriptionEntity subscription: subscriptions) {
            ChannelInfoItem item = new ChannelInfoItem();
            item.webPageUrl = subscription.getUrl();
            item.serviceId = subscription.getServiceId();
            item.channelName = subscription.getTitle();
            item.thumbnailUrl = subscription.getThumbnailUrl();
            item.subscriberCount = subscription.getSubscriberCount();
            item.description = subscription.getDescription();

            items.add( item );
        }
        Collections.sort(items, new Comparator<InfoItem>() {
            @Override
            public int compare(InfoItem o1, InfoItem o2) {
                return o1.getTitle().compareToIgnoreCase(o2.getTitle());
            }
        });

        return items;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

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
}
