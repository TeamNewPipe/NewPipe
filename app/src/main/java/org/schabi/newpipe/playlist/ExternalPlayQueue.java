package org.schabi.newpipe.playlist;

import android.util.Log;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
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

public final class ExternalPlayQueue extends PlayQueue {
    private final String TAG = "ExternalPlayQueue@" + Integer.toHexString(hashCode());

    public static final String SERVICE_ID = "service_id";
    public static final String INDEX = "index";
    public static final String STREAMS = "streams";
    public static final String URL = "url";
    public static final String NEXT_PAGE_URL = "next_page_url";

    private static final int RETRY_COUNT = 2;

    private boolean isComplete;

    private int serviceId;
    private String baseUrl;
    private String nextUrl;

    private transient Disposable fetchReactor;

    public ExternalPlayQueue(final int serviceId,
                             final String url,
                             final String nextPageUrl,
                             final List<InfoItem> streams,
                             final int index) {
        super(index, extractPlaylistItems(streams));

        this.baseUrl = url;
        this.nextUrl = nextPageUrl;
        this.serviceId = serviceId;

        this.isComplete = nextPageUrl == null || nextPageUrl.isEmpty();
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public void fetch() {
       ExtractorHelper.getMorePlaylistItems(this.serviceId, this.baseUrl, this.nextUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(RETRY_COUNT)
                .subscribe(getPlaylistObserver());
    }

    private SingleObserver<ListExtractor.NextItemsResult> getPlaylistObserver() {
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

                append(extractPlaylistItems(result.nextItemsList));
            }

            @Override
            public void onError(@NonNull Throwable e) {
                Log.e(TAG, "Error fetching more playlist, marking playlist as complete.", e);
                isComplete = true;
                append(Collections.<PlayQueueItem>emptyList());
            }
        };
    }

    @Override
    public void dispose() {
        super.dispose();
        if (fetchReactor != null) fetchReactor.dispose();
    }

    private static List<PlayQueueItem> extractPlaylistItems(final List<InfoItem> infos) {
        List<PlayQueueItem> result = new ArrayList<>();
        for (final InfoItem stream : infos) {
            if (stream instanceof StreamInfoItem) {
                result.add(new PlayQueueItem((StreamInfoItem) stream));
            }
        }
        return result;
    }
}
