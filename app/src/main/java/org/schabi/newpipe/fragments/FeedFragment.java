package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class FeedFragment extends BaseFragment {
    private InfoListAdapter infoListAdapter;
    private RecyclerView resultRecyclerView;


    /* Used for subscription following fragment lifecycle */
    private CompositeDisposable disposables;
    private SubscriptionService subscriptionService;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscription, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        disposables = new CompositeDisposable();
        subscriptionService = SubscriptionService.getInstance( getContext() );
    }


    @Override
    public void onDestroy() {
        subscriptionService = null;
        disposables.dispose();

        super.onDestroy();
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

        populateView();
    }

    private void populateView() {
        resetFragment();

        animateView(loadingProgressBar, true, 200);
        animateView(errorPanel, false, 200);

        subscriptionService.getSubscription()
                .toObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getFeedObserver());
    }

    private Observer<Map<SubscriptionEntity, ChannelInfo>> getFeedObserver() {
        return new Observer<Map<SubscriptionEntity, ChannelInfo>>() {
            @Override
            public void onSubscribe(Disposable d) {
                disposables.add( d );
                animateView(loadingProgressBar, true, 200);
                subscriptionService.getSubscription().connect();
            }

            @Override
            public void onNext(Map<SubscriptionEntity, ChannelInfo> channelInfos) {
                infoListAdapter.clearStreamItemList();
                animateView(loadingProgressBar, true, 200);

                List<InfoItem> items = new ArrayList<>();
                for (final Map.Entry<SubscriptionEntity, ChannelInfo> pair: channelInfos.entrySet()) {
                    final ChannelInfo channelInfo = pair.getValue();

                    if (!channelInfo.related_streams.isEmpty()) {
                        items.add( channelInfo.related_streams.get( 0 ));
                    }
                }

                animateView(loadingProgressBar, false, 200);
                infoListAdapter.addInfoItemList( items );
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

    private void onRecoverableError(int messageId) {
        if (!this.isAdded()) return;

        if (DEBUG) Log.d(TAG, "onError() called with: messageId = [" + messageId + "]");
        setErrorMessage(getString(messageId), true);
    }

    private void onUnrecoverableError(Throwable exception) {
        if (DEBUG) Log.d(TAG, "onUnrecoverableError() called with: exception = [" + exception + "]");
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
