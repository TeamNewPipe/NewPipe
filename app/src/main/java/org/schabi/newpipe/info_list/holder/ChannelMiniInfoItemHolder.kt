package org.schabi.newpipe.info_list.holder

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PicassoHelper

open class ChannelMiniInfoItemHolder internal constructor(
    infoItemBuilder: InfoItemBuilder?,
    layoutId: Int,
    parent: ViewGroup?
) : InfoItemHolder(infoItemBuilder!!, layoutId, parent) {
    private val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    private val itemTitleView: TextView = itemView.findViewById(R.id.itemTitleView)
    private val itemAdditionalDetailView: TextView = itemView.findViewById(R.id.itemAdditionalDetails)
    private val itemChannelDescriptionView: TextView? = itemView.findViewById(R.id.itemChannelDescriptionView)

    constructor(
        infoItemBuilder: InfoItemBuilder?,
        parent: ViewGroup?
    ) : this(infoItemBuilder, R.layout.list_channel_mini_item, parent) {
    }

    override fun updateFromItem(
        infoItem: InfoItem?,
        historyRecordManager: HistoryRecordManager?
    ) {
        if (infoItem !is ChannelInfoItem) {
            return
        }
        itemTitleView.text = infoItem.name
        itemTitleView.isSelected = true
        val detailLine = getDetailLine(infoItem)
        if (detailLine == null) {
            itemAdditionalDetailView.visibility = View.GONE
        } else {
            itemAdditionalDetailView.visibility = View.VISIBLE
            itemAdditionalDetailView.text = getDetailLine(infoItem)
        }
        PicassoHelper.loadAvatar(infoItem.thumbnailUrl).into(itemThumbnailView)
        itemView.setOnClickListener {
            if (itemBuilder.onChannelSelectedListener != null) {
                itemBuilder.onChannelSelectedListener.selected(infoItem)
            }
        }
        itemView.setOnLongClickListener {
            if (itemBuilder.onChannelSelectedListener != null) {
                itemBuilder.onChannelSelectedListener.held(infoItem)
            }
            true
        }
        if (itemChannelDescriptionView != null) {
            // itemChannelDescriptionView will be null in the mini variant
            if (Utils.isBlank(infoItem.description)) {
                itemChannelDescriptionView.visibility = View.GONE
            } else {
                itemChannelDescriptionView.visibility = View.VISIBLE
                itemChannelDescriptionView.text = infoItem.description
                // setMaxLines utilize the line space for description if the additional details
                // (sub / video count) are not present.
                // Case1: 2 lines of description + 1 line additional details
                // Case2: 3 lines of description (additionalDetails is GONE)
                itemChannelDescriptionView.maxLines = getDescriptionMaxLineCount(detailLine)
            }
        }
    }

    /**
     * Returns max number of allowed lines for the description field.
     * @param content additional detail content (video / sub count)
     * @return max line count
     */
    protected open fun getDescriptionMaxLineCount(content: String?): Int {
        return if (content == null) 3 else 2
    }

    private fun getDetailLine(item: ChannelInfoItem): String? {
        return if (item.streamCount >= 0 && item.subscriberCount >= 0) {
            Localization.concatenateStrings(
                Localization.shortSubscriberCount(
                    itemBuilder.context,
                    item.subscriberCount
                ),
                Localization.localizeStreamCount(
                    itemBuilder.context,
                    item.streamCount
                )
            )
        } else if (item.streamCount >= 0) {
            Localization.localizeStreamCount(
                itemBuilder.context,
                item.streamCount
            )
        } else if (item.subscriberCount >= 0) {
            Localization.shortSubscriberCount(
                itemBuilder.context,
                item.subscriberCount
            )
        } else {
            null
        }
    }
}
