package org.schabi.newpipe.local.holder

import android.view.View
import android.view.View.OnLongClickListener
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
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.PicassoHelper
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
open class LocalStatisticStreamItemHolder internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int,
                                                               parent: ViewGroup?) : LocalItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: ImageView
    val itemVideoTitleView: TextView
    val itemUploaderView: TextView
    val itemDurationView: TextView
    val itemAdditionalDetails: TextView?
    private val itemProgressView: AnimatedProgressBar

    constructor(itemBuilder: LocalItemBuilder,
                parent: ViewGroup?) : this(itemBuilder, R.layout.list_stream_item, parent)

    init {
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView)
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView)
        itemDurationView = itemView.findViewById(R.id.itemDurationView)
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails)
        itemProgressView = itemView.findViewById(R.id.itemProgressView)
    }

    private fun getStreamInfoDetailLine(entry: StreamStatisticsEntry,
                                        dateTimeFormatter: DateTimeFormatter): String {
        return Localization.concatenateStrings( // watchCount
                Localization.shortViewCount(itemBuilder.getContext(), entry.watchCount),
                dateTimeFormatter.format(entry.latestAccessDate),  // serviceName
                ServiceHelper.getNameOfServiceById(entry.streamEntity.serviceId))
    }

    public override fun updateFromItem(localItem: LocalItem?,
                                       historyRecordManager: HistoryRecordManager?,
                                       dateTimeFormatter: DateTimeFormatter) {
        if (!(localItem is StreamStatisticsEntry)) {
            return
        }
        val item: StreamStatisticsEntry = localItem
        itemVideoTitleView.setText(item.streamEntity.title)
        itemUploaderView.setText(item.streamEntity.uploader)
        if (item.streamEntity.duration > 0) {
            itemDurationView.setText(Localization.getDurationString(item.streamEntity.duration))
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color))
            itemDurationView.setVisibility(View.VISIBLE)
            if ((DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())
                            && item.progressMillis > 0)) {
                itemProgressView.setVisibility(View.VISIBLE)
                itemProgressView.setMax(item.streamEntity.duration.toInt())
                itemProgressView.setProgress(TimeUnit.MILLISECONDS
                        .toSeconds(item.progressMillis).toInt())
            } else {
                itemProgressView.setVisibility(View.GONE)
            }
        } else {
            itemDurationView.setVisibility(View.GONE)
            itemProgressView.setVisibility(View.GONE)
        }
        if (itemAdditionalDetails != null) {
            itemAdditionalDetails.setText(getStreamInfoDetailLine(item, dateTimeFormatter))
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        PicassoHelper.loadThumbnail(item.streamEntity.thumbnailUrl)
                .into(itemThumbnailView)
        itemView.setOnClickListener(View.OnClickListener({ view: View? ->
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().selected(item)
            }
        }))
        itemView.setLongClickable(true)
        itemView.setOnLongClickListener(OnLongClickListener({ view: View? ->
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().held(item)
            }
            true
        }))
    }

    public override fun updateState(localItem: LocalItem?,
                                    historyRecordManager: HistoryRecordManager?) {
        if (!(localItem is StreamStatisticsEntry)) {
            return
        }
        val item: StreamStatisticsEntry = localItem
        if ((DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())
                        && (item.progressMillis > 0) && (item.streamEntity.duration > 0))) {
            itemProgressView.setMax(item.streamEntity.duration.toInt())
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated(TimeUnit.MILLISECONDS
                        .toSeconds(item.progressMillis).toInt())
            } else {
                itemProgressView.setProgress(TimeUnit.MILLISECONDS
                        .toSeconds(item.progressMillis).toInt())
                itemProgressView.animate(true, 500)
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            itemProgressView.animate(false, 500)
        }
    }
}
