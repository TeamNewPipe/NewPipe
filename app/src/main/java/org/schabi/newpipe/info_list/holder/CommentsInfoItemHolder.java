package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.ktx.TextViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

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

    public CommentsInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_comments_item, parent);

        itemTitleView = itemView.findViewById(R.id.itemTitleView);
    }

    @NonNull
    @Override
    public Disposable updateFromItem(final InfoItem infoItem,
                                     final HistoryRecordManager historyRecordManager) {
        final Disposable disposable = super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof CommentsInfoItem)) {
            return disposable;
        }
        final CommentsInfoItem item = (CommentsInfoItem) infoItem;

        return new CompositeDisposable(
                disposable,
                TextViewUtils.computeAndSetPrecomputedText(itemTitleView, item.getUploaderName())
        );
    }
}
