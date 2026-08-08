package org.schabi.newpipe.ui.components.items.stream

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.NO_SERVICE_ID

@Suppress("ktlint:standard:function-naming")
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

internal class StreamItemPreviewProvider : PreviewParameterProvider<StreamInfoItem> {
    override val values = sequenceOf(
        StreamInfoItem(streamType = StreamType.NONE),
        StreamInfoItem(streamType = StreamType.LIVE_STREAM),
        StreamInfoItem(streamType = StreamType.AUDIO_LIVE_STREAM)
    )
}
