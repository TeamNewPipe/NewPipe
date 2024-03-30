package org.schabi.newpipe.local.holder

import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
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

open class LocalPlaylistStreamItemHolder internal constructor(infoItemBuilder: LocalItemBuilder, layoutId: Int,
                                                              parent: ViewGroup?) : LocalItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: ImageView
    val itemVideoTitleView: TextView
    private val itemAdditionalDetailsView: TextView
    val itemDurationView: TextView
    private val itemHandleView: View
    private val itemProgressView: AnimatedProgressBar

    init {
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView)
        itemAdditionalDetailsView = itemView.findViewById(R.id.itemAdditionalDetails)
        itemDurationView = itemView.findViewById(R.id.itemDurationView)
        itemHandleView = itemView.findViewById(R.id.itemHandle)
        itemProgressView = itemView.findViewById(R.id.itemProgressView)
    }

    constructor(infoItemBuilder: LocalItemBuilder,
                parent: ViewGroup?) : this(infoItemBuilder, R.layout.list_stream_playlist_item, parent)

    public override fun updateFromItem(localItem: LocalItem?,
                                       historyRecordManager: HistoryRecordManager?,
                                       dateTimeFormatter: DateTimeFormatter) {
        if (!(localItem is PlaylistStreamEntry)) {
            return
        }
        val item: PlaylistStreamEntry = localItem
        itemVideoTitleView.setText(item.streamEntity.title)
        itemAdditionalDetailsView.setText(Localization.concatenateStrings(item.streamEntity.uploader,
                ServiceHelper.getNameOfServiceById(item.streamEntity.serviceId)))
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
        itemHandleView.setOnTouchListener(getOnTouchListener(item))
    }

    public override fun updateState(localItem: LocalItem?,
                                    historyRecordManager: HistoryRecordManager?) {
        if (!(localItem is PlaylistStreamEntry)) {
            return
        }
        val item: PlaylistStreamEntry = localItem
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

    private fun getOnTouchListener(item: PlaylistStreamEntry): OnTouchListener {
        return OnTouchListener({ view: View, motionEvent: MotionEvent ->
            view.performClick()
            if ((itemBuilder != null) && (itemBuilder.getOnItemSelectedListener() != null
                            ) && (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN)) {
                itemBuilder.getOnItemSelectedListener().drag(item,
                        this@LocalPlaylistStreamItemHolder)
            }
            false
        })
    }
}
