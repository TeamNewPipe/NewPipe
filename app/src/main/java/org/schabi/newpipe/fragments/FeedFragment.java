package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.channel.ChannelDAO;
import org.schabi.newpipe.database.channel.ChannelEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class FeedFragment extends BaseFragment {
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
            public void onNext(List<ChannelEntity> subscriptions) {
                infoListAdapter.clearStreamItemList();
                subscriptionMonitor.clear();
                for (final ChannelEntity subscription: subscriptions) {
                    displayNewVideo( subscription );
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

    private void displayNewVideo(final ChannelEntity subscription) {
        final int serviceId = subscription.getServiceId();
        final String url = subscription.getLastVideoId();

        final StreamingService service = getService( serviceId );

        if (service != null) {
            final Callable<StreamInfo> infoCallable = new Callable<StreamInfo>() {
                @Override
                public StreamInfo call() throws Exception {
                    final StreamExtractor extractor = service.getExtractorInstance(url);
                    return StreamInfo.getVideoInfo(extractor);
                }
            };

            Single.fromCallable(infoCallable)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(singleExtractionObserver(subscription));
        }
    }

    private SingleObserver<StreamInfo> singleExtractionObserver(final ChannelEntity subscription) {
        return new SingleObserver<StreamInfo>() {
            @Override
            public void onSubscribe(Disposable d) {
                subscriptionMonitor.add( d );
            }

            @Override
            public void onSuccess(StreamInfo videoInfo) {
                StreamInfoItem item = new StreamInfoItem();
                item.webpage_url = videoInfo.webpage_url;
                item.duration = videoInfo.duration;
                item.service_id = videoInfo.service_id;
                item.view_count = videoInfo.view_count;
                item.stream_type = videoInfo.stream_type;
                item.thumbnail_url = videoInfo.thumbnail_url;
                item.title = videoInfo.title;
                item.upload_date = videoInfo.upload_date;
                item.uploader = videoInfo.uploader;
                item.id = videoInfo.id;

                infoListAdapter.addInfoItem( item );
            }

            @Override
            public void onError(Throwable exception) {
                if (exception instanceof IOException) {
                    onRecoverableError(R.string.network_error);
                } else if (exception instanceof ParsingException || exception instanceof ExtractionException) {
                    ErrorActivity.reportError(getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(
                            UserAction.REQUESTED_CHANNEL, getServiceName(subscription.getServiceId()), subscription.getUrl(), R.string.parsing_error
                    ));

                    onUnrecoverableError(exception);
                } else {
                    ErrorActivity.reportError(getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(
                            UserAction.REQUESTED_CHANNEL, getServiceName(subscription.getServiceId()), subscription.getUrl(), R.string.general_error
                    ));

                    onUnrecoverableError(exception);
                }
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
}
