package org.schabi.newpipe.info_list.holder

import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization

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
open class StreamInfoItemHolder(infoItemBuilder: InfoItemBuilder, layoutId: Int,
                                parent: ViewGroup?) : StreamMiniInfoItemHolder(infoItemBuilder, layoutId, parent) {
    val itemAdditionalDetails: TextView

    constructor(infoItemBuilder: InfoItemBuilder, parent: ViewGroup?) : this(infoItemBuilder, R.layout.list_stream_item, parent)

    init {
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails)
    }

    public override fun updateFromItem(infoItem: InfoItem?,
                                       historyRecordManager: HistoryRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager)
        if (!(infoItem is StreamInfoItem)) {
            return
        }
        itemAdditionalDetails.setText(getStreamInfoDetailLine(infoItem))
    }

    private fun getStreamInfoDetailLine(infoItem: StreamInfoItem): String? {
        var viewsAndDate: String? = ""
        if (infoItem.getViewCount() >= 0) {
            if ((infoItem.getStreamType() == StreamType.AUDIO_LIVE_STREAM)) {
                viewsAndDate = Localization.listeningCount(itemBuilder.getContext(), infoItem.getViewCount())
            } else if ((infoItem.getStreamType() == StreamType.LIVE_STREAM)) {
                viewsAndDate = Localization.shortWatchingCount(itemBuilder.getContext(), infoItem.getViewCount())
            } else {
                viewsAndDate = Localization.shortViewCount(itemBuilder.getContext(), infoItem.getViewCount())
            }
        }
        val uploadDate: String? = Localization.relativeTimeOrTextual(itemBuilder.getContext(),
                infoItem.getUploadDate(),
                infoItem.getTextualUploadDate())
        if (!TextUtils.isEmpty(uploadDate)) {
            if (viewsAndDate!!.isEmpty()) {
                return uploadDate
            }
            return Localization.concatenateStrings(viewsAndDate, uploadDate)
        }
        return viewsAndDate
    }
}
