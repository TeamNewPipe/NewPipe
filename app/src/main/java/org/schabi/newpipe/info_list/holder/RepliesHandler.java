package org.schabi.newpipe.info_list.holder;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RepliesHandler {
    private final List<CommentsInfoItem> cachedReplies;
    private final TextView showReplies;
    private final RecyclerView repliesView;

    public RepliesHandler(final TextView showReplies, final RecyclerView recyclerView) {
        this.repliesView = recyclerView;
        repliesView.setAdapter(new InfoListAdapter(repliesView.getContext()));
        repliesView.setLayoutManager(new LinearLayoutManager(repliesView.getContext()));

        this.showReplies = showReplies;
        this.cachedReplies = new ArrayList<>();
    }

    static class GetMoreItemsCallable implements
            Callable<ListExtractor.InfoItemsPage<CommentsInfoItem>> {
        private CommentsInfo parentCommentInfo;
        private CommentsInfoItem parentInfoItem;

        public void setCallableParameters(
                final CommentsInfo commentInfo, final CommentsInfoItem infoItem) {
            parentCommentInfo = commentInfo;
            parentInfoItem = infoItem;
        }

        @Override
        public ListExtractor.InfoItemsPage<CommentsInfoItem> call() throws Exception {
            return CommentsInfo.getMoreItems(parentCommentInfo, parentInfoItem.getReplies());
        }
    }

    private Single<ListExtractor.InfoItemsPage<CommentsInfoItem>>
    repliesSingle(final CommentsInfo parentCommentInfo,
                      final CommentsInfoItem parentInfoItem) {

        final GetMoreItemsCallable getMoreItems = new GetMoreItemsCallable();
        getMoreItems.setCallableParameters(parentCommentInfo, parentInfoItem);
        return Single.fromCallable(getMoreItems);

    }

    private SingleObserver<ListExtractor.InfoItemsPage<CommentsInfoItem>>
    repliesObserver(final CommentsInfoItem parentInfoItem) {

        return new SingleObserver<ListExtractor.InfoItemsPage<CommentsInfoItem>>() {

            @SuppressLint("SetTextI18n")
            @Override
            public void onSubscribe(@NonNull final Disposable d) {
                showReplies.setText("Collapse");
            }

            @Override
            public void onSuccess(@NonNull final
                                  ListExtractor.InfoItemsPage<CommentsInfoItem>
                                          commentsInfoItemInfoItemsPage) {

                final List<CommentsInfoItem> actualList
                        = commentsInfoItemInfoItemsPage.getItems();

                cachedReplies.addAll(actualList);
                addRepliesToUI(parentInfoItem);
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onError(@NonNull final Throwable e) {
                showReplies.setText("Error getting replies");
            }
        };
    }

    private SingleObserver<CommentsInfo>
        repliesInfoObserver(final CommentsInfoItem parentInfoItem) {

        return new SingleObserver<CommentsInfo>() {
            @Override

            public void onSubscribe(@NonNull final Disposable d) {

            }
            @Override
            public void onSuccess(@NonNull final CommentsInfo commentsInfo) {
                final Single<ListExtractor.InfoItemsPage<CommentsInfoItem>>
                        getRepliesInfoSingle =
                        repliesSingle(commentsInfo, parentInfoItem);

                final SingleObserver<ListExtractor.InfoItemsPage<CommentsInfoItem>>
                        getRepliesInfoObserver = repliesObserver(parentInfoItem);

                getRepliesInfoSingle
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(getRepliesInfoObserver);
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onError(@NonNull final Throwable e) {
                showReplies.setText("Error getting replies");
            }
        };
    }


    public void addRepliesToUI(final CommentsInfoItem parentInfoItem) {
        ((InfoListAdapter) Objects.requireNonNull(repliesView.getAdapter()))
                .setInfoItemList(cachedReplies);

        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) repliesView.getLayoutParams();
        params.topMargin = 45;

        repliesView.setMinimumHeight(100);
        repliesView.setHasFixedSize(true);
        repliesView.setVisibility(View.VISIBLE);
    }

    public void downloadReplies(final CommentsInfoItem parentInfoItem) {

        final Single<CommentsInfo> parentInfoSingle = ExtractorHelper.getCommentsInfo(
                parentInfoItem.getServiceId(),
                parentInfoItem.getUrl(),
                false
        );

        final SingleObserver<CommentsInfo> singleInfoRepliesInfoObserver
                = repliesInfoObserver(parentInfoItem);

        parentInfoSingle
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(singleInfoRepliesInfoObserver);
    }

    public void addReplies(final CommentsInfoItem parentInfoItem) {
        if (parentInfoItem.getReplies() == null) {
            return;
        }

        if (cachedReplies.isEmpty()) {
            downloadReplies(parentInfoItem);
             addRepliesToUI(parentInfoItem);
             showReplies.setText("Collapse");
        } else {
            cachedReplies.clear();
            repliesView.setVisibility(View.GONE);
            showReplies.setText("Show Replies");
        }
    }

    public void checkForReplies(final CommentsInfoItem item) {
        if (item.getReplies() == null) {
            repliesView.setVisibility(View.GONE);
            showReplies.setVisibility(View.GONE);
        } else {
            repliesView.setVisibility(View.GONE);
            showReplies.setVisibility(View.VISIBLE);
            showReplies.setOnClickListener(v -> addReplies(item));
        }
    }
}
