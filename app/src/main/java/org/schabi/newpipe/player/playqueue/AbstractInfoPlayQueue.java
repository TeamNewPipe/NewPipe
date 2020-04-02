package org.schabi.newpipe.player.playqueue;

import android.util.Log;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.SingleObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

abstract class AbstractInfoPlayQueue<T extends ListInfo, U extends InfoItem> extends PlayQueue {
    boolean isInitial;
    private boolean isComplete;

    final int serviceId;
    final String baseUrl;
    String nextUrl;

    private transient Disposable fetchReactor;

    AbstractInfoPlayQueue(final U item) {
        this(item.getServiceId(), item.getUrl(), null, Collections.emptyList(), 0);
    }

    AbstractInfoPlayQueue(final int serviceId, final String url, final String nextPageUrl,
                          final List<StreamInfoItem> streams, final int index) {
        super(index, extractListItems(streams));

        this.baseUrl = url;
        this.nextUrl = nextPageUrl;
        this.serviceId = serviceId;

        this.isInitial = streams.isEmpty();
        this.isComplete = !isInitial && (nextPageUrl == null || nextPageUrl.isEmpty());
    }

    protected abstract String getTag();

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    SingleObserver<T> getHeadListObserver() {
        return new SingleObserver<T>() {
            @Override
            public void onSubscribe(@NonNull final Disposable d) {
                if (isComplete || !isInitial || (fetchReactor != null
                        && !fetchReactor.isDisposed())) {
                    d.dispose();
                } else {
                    fetchReactor = d;
                }
            }

            @Override
            public void onSuccess(@NonNull final T result) {
                isInitial = false;
                if (!result.hasNextPage()) {
                    isComplete = true;
                }
                nextUrl = result.getNextPageUrl();

                append(extractListItems(result.getRelatedItems()));

                fetchReactor.dispose();
                fetchReactor = null;
            }

            @Override
            public void onError(@NonNull final Throwable e) {
                Log.e(getTag(), "Error fetching more playlist, marking playlist as complete.", e);
                isComplete = true;
                append(); // Notify change
            }
        };
    }

    SingleObserver<ListExtractor.InfoItemsPage> getNextPageObserver() {
        return new SingleObserver<ListExtractor.InfoItemsPage>() {
            @Override
            public void onSubscribe(@NonNull final Disposable d) {
                if (isComplete || isInitial || (fetchReactor != null
                        && !fetchReactor.isDisposed())) {
                    d.dispose();
                } else {
                    fetchReactor = d;
                }
            }

            @Override
            public void onSuccess(@NonNull final ListExtractor.InfoItemsPage result) {
                if (!result.hasNextPage()) {
                    isComplete = true;
                }
                nextUrl = result.getNextPageUrl();

                append(extractListItems(result.getItems()));

                fetchReactor.dispose();
                fetchReactor = null;
            }

            @Override
            public void onError(@NonNull final Throwable e) {
                Log.e(getTag(), "Error fetching more playlist, marking playlist as complete.", e);
                isComplete = true;
                append(); // Notify change
            }
        };
    }

    @Override
    public void dispose() {
        super.dispose();
        if (fetchReactor != null) {
            fetchReactor.dispose();
        }
        fetchReactor = null;
    }

    private static List<PlayQueueItem> extractListItems(final List<StreamInfoItem> infos) {
        List<PlayQueueItem> result = new ArrayList<>();
        for (final InfoItem stream : infos) {
            if (stream instanceof StreamInfoItem) {
                result.add(new PlayQueueItem((StreamInfoItem) stream));
            }
        }
        return result;
    }
}
