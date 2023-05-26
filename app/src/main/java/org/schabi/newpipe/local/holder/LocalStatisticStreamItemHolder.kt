package org.schabi.newpipe.local.holder

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PicassoHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.views.AnimatedProgressBar
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
open class LocalStatisticStreamItemHolder internal constructor(
    infoItemBuilder: LocalItemBuilder,
    layoutId: Int,
    parent: ViewGroup?
) : LocalItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    val itemVideoTitleView: TextView = itemView.findViewById(R.id.itemVideoTitleView)
    val itemUploaderView: TextView = itemView.findViewById(R.id.itemUploaderView)
    val itemDurationView: TextView = itemView.findViewById(R.id.itemDurationView)
    val itemAdditionalDetails: TextView? = itemView.findViewById(R.id.itemAdditionalDetails)
    private val itemProgressView: AnimatedProgressBar = itemView.findViewById(R.id.itemProgressView)

    constructor(
        itemBuilder: LocalItemBuilder,
        parent: ViewGroup?
    ) : this(itemBuilder, R.layout.list_stream_item, parent) {
    }

    private fun getStreamInfoDetailLine(
        entry: StreamStatisticsEntry,
        dateTimeFormatter: DateTimeFormatter
    ): String {
        return Localization.concatenateStrings( // watchCount
            Localization.shortViewCount(itemBuilder.context, entry.watchCount),
            dateTimeFormatter.format(entry.latestAccessDate), // serviceName
            ServiceHelper.getNameOfServiceById(entry.streamEntity.serviceId)
        )
    }

    override fun updateFromItem(
        item: LocalItem?,
        historyRecordManager: HistoryRecordManager?,
        dateTimeFormatter: DateTimeFormatter
    ) {
        if (item !is StreamStatisticsEntry) {
            return
        }
        itemVideoTitleView.text = item.streamEntity.title
        itemUploaderView.text = item.streamEntity.uploader
        if (item.streamEntity.duration > 0) {
            itemDurationView.text = Localization.getDurationString(item.streamEntity.duration)
            itemDurationView.setBackgroundColor(
                ContextCompat.getColor(
                    itemBuilder.context,
                    R.color.duration_background_color
                )
            )
            itemDurationView.visibility = View.VISIBLE
            if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.context) &&
                item.progressMillis > 0
            ) {
                itemProgressView.visibility = View.VISIBLE
                itemProgressView.max = item.streamEntity.duration.toInt()
                itemProgressView.progress = TimeUnit.MILLISECONDS
                    .toSeconds(item.progressMillis).toInt()
            } else {
                itemProgressView.visibility = View.GONE
            }
        } else {
            itemDurationView.visibility = View.GONE
            itemProgressView.visibility = View.GONE
        }
        if (itemAdditionalDetails != null) {
            itemAdditionalDetails.text = getStreamInfoDetailLine(item, dateTimeFormatter)
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        PicassoHelper.loadThumbnail(item.streamEntity.thumbnailUrl)
            .into(itemThumbnailView)
        itemView.setOnClickListener {
            if (itemBuilder.onItemSelectedListener != null) {
                itemBuilder.onItemSelectedListener.selected(item)
            }
        }
        itemView.isLongClickable = true
        itemView.setOnLongClickListener {
            if (itemBuilder.onItemSelectedListener != null) {
                itemBuilder.onItemSelectedListener.held(item)
            }
            true
        }
    }

    override fun updateState(
        localItem: LocalItem?,
        historyRecordManager: HistoryRecordManager?
    ) {
        if (localItem !is StreamStatisticsEntry) {
            return
        }
        if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.context) && localItem.progressMillis > 0 && localItem.streamEntity.duration > 0) {
            itemProgressView.max = localItem.streamEntity.duration.toInt()
            if (itemProgressView.visibility == View.VISIBLE) {
                itemProgressView.setProgressAnimated(
                    TimeUnit.MILLISECONDS
                        .toSeconds(localItem.progressMillis).toInt()
                )
            } else {
                itemProgressView.progress = TimeUnit.MILLISECONDS
                    .toSeconds(localItem.progressMillis).toInt()
                itemProgressView.animate(true, 500)
            }
        } else if (itemProgressView.visibility == View.VISIBLE) {
            itemProgressView.animate(false, 500)
        }
    }
}
