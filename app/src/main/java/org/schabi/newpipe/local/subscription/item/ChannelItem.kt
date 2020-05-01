package org.schabi.newpipe.local.subscription.item

import android.content.Context
import com.nostra13.universalimageloader.core.ImageLoader
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.list_channel_item.itemAdditionalDetails
import kotlinx.android.synthetic.main.list_channel_item.itemChannelDescriptionView
import kotlinx.android.synthetic.main.list_channel_item.itemThumbnailView
import kotlinx.android.synthetic.main.list_channel_item.itemTitleView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.util.ImageDisplayConstants
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.OnClickGesture

class ChannelItem(
    private val infoItem: ChannelInfoItem,
    private val subscriptionId: Long = -1L,
    var itemVersion: ItemVersion = ItemVersion.NORMAL,
    var gesturesListener: OnClickGesture<ChannelInfoItem>? = null
) : Item() {

    override fun getId(): Long = if (subscriptionId == -1L) super.getId() else subscriptionId

    enum class ItemVersion { NORMAL, MINI, GRID }

    override fun getLayout(): Int = when (itemVersion) {
        ItemVersion.NORMAL -> R.layout.list_channel_item
        ItemVersion.MINI -> R.layout.list_channel_mini_item
        ItemVersion.GRID -> R.layout.list_channel_grid_item
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.itemTitleView.text = infoItem.name
        viewHolder.itemAdditionalDetails.text = getDetailLine(viewHolder.root.context)
        if (itemVersion == ItemVersion.NORMAL) viewHolder.itemChannelDescriptionView.text = infoItem.description

        ImageLoader.getInstance().displayImage(infoItem.thumbnailUrl, viewHolder.itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS)

        gesturesListener?.run {
            viewHolder.containerView.setOnClickListener { selected(infoItem) }
            viewHolder.containerView.setOnLongClickListener { held(infoItem); true }
        }
    }

    private fun getDetailLine(context: Context): String {
        var details = if (infoItem.subscriberCount >= 0) {
            Localization.shortSubscriberCount(context, infoItem.subscriberCount)
        } else {
            context.getString(R.string.subscribers_count_not_available)
        }

        if (itemVersion == ItemVersion.NORMAL) {
            if (infoItem.streamCount >= 0) {
                val formattedVideoAmount = Localization.localizeStreamCount(context, infoItem.streamCount)
                details = Localization.concatenateStrings(details, formattedVideoAmount)
            }
        }
        return details
    }

    override fun getSpanSize(spanCount: Int, position: Int): Int {
        return if (itemVersion == ItemVersion.GRID) 1 else spanCount
    }
}
