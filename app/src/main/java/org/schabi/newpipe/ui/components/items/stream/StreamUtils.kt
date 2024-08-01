package org.schabi.newpipe.ui.components.items.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID
import java.util.concurrent.TimeUnit

fun StreamInfoItem(
    serviceId: Int = NO_SERVICE_ID,
    url: String = "",
    name: String = "Stream",
    streamType: StreamType,
    uploaderName: String? = "Uploader",
    uploaderUrl: String? = null,
    uploaderAvatars: List<Image> = emptyList(),
    duration: Long = TimeUnit.HOURS.toSeconds(1),
    viewCount: Long = 10,
    textualUploadDate: String = "1 month ago"
) = StreamInfoItem(serviceId, url, name, streamType).apply {
    this.uploaderName = uploaderName
    this.uploaderUrl = uploaderUrl
    this.uploaderAvatars = uploaderAvatars
    this.duration = duration
    this.viewCount = viewCount
    this.textualUploadDate = textualUploadDate
}

@Composable
internal fun getStreamInfoDetail(stream: StreamInfoItem): String {
    val context = LocalContext.current

    return rememberSaveable(stream) {
        val count = stream.viewCount
        val views = if (count >= 0) {
            when (stream.streamType) {
                StreamType.AUDIO_LIVE_STREAM -> Localization.listeningCount(context, count)
                StreamType.LIVE_STREAM -> Localization.shortWatchingCount(context, count)
                else -> Localization.shortViewCount(context, count)
            }
        } else {
            ""
        }
        val date =
            Localization.relativeTimeOrTextual(context, stream.uploadDate, stream.textualUploadDate)

        if (views.isEmpty()) {
            date.orEmpty()
        } else if (date.isNullOrEmpty()) {
            views
        } else {
            "$views • $date"
        }
    }
}

internal class StreamItemPreviewProvider : PreviewParameterProvider<StreamInfoItem> {
    override val values = sequenceOf(
        StreamInfoItem(streamType = StreamType.NONE),
        StreamInfoItem(streamType = StreamType.LIVE_STREAM),
        StreamInfoItem(streamType = StreamType.AUDIO_LIVE_STREAM),
    )
}
