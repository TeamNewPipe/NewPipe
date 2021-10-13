package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.local.history.HistoryRecordManager;

import java.util.List;
import java.util.Objects;

/*
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelInfoItemHolder .java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class CommentsInfoItemHolder extends CommentsMiniInfoItemHolder {
    public final TextView itemTitleView;
    private final ImageView itemHeartView;
    private final ImageView itemPinnedView;

    private final TextView showReplies;
    private final RecyclerView repliesView;

    public CommentsInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_comments_item, parent);

        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemHeartView = itemView.findViewById(R.id.detail_heart_image_view);
        itemPinnedView = itemView.findViewById(R.id.detail_pinned_view);
        showReplies = itemView.findViewById(R.id.showReplies);
        repliesView = itemView.findViewById(R.id.replyRecycleView);
        repliesView.setAdapter(new InfoListAdapter(repliesView.getContext()));
        repliesView.setLayoutManager(new LinearLayoutManager(repliesView.getContext()));
    }

    public void addReplies(final View buttonView, final CommentsInfoItem comment) {

        final List<CommentsInfoItem> replies = comment.getRepliesInfoList();
        ((InfoListAdapter) Objects.requireNonNull(repliesView.getAdapter()))
                .setInfoItemList(replies);

        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) repliesView.getLayoutParams();
        params.topMargin = 45;

        repliesView.setMinimumHeight(100);
        repliesView.setHasFixedSize(true);
        buttonView.setVisibility(View.GONE);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof CommentsInfoItem)) {
            return;
        }
        final CommentsInfoItem item = (CommentsInfoItem) infoItem;

        itemTitleView.setText(item.getUploaderName());

        itemHeartView.setVisibility(item.isHeartedByUploader() ? View.VISIBLE : View.GONE);
        itemPinnedView.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
        if (item.getReplies() == null) {
            showReplies.setVisibility(View.GONE);
        } else {
            showReplies.setVisibility(View.VISIBLE);
            showReplies.setOnClickListener(v -> addReplies(v, item));
        }
    }
}
