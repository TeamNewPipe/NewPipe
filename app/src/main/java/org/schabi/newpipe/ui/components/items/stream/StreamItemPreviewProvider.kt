package org.schabi.newpipe.ui.components.items.stream

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ui.components.items.Stream

internal class StreamItemPreviewProvider : PreviewParameterProvider<Stream> {
    override val values = sequenceOf(
        Stream(type = StreamType.NONE, name = "Stream", uploaderName = "Uploader"),
        Stream(type = StreamType.LIVE_STREAM, name = "Stream", uploaderName = "Uploader"),
        Stream(type = StreamType.AUDIO_LIVE_STREAM, name = "Stream", uploaderName = "Uploader"),
    )
}
