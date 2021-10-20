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

import io.reactivex.rxjava3.core.Single;
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


    public void addRepliesToUI(final CommentsInfoItem parentInfoItem) {
        ((InfoListAdapter) Objects.requireNonNull(repliesView.getAdapter()))
                .setInfoItemList(cachedReplies);

        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) repliesView.getLayoutParams();
        params.topMargin = 45;

        repliesView.setMinimumHeight(100);
        repliesView.setHasFixedSize(true);
        parentInfoItem.setRepliesOpen(true);
        showReplies.setVisibility(View.GONE);
        repliesView.setVisibility(View.VISIBLE);
    }

    @SuppressLint("SetTextI18n") // Testing purposes
    public void downloadReplies(final CommentsInfoItem parentInfoItem) {
        showReplies.setText("Loading...");

        final Single<CommentsInfo> parentInfoSingle = ExtractorHelper.getCommentsInfo(
                parentInfoItem.getServiceId(),
                parentInfoItem.getUrl(),
                false
        );

        final CommentsInfo infoListInfo = parentInfoSingle.blockingGet();

        final GetMoreItemsCallable getMoreItems = new GetMoreItemsCallable();
        getMoreItems.setCallableParameters(infoListInfo, parentInfoItem);
        final Single<ListExtractor.InfoItemsPage<CommentsInfoItem>>
                getItemsSingle = Single.fromCallable(getMoreItems);

        final ListExtractor.InfoItemsPage<CommentsInfoItem> infoItemsPageList = getItemsSingle
                .subscribeOn(Schedulers.newThread())
                .blockingGet(); // It works, just isn't great

        final List<CommentsInfoItem> actualList = infoItemsPageList.getItems();
        cachedReplies.addAll(actualList);
        addRepliesToUI(parentInfoItem);
    }

    public void addReplies(final CommentsInfoItem parentInfoItem) {
        if (parentInfoItem.getReplies() == null) {
            return;
        }

        if (cachedReplies.isEmpty()) {
            downloadReplies(parentInfoItem);
        } else {
            addRepliesToUI(parentInfoItem);
        }
    }

    public void checkForReplies(final CommentsInfoItem item) {
        if (item.getReplies() == null) {
            repliesView.setVisibility(View.GONE);
            showReplies.setVisibility(View.GONE);
        } else if (item.getRepliesOpen()) {
            addReplies(item);
        } else {
            repliesView.setVisibility(View.GONE);
            showReplies.setVisibility(View.VISIBLE);
            showReplies.setOnClickListener(v -> addReplies(item));
        }
    }
}
