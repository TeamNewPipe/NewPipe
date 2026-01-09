package org.schabi.newpipe.ui.components.channel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.channel.ChannelInfo

@Parcelize
data class ParcelableChannelInfo(
    val serviceId: Int,
    val description: String,
    val subscriberCount: Long,
    val avatars: List<Image>,
    val banners: List<Image>,
    val tags: List<String>
) : Parcelable {
    constructor(channelInfo: ChannelInfo) : this(
        channelInfo.serviceId, channelInfo.description, channelInfo.subscriberCount,
        channelInfo.avatars, channelInfo.banners, channelInfo.tags
    )
}
