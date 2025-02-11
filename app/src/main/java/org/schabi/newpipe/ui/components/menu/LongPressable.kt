package org.schabi.newpipe.ui.components.menu

import androidx.compose.runtime.Stable
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.Either
import org.schabi.newpipe.util.image.ImageStrategy
import java.time.OffsetDateTime

@Stable
data class LongPressable(
    val title: String,
    val url: String?,
    val thumbnailUrl: String?,
    val uploader: String?,
    val uploaderUrl: String?,
    val viewCount: Long?,
    val uploadDate: Either<String, OffsetDateTime>?,
    val decoration: Decoration?,
) {
    sealed interface Decoration {
        data class Duration(val duration: Long) : Decoration
        data object Live : Decoration
        data class Playlist(val itemCount: Long) : Decoration
    }

    companion object {
        @JvmStatic
        fun from(item: StreamInfoItem) = LongPressable(
            title = item.name,
            url = item.url?.takeIf { it.isNotBlank() },
            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            uploader = item.uploaderName?.takeIf { it.isNotBlank() },
            uploaderUrl = item.uploaderUrl?.takeIf { it.isNotBlank() },
            viewCount = item.viewCount.takeIf { it >= 0 },
            uploadDate = item.uploadDate?.let { Either.right(it.offsetDateTime()) }
                ?: item.textualUploadDate?.let { Either.left(it) },
            decoration = if (item.streamType == StreamType.LIVE_STREAM ||
                item.streamType == StreamType.AUDIO_LIVE_STREAM
            ) {
                LongPressable.Decoration.Live
            } else {
                item.duration.takeIf { it >= 0 }?.let {
                    LongPressable.Decoration.Duration(it)
                }
            },
        )
    }
}
