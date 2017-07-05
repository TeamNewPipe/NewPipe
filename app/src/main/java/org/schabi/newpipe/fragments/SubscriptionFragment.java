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

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.channel.ChannelDAO;
import org.schabi.newpipe.database.channel.ChannelEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class SubscriptionFragment extends BaseFragment {

    private InfoListAdapter infoListAdapter;
    private RecyclerView resultRecyclerView;

    /* Used for tracking subscription list items */
    private CompositeDisposable subscriptionMonitor;
    /* Used for independent events */
    private CompositeDisposable disposables;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscription, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        subscriptionMonitor = new CompositeDisposable();
        disposables = new CompositeDisposable();
    }

    @Override
    public void onDestroy() {
        disposables.dispose();
        subscriptionMonitor.dispose();

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

            removeSubscription(item.getLink());
            infoListAdapter.notifyDataSetChanged();
        }
    };

    private void removeSubscription(final String url) {
        final Runnable unsubscribe = new Runnable() {
            @Override
            public void run() {
                final ChannelEntity channel = subscriptionTable().findByUrl( url );
                subscriptionTable().delete( channel );
            }
        };

        final CompletableObserver unsubscriptionObserver = new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {
                disposables.add( d );
            }

            @Override
            public void onComplete() {
                Toast.makeText(getContext(), "Channel unsubscribed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Subscription Fatal Error: ", e.getCause());
                Toast.makeText(getContext(), "Unable to unsubscribe", Toast.LENGTH_SHORT).show();
            }
        };

        Completable.fromRunnable( unsubscribe )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(unsubscriptionObserver);
    }

    private void populateView() {
        animateView(errorPanel, false, 200);

        /* Backpressure not expected here, switch to observable */
        subscriptionTable().findAll().toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriptionObserver());
    }

    private Observer<List<ChannelEntity>> subscriptionObserver() {
        return new Observer<List<ChannelEntity>>() {
            @Override
            public void onSubscribe(Disposable d) {
                disposables.add( d );
            }

            @Override
            public void onNext(List<ChannelEntity> channels) {
                infoListAdapter.clearStreamItemList();
                subscriptionMonitor.clear();
                for (final ChannelEntity channel: channels) {
                    displayChannel( channel );
                }
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Subscription Retrieval Error: ", e);
            }

            @Override
            public void onComplete() {}
        };
    }

    private void displayChannel(final ChannelEntity channel) {
        final int serviceId = channel.getServiceId();
        final String url = channel.getUrl();

        final StreamingService service = getService( serviceId );

        if (service != null) {
            final Callable<ChannelInfo> infoCallable = new Callable<ChannelInfo>() {
                @Override
                public ChannelInfo call() throws Exception {
                    final ChannelExtractor extractor = service.getChannelExtractorInstance(url, 0);
                    return ChannelInfo.getInfo(extractor);
                }
            };

            Single.fromCallable(infoCallable)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(singleExtractionObserver(channel));
        }
    }

    private SingleObserver<ChannelInfo> singleExtractionObserver(final ChannelEntity channel) {
        return new SingleObserver<ChannelInfo>() {
            @Override
            public void onSubscribe(Disposable d) {
                subscriptionMonitor.add( d );
            }

            @Override
            public void onSuccess(ChannelInfo channelInfo) {
                ChannelInfoItem item = new ChannelInfoItem();
                item.webPageUrl = channel.getUrl();
                item.serviceId = channelInfo.service_id;
                item.channelName = channelInfo.channel_name;
                item.thumbnailUrl = channelInfo.avatar_url;
                item.subscriberCount = channelInfo.subscriberCount;

                // TODO: fix this when extractor allows subscription info types
                if (!channelInfo.related_streams.isEmpty() &&
                        !channelInfo.related_streams.get(0).getLink().equals(channel.getLastVideoId())) {

                    updateLatest( channel, channelInfo.related_streams.get(0).getLink() );
                    item.videoAmount = 1;
                } else {
                    item.videoAmount = channel.isLastVideoViewed() ? 1 : -1;
                }
                infoListAdapter.addInfoItem( item );
            }

            @Override
            public void onError(Throwable exception) {
                if (exception instanceof IOException) {
                    onRecoverableError(R.string.network_error);
                } else if (exception instanceof ParsingException || exception instanceof ExtractionException) {
                    ErrorActivity.reportError(getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(
                            UserAction.REQUESTED_CHANNEL, getServiceName(channel.getServiceId()), channel.getUrl(), R.string.parsing_error
                    ));

                    onUnrecoverableError(exception);
                } else {
                    ErrorActivity.reportError(getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(
                            UserAction.REQUESTED_CHANNEL, getServiceName(channel.getServiceId()), channel.getUrl(), R.string.general_error
                    ));

                    onUnrecoverableError(exception);
                }
            }
        };
    }

    private void updateLatest(final ChannelEntity channel, final String url) {
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                channel.setLastVideoId( url );
                channel.setLastVideoViewed( false );
                subscriptionTable().update(channel);
            }
        };

        final CompletableObserver updateObserver = new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {
                subscriptionMonitor.add( d );
            }

            @Override
            public void onComplete() {}

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Subscription Fatal Error: ", e.getCause());
            }
        };

        Completable.fromRunnable(update)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(updateObserver);
    }

    @Override
    protected void reloadContent() {
        populateView();
    }

    @Override
    protected void setErrorMessage(String message, boolean showRetryButton) {
        super.setErrorMessage(message, showRetryButton);

        disposables.clear();
        subscriptionMonitor.clear();
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

    // TODO: refactor this to a common util
    private String getServiceName(final int serviceId) {
        final StreamingService service = getService( serviceId );
        return (service != null) ? service.getServiceInfo().name : "none";
    }

    private StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }

    private ChannelDAO subscriptionTable() {
        return NewPipeDatabase.getInstance( getContext() ).channelDAO();
    }
}
