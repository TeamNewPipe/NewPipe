/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.tv.util.TvNavigationHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TvCommentsDialog extends DialogFragment {

    private static final String TAG = "TvCommentsDialog";

    private int serviceId;
    private String url;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<CommentsInfoItem> commentsList = new ArrayList<>();
    private CommentsAdapter adapter;

    public static TvCommentsDialog newInstance(final int serviceId, @NonNull final String url) {
        final TvCommentsDialog dialog = new TvCommentsDialog();
        final Bundle args = new Bundle();
        args.putInt(TvNavigationHelper.KEY_SERVICE_ID, serviceId);
        args.putString(TvNavigationHelper.KEY_URL, url);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_NewPipe_Tv);

        final Bundle args = getArguments();
        if (args != null) {
            serviceId = args.getInt(TvNavigationHelper.KEY_SERVICE_ID, 0);
            url = args.getString(TvNavigationHelper.KEY_URL, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_tv_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final VerticalGridView gridView = view.findViewById(R.id.tv_comments_list);
        adapter = new CommentsAdapter(commentsList);
        gridView.setAdapter(adapter);

        loadComments();
    }

    private void loadComments() {
        if (url == null || url.isEmpty()) {
            return;
        }

        disposables.add(ExtractorHelper.getCommentsInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(commentsInfo -> {
                    commentsList.clear();
                    for (final Object item : commentsInfo.getRelatedItems()) {
                        if (item instanceof CommentsInfoItem) {
                            commentsList.add((CommentsInfoItem) item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                }, throwable -> Log.e(TAG, "Error loading comments", throwable)));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    private static class CommentsAdapter extends RecyclerView.Adapter<CommentViewHolder> {
        private final List<CommentsInfoItem> items;

        CommentsAdapter(final List<CommentsInfoItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                     final int viewType) {
            final View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tv_comment, parent, false);
            return new CommentViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final CommentViewHolder holder, final int position) {
            final CommentsInfoItem comment = items.get(position);
            holder.authorView.setText(comment.getUploaderName());
            holder.dateView.setText(comment.getTextualUploadDate());
            if (comment.getCommentText() != null && comment.getCommentText().content() != null) {
                holder.textView.setText(comment.getCommentText().content());
            }
            if (!comment.getUploaderAvatars().isEmpty()) {
                CoilHelper.INSTANCE.loadAvatar(holder.avatarView, comment.getUploaderAvatars());
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class CommentViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatarView;
        final TextView authorView;
        final TextView dateView;
        final TextView textView;

        CommentViewHolder(@NonNull final View itemView) {
            super(itemView);
            avatarView = itemView.findViewById(R.id.tv_comment_avatar);
            authorView = itemView.findViewById(R.id.tv_comment_author);
            dateView = itemView.findViewById(R.id.tv_comment_date);
            textView = itemView.findViewById(R.id.tv_comment_text);
        }
    }
}
