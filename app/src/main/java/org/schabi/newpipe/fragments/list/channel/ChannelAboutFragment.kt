package org.schabi.newpipe.fragments.list.channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import icepick.State
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.fragments.detail.BaseDescriptionFragment
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.Localization

class ChannelAboutFragment : BaseDescriptionFragment {
    @State
    protected var channelInfo: ChannelInfo? = null

    internal constructor(channelInfo: ChannelInfo) {
        this.channelInfo = channelInfo
    }

    constructor()

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        binding!!.constraintLayout.setPadding(0, DeviceUtils.dpToPx(8, requireContext()), 0, 0)
    }

    override fun getDescription(): Description? {
        return Description(channelInfo!!.getDescription(), Description.PLAIN_TEXT)
    }

    override fun getService(): StreamingService {
        return channelInfo!!.getService()
    }

    override fun getServiceId(): Int {
        return channelInfo!!.getServiceId()
    }

    override fun getStreamUrl(): String? {
        return null
    }

    public override fun getTags(): List<String?> {
        return channelInfo!!.getTags()
    }

    override fun setupMetadata(inflater: LayoutInflater?,
                               layout: LinearLayout?) {
        // There is no upload date available for channels, so hide the relevant UI element
        binding!!.detailUploadDateView.setVisibility(View.GONE)
        if (channelInfo == null) {
            return
        }
        if (channelInfo!!.getSubscriberCount() != StreamExtractor.UNKNOWN_SUBSCRIBER_COUNT) {
            addMetadataItem(inflater, (layout)!!, false, R.string.metadata_subscribers,
                    Localization.localizeNumber(
                            requireContext(),
                            channelInfo!!.getSubscriberCount()))
        }
        addImagesMetadataItem(inflater, (layout)!!, R.string.metadata_avatars,
                channelInfo!!.getAvatars())
        addImagesMetadataItem(inflater, (layout), R.string.metadata_banners,
                channelInfo!!.getBanners())
    }
}
