package org.schabi.newpipe.info_list.holder

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager

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
class CommentsInfoItemHolder(infoItemBuilder: InfoItemBuilder, parent: ViewGroup?) : CommentsMiniInfoItemHolder(infoItemBuilder, R.layout.list_comments_item, parent) {
    val itemTitleView: TextView = itemView.findViewById(R.id.itemTitleView)
    private val itemHeartView: ImageView = itemView.findViewById(R.id.detail_heart_image_view)
    private val itemPinnedView: ImageView = itemView.findViewById(R.id.detail_pinned_view)

    override fun updateFromItem(
        infoItem: InfoItem?,
        historyRecordManager: HistoryRecordManager?
    ) {
        super.updateFromItem(infoItem, historyRecordManager)
        if (infoItem !is CommentsInfoItem) {
            return
        }
        itemTitleView.text = infoItem.uploaderName
        itemHeartView.visibility = if (infoItem.isHeartedByUploader) View.VISIBLE else View.GONE
        itemPinnedView.visibility = if (infoItem.isPinned) View.VISIBLE else View.GONE
    }
}
