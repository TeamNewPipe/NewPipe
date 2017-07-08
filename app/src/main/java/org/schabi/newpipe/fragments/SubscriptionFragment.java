package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class SubscriptionFragment extends BaseFragment {

    private InfoListAdapter infoListAdapter;
    private RecyclerView resultRecyclerView;

    /* Used for independent events */
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
        if (disposables != null) disposables.dispose();

        subscriptionService = null;
        disposables = null;

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
            infoListAdapter.setOnChannelInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
                @Override
                public void selected(int serviceId, String url, String title) {
                    /* Requires the parent fragment to find holder for fragment replacement */
                    NavigationHelper.openChannelFragment(getParentFragment().getFragmentManager(), serviceId, url, title);
                }
            });
        }

        resultRecyclerView.setAdapter(infoListAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(resultRecyclerView);

        populateView();
    }

    ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
           return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
            final int pos = viewHolder.getAdapterPosition();
            final InfoItem item = infoListAdapter.getItemsList().remove( pos );

            // TODO: need to update extractor to expose service id
            removeSubscription(item.getLink());
            infoListAdapter.notifyDataSetChanged();
        }
    };

    private void populateView() {
        resetFragment();

        animateView(loadingProgressBar, true, 200);
        animateView(errorPanel, false, 200);

        subscriptionService.getSubscription()
                .toObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionObserver());
    }

    private Observer<Map<SubscriptionEntity, ChannelInfo>> getSubscriptionObserver() {
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

                infoListAdapter.addInfoItemList( getChannelItems(channelInfos) );
                animateView(loadingProgressBar, false, 200);
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

    private List<InfoItem> getChannelItems(Map<SubscriptionEntity, ChannelInfo> channelInfos) {
        List<InfoItem> items = new ArrayList<>();
        for (final Map.Entry<SubscriptionEntity, ChannelInfo> pair: channelInfos.entrySet()) {
            final SubscriptionEntity channel = pair.getKey();
            final ChannelInfo channelInfo = pair.getValue();

            ChannelInfoItem item = new ChannelInfoItem();
            item.webPageUrl = channel.getUrl();
            item.serviceId = channelInfo.service_id;
            item.channelName = channelInfo.channel_name;
            item.thumbnailUrl = channelInfo.avatar_url;
            item.subscriberCount = channelInfo.subscriberCount;

            // TODO: fix this when extractor allows subscription info types
            item.videoAmount = channel.isLatestStreamViewed() ? 1 : -1;
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

    private void removeSubscription(final String url) {
        final Runnable unsubscribe = new Runnable() {
            @Override
            public void run() {
                final SubscriptionDAO subscriptionTable = subscriptionService.subscriptionTable();
                final SubscriptionEntity channel = subscriptionTable.findSingle( url );
                if (channel != null) subscriptionTable.delete( channel );
            }
        };

        final CompletableObserver unsubscriptionObserver = new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {
                disposables.add( d );
            }

            @Override
            public void onComplete() {
                Toast.makeText(getContext(), R.string.channel_unsubscribed, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Subscription Fatal Error: ", e.getCause());
                Toast.makeText(getContext(), R.string.subscription_change_failed, Toast.LENGTH_SHORT).show();
            }
        };

        Completable.fromRunnable(unsubscribe)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(unsubscriptionObserver);
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
        disposables.clear();
        infoListAdapter.clearStreamItemList();
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
}
