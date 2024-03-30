package org.schabi.newpipe.info_list.holder

import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.PicassoHelper
import org.schabi.newpipe.views.AnimatedProgressBar
import java.util.concurrent.TimeUnit

open class StreamMiniInfoItemHolder internal constructor(infoItemBuilder: InfoItemBuilder, layoutId: Int,
                                                         parent: ViewGroup?) : InfoItemHolder(infoItemBuilder, layoutId, parent) {
    val itemThumbnailView: ImageView
    val itemVideoTitleView: TextView
    val itemUploaderView: TextView
    val itemDurationView: TextView
    private val itemProgressView: AnimatedProgressBar

    init {
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView)
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView)
        itemDurationView = itemView.findViewById(R.id.itemDurationView)
        itemProgressView = itemView.findViewById(R.id.itemProgressView)
    }

    constructor(infoItemBuilder: InfoItemBuilder, parent: ViewGroup?) : this(infoItemBuilder, R.layout.list_stream_mini_item, parent)

    public override fun updateFromItem(infoItem: InfoItem?,
                                       historyRecordManager: HistoryRecordManager) {
        if (!(infoItem is StreamInfoItem)) {
            return
        }
        val item: StreamInfoItem = infoItem
        itemVideoTitleView.setText(item.getName())
        itemUploaderView.setText(item.getUploaderName())
        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()))
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color))
            itemDurationView.setVisibility(View.VISIBLE)
            var state2: StreamStateEntity? = null
            if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())) {
                state2 = historyRecordManager.loadStreamState(infoItem)
                        .blockingGet().get(0)
            }
            if (state2 != null) {
                itemProgressView.setVisibility(View.VISIBLE)
                itemProgressView.setMax(item.getDuration().toInt())
                itemProgressView.setProgress(TimeUnit.MILLISECONDS
                        .toSeconds(state2.getProgressMillis()).toInt())
            } else {
                itemProgressView.setVisibility(View.GONE)
            }
        } else if (StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemDurationView.setText(R.string.duration_live)
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color))
            itemDurationView.setVisibility(View.VISIBLE)
            itemProgressView.setVisibility(View.GONE)
        } else {
            itemDurationView.setVisibility(View.GONE)
            itemProgressView.setVisibility(View.GONE)
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        PicassoHelper.loadThumbnail(item.getThumbnails()).into(itemThumbnailView)
        itemView.setOnClickListener(View.OnClickListener({ view: View? ->
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener()!!.selected(item)
            }
        }))
        when (item.getStreamType()) {
            StreamType.AUDIO_STREAM, StreamType.VIDEO_STREAM, StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM, StreamType.POST_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM -> enableLongClick(item)
            StreamType.NONE -> disableLongClick()
            else -> disableLongClick()
        }
    }

    public override fun updateState(infoItem: InfoItem,
                                    historyRecordManager: HistoryRecordManager) {
        val item: StreamInfoItem = infoItem as StreamInfoItem
        var state: StreamStateEntity? = null
        if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())) {
            state = historyRecordManager
                    .loadStreamState(infoItem)
                    .blockingGet().get(0)
        }
        if ((state != null) && (item.getDuration() > 0
                        ) && !StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemProgressView.setMax(item.getDuration().toInt())
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated(TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()).toInt())
            } else {
                itemProgressView.setProgress(TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()).toInt())
                itemProgressView.animate(true, 500)
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            itemProgressView.animate(false, 500)
        }
    }

    private fun enableLongClick(item: StreamInfoItem) {
        itemView.setLongClickable(true)
        itemView.setOnLongClickListener(OnLongClickListener({ view: View? ->
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener()!!.held(item)
            }
            true
        }))
    }

    private fun disableLongClick() {
        itemView.setLongClickable(false)
        itemView.setOnLongClickListener(null)
    }
}
