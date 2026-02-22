package org.schabi.newpipe.fragments.list.channel

import androidx.compose.runtime.Composable
import com.evernote.android.state.State
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.fragments.detail.BaseDescriptionFragment
import org.schabi.newpipe.util.Localization

class ChannelAboutFragment : BaseDescriptionFragment {
    @State
    var channelInfo: ChannelInfo? = null

    constructor(channelInfo: ChannelInfo) {
        this.channelInfo = channelInfo
    }

    constructor()

    override val description: Description
        get() = Description(channelInfo!!.description, Description.PLAIN_TEXT)

    override val serviceId: Int
        get() = channelInfo!!.serviceId

    override val tags: List<String>
        get() = channelInfo!!.tags

    override val uploadDate: String?
        get() = null

    @Composable
    override fun Metadata() {
        if (channelInfo == null) {
            return
        }

        if (channelInfo!!.subscriberCount != StreamExtractor.UNKNOWN_SUBSCRIBER_COUNT) {
            MetadataItem(
                R.string.metadata_subscribers,
                Localization.localizeNumber(channelInfo!!.subscriberCount),
                false
            )
        }

        MetadataItem(R.string.metadata_avatars, channelInfo!!.avatars)
        MetadataItem(R.string.metadata_banners, channelInfo!!.banners)
    }
}
