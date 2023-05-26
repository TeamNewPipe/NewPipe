package org.schabi.newpipe.info_list.holder

import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import org.schabi.newpipe.MainActivity
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
open class StreamInfoItemHolder(
    infoItemBuilder: InfoItemBuilder?,
    layoutId: Int,
    parent: ViewGroup?
) : StreamMiniInfoItemHolder(infoItemBuilder, layoutId, parent) {
    val itemAdditionalDetails: TextView = itemView.findViewById(R.id.itemAdditionalDetails)

    constructor(infoItemBuilder: InfoItemBuilder?, parent: ViewGroup?) : this(infoItemBuilder, R.layout.list_stream_item, parent) {}

    override fun updateFromItem(
        infoItem: InfoItem?,
        historyRecordManager: HistoryRecordManager?
    ) {
        super.updateFromItem(infoItem, historyRecordManager)
        if (infoItem !is StreamInfoItem) {
            return
        }
        itemAdditionalDetails.text = getStreamInfoDetailLine(infoItem)
    }

    private fun getStreamInfoDetailLine(infoItem: StreamInfoItem): String? {
        var viewsAndDate = ""
        if (infoItem.viewCount >= 0) {
            viewsAndDate = when (infoItem.streamType) {
                StreamType.AUDIO_LIVE_STREAM -> {
                    Localization
                        .listeningCount(itemBuilder.context, infoItem.viewCount)
                }
                StreamType.LIVE_STREAM -> {
                    Localization
                        .shortWatchingCount(itemBuilder.context, infoItem.viewCount)
                }
                else -> {
                    Localization
                        .shortViewCount(itemBuilder.context, infoItem.viewCount)
                }
            }
        }
        val uploadDate = getFormattedRelativeUploadDate(infoItem)
        return if (!TextUtils.isEmpty(uploadDate)) {
            if (viewsAndDate.isEmpty()) {
                uploadDate
            } else Localization.concatenateStrings(viewsAndDate, uploadDate)
        } else viewsAndDate
    }

    private fun getFormattedRelativeUploadDate(infoItem: StreamInfoItem): String? {
        return if (infoItem.uploadDate != null) {
            var formattedRelativeTime = Localization
                .relativeTime(infoItem.uploadDate!!.offsetDateTime())
            if (MainActivity.DEBUG && PreferenceManager.getDefaultSharedPreferences(itemBuilder.context)
                .getBoolean(
                        itemBuilder.context
                            .getString(R.string.show_original_time_ago_key),
                        false
                    )
            ) {
                formattedRelativeTime += " (" + infoItem.textualUploadDate + ")"
            }
            formattedRelativeTime
        } else {
            infoItem.textualUploadDate
        }
    }
}
