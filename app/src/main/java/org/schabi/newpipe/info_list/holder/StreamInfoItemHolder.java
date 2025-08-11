package org.schabi.newpipe.info_list.holder;

import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.Localization;

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p?
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class StreamInfoItemHolder extends StreamMiniInfoItemHolder {
    public final TextView itemAdditionalDetails;

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_item, parent);
    }

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                                final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (infoItem instanceof StreamInfoItem item) {
            itemAdditionalDetails.setText(getStreamInfoDetailLine(item));
        }
    }

    private String getStreamInfoDetailLine(final StreamInfoItem infoItem) {
        final var context = itemBuilder.getContext();
        final long count = infoItem.getViewCount();
        final String views;
        if (count >= 0) {
            views = switch (infoItem.getStreamType()) {
                case LIVE_STREAM -> Localization.formatWatchingCount(context, count);
                case AUDIO_LIVE_STREAM -> Localization.formatListeningCount(context, count);
                default -> Localization.formatViewCount(context, count);
            };
        } else {
            views = "";
        }

        final var uploadDate = Localization.relativeTimeOrTextual(context, infoItem.getUploadDate(),
                infoItem.getTextualUploadDate());
        if (!TextUtils.isEmpty(uploadDate)) {
            if (views.isEmpty()) {
                return uploadDate;
            }

            return Localization.concatenateStrings(views, uploadDate);
        }

        return views;
    }
}
