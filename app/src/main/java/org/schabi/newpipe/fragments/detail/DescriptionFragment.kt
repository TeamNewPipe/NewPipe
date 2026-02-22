package org.schabi.newpipe.fragments.detail

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.evernote.android.state.State
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamExtractor.Privacy
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.util.Localization

class DescriptionFragment : BaseDescriptionFragment {
    @JvmField
    @State
    var streamInfo: StreamInfo? = null

    constructor(streamInfo: StreamInfo?) {
        this.streamInfo = streamInfo
    }

    constructor()

    override val description: Description
        get() = streamInfo!!.description

    override val serviceId: Int
        get() = streamInfo!!.serviceId

    override val tags: List<String>
        get() = streamInfo!!.tags

    override val uploadDate: String?
        get() = Localization.localizeUploadDate(activity, streamInfo!!.uploadDate.offsetDateTime())

    @Composable
    override fun Metadata() {
        if (streamInfo == null) {
            return
        }

        MetadataItem(
            R.string.metadata_category,
            streamInfo!!.category,
            false
        )

        MetadataItem(
            R.string.metadata_licence,
            streamInfo!!.licence,
            false
        )

        @StringRes val contentRes = when (streamInfo?.privacy) {
            Privacy.PUBLIC -> R.string.metadata_privacy_public
            Privacy.UNLISTED -> R.string.metadata_privacy_unlisted
            Privacy.PRIVATE -> R.string.metadata_privacy_private
            Privacy.INTERNAL -> R.string.metadata_privacy_internal
            else -> -1
        }

        if (contentRes != -1) {
            MetadataItem(R.string.metadata_privacy, getString(contentRes), false)
        }

        if (streamInfo!!.ageLimit != StreamExtractor.NO_AGE_LIMIT) {
            MetadataItem(
                R.string.metadata_age_limit,
                streamInfo!!.ageLimit.toString(),
                false
            )
        }

        if (streamInfo!!.languageInfo != null) {
            MetadataItem(
                R.string.metadata_language,
                streamInfo!!.languageInfo.getDisplayLanguage(Localization.getAppLocale()),
                false
            )
        }

        MetadataItem(
            R.string.metadata_support,
            streamInfo!!.supportInfo,
            true
        )
        MetadataItem(
            R.string.metadata_host,
            streamInfo!!.host,
            true
        )

        MetadataItem(
            R.string.metadata_thumbnails,
            streamInfo!!.thumbnails
        )
        MetadataItem(
            R.string.metadata_uploader_avatars,
            streamInfo!!.uploaderAvatars
        )
        MetadataItem(
            R.string.metadata_subchannel_avatars,
            streamInfo!!.subChannelAvatars
        )
    }
}
