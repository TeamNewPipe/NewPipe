package org.schabi.newpipe.fragments.detail

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import icepick.State
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamExtractor.Privacy
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.util.Localization

class DescriptionFragment : BaseDescriptionFragment {
    @State
    var streamInfo: StreamInfo? = null

    constructor(streamInfo: StreamInfo?) {
        this.streamInfo = streamInfo
    }

    constructor()

    override fun getDescription(): Description? {
        return streamInfo!!.getDescription()
    }

    override fun getService(): StreamingService {
        return streamInfo!!.getService()
    }

    override fun getServiceId(): Int {
        return streamInfo!!.getServiceId()
    }

    override fun getStreamUrl(): String {
        return streamInfo!!.getUrl()
    }

    public override fun getTags(): List<String?> {
        return streamInfo!!.getTags()
    }

    override fun setupMetadata(inflater: LayoutInflater?,
                               layout: LinearLayout?) {
        if (streamInfo != null && streamInfo!!.getUploadDate() != null) {
            binding!!.detailUploadDateView.setText(Localization.localizeUploadDate((activity)!!, streamInfo!!.getUploadDate().offsetDateTime()))
        } else {
            binding!!.detailUploadDateView.setVisibility(View.GONE)
        }
        if (streamInfo == null) {
            return
        }
        addMetadataItem(inflater, (layout)!!, false, R.string.metadata_category,
                streamInfo!!.getCategory())
        addMetadataItem(inflater, (layout), false, R.string.metadata_licence,
                streamInfo!!.getLicence())
        addPrivacyMetadataItem(inflater, layout)
        if (streamInfo!!.getAgeLimit() != StreamExtractor.NO_AGE_LIMIT) {
            addMetadataItem(inflater, (layout), false, R.string.metadata_age_limit, streamInfo!!.getAgeLimit().toString())
        }
        if (streamInfo!!.getLanguageInfo() != null) {
            addMetadataItem(inflater, (layout), false, R.string.metadata_language,
                    streamInfo!!.getLanguageInfo().getDisplayLanguage(Localization.getAppLocale((getContext())!!)))
        }
        addMetadataItem(inflater, (layout), true, R.string.metadata_support,
                streamInfo!!.getSupportInfo())
        addMetadataItem(inflater, (layout), true, R.string.metadata_host,
                streamInfo!!.getHost())
        addImagesMetadataItem(inflater, (layout), R.string.metadata_thumbnails,
                streamInfo!!.getThumbnails())
        addImagesMetadataItem(inflater, (layout), R.string.metadata_uploader_avatars,
                streamInfo!!.getUploaderAvatars())
        addImagesMetadataItem(inflater, (layout), R.string.metadata_subchannel_avatars,
                streamInfo!!.getSubChannelAvatars())
    }

    private fun addPrivacyMetadataItem(inflater: LayoutInflater?, layout: LinearLayout?) {
        if (streamInfo!!.getPrivacy() != null) {
            @StringRes val contentRes: Int
            when (streamInfo!!.getPrivacy()) {
                Privacy.PUBLIC -> contentRes = R.string.metadata_privacy_public
                Privacy.UNLISTED -> contentRes = R.string.metadata_privacy_unlisted
                Privacy.PRIVATE -> contentRes = R.string.metadata_privacy_private
                Privacy.INTERNAL -> contentRes = R.string.metadata_privacy_internal
                Privacy.OTHER -> contentRes = 0
                else -> contentRes = 0
            }
            if (contentRes != 0) {
                addMetadataItem(inflater, (layout)!!, false, R.string.metadata_privacy,
                        getString(contentRes))
            }
        }
    }
}
