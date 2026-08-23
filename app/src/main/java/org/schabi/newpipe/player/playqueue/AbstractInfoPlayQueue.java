package org.schabi.newpipe.player.playqueue;

import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;

abstract class AbstractInfoPlayQueue<T extends ListInfo<StreamInfoItem>>
        extends PlayQueue {
    boolean isInitial;
    private boolean isComplete;

    final int serviceId;
    final String baseUrl;
    Page nextPage;

    private transient Disposable fetchReactor;

    protected AbstractInfoPlayQueue(final T info) {
        this(info.getServiceId(), info.getUrl(), info.getNextPage(), info.getRelatedItems(), 0);
    }

    protected AbstractInfoPlayQueue(final T info, int index) {
        this(info.getServiceId(), info.getUrl(), info.getNextPage(), info.getRelatedItems(), index);
    }

    protected AbstractInfoPlayQueue(final int serviceId,
                                    final String url,
                                    final Page nextPage,
                                    final List<StreamInfoItem> streams,
                                    final int index) {
        super(index, extractListItems(streams));

        this.baseUrl = url;
        this.nextPage = nextPage;
        this.serviceId = serviceId;

        this.isInitial = streams.isEmpty();
        this.isComplete = !isInitial && !Page.isValid(nextPage);
    }

    protected abstract String getTag();

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    SingleObserver<T> getHeadListObserver() {
        return new SingleObserver<>() {
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
                nextPage = result.getNextPage();

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

    SingleObserver<ListExtractor.InfoItemsPage<StreamInfoItem>> getNextPageObserver() {
        return new SingleObserver<>() {
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
            public void onSuccess(
                    @NonNull final ListExtractor.InfoItemsPage<StreamInfoItem> result) {
                if (!result.hasNextPage()) {
                    isComplete = true;
                }
                nextPage = result.getNextPage();

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

    private static List<PlayQueueItem> extractListItems(final List<StreamInfoItem> infoItems) {
        return infoItems.stream().map(PlayQueueItem::new).collect(Collectors.toList());
    }
}
