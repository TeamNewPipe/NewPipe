package org.schabi.newpipe.playlist;

import android.util.Log;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public final class ChannelPlayQueue extends PlayQueue {
    private final String TAG = "ChannelPlayQueue@" + Integer.toHexString(hashCode());

    private boolean isInitial;
    private boolean isComplete;

    private int serviceId;
    private String baseUrl;
    private String nextUrl;

    private transient Disposable fetchReactor;

    public ChannelPlayQueue(final ChannelInfoItem item) {
        this(item.service_id, item.url, item.url, Collections.<InfoItem>emptyList(), 0);
    }

    public ChannelPlayQueue(final int serviceId,
                            final String url,
                            final String nextPageUrl,
                            final List<InfoItem> streams,
                            final int index) {
        super(index, extractChannelItems(streams));

        this.baseUrl = url;
        this.nextUrl = nextPageUrl;
        this.serviceId = serviceId;

        this.isInitial = streams.isEmpty();
        this.isComplete = !isInitial && (nextPageUrl == null || nextPageUrl.isEmpty());
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public void fetch() {
        if (isInitial) {
            ExtractorHelper.getChannelInfo(this.serviceId, this.baseUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getChannelInitialObserver());
        } else {
            ExtractorHelper.getMoreChannelItems(this.serviceId, this.baseUrl, this.nextUrl)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getChannelNextItemsObserver());
        }
    }

    private SingleObserver<ChannelInfo> getChannelInitialObserver() {
        return new SingleObserver<ChannelInfo>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                if (isComplete || (fetchReactor != null && !fetchReactor.isDisposed())) {
                    d.dispose();
                } else {
                    fetchReactor = d;
                }
            }

            @Override
            public void onSuccess(@NonNull ChannelInfo result) {
                if (!result.has_more_streams) isComplete = true;
                nextUrl = result.next_streams_url;

                append(extractChannelItems(result.related_streams));

                isInitial = false;
                fetchReactor.dispose();
                fetchReactor = null;
            }

            @Override
            public void onError(@NonNull Throwable e) {
                Log.e(TAG, "Error fetching more playlist, marking playlist as complete.", e);
                isComplete = true;
                append(); // Notify change
            }
        };
    }

    private SingleObserver<ListExtractor.NextItemsResult> getChannelNextItemsObserver() {
        return new SingleObserver<ListExtractor.NextItemsResult>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                if (isComplete || (fetchReactor != null && !fetchReactor.isDisposed())) {
                    d.dispose();
                } else {
                    fetchReactor = d;
                }
            }

            @Override
            public void onSuccess(@NonNull ListExtractor.NextItemsResult result) {
                if (!result.hasMoreStreams()) isComplete = true;
                nextUrl = result.nextItemsUrl;

                append(extractChannelItems(result.nextItemsList));

                fetchReactor.dispose();
                fetchReactor = null;
            }

            @Override
            public void onError(@NonNull Throwable e) {
                Log.e(TAG, "Error fetching more playlist, marking playlist as complete.", e);
                isComplete = true;
                append(); // Notify change
            }
        };
    }

    @Override
    public void dispose() {
        super.dispose();
        if (fetchReactor != null) fetchReactor.dispose();
    }

    private static List<PlayQueueItem> extractChannelItems(final List<InfoItem> infos) {
        List<PlayQueueItem> result = new ArrayList<>();
        for (final InfoItem stream : infos) {
            if (stream instanceof StreamInfoItem) {
                result.add(new PlayQueueItem((StreamInfoItem) stream));
            }
        }
        return result;
    }
}
