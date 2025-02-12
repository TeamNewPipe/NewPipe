package org.schabi.newpipe.ui.components.menu

import androidx.compose.runtime.Stable
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
import org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
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

        companion object {
            internal fun from(streamType: StreamType, duration: Long) =
                if (streamType == LIVE_STREAM || streamType == AUDIO_LIVE_STREAM) {
                    Live
                } else {
                    duration.takeIf { it >= 0 }?.let { Duration(it) }
                }
        }
    }

    companion object {
        @JvmStatic
        fun fromStreamInfoItem(item: StreamInfoItem) = LongPressable(
            title = item.name,
            url = item.url?.takeIf { it.isNotBlank() },
            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            uploader = item.uploaderName?.takeIf { it.isNotBlank() },
            uploaderUrl = item.uploaderUrl?.takeIf { it.isNotBlank() },
            viewCount = item.viewCount.takeIf { it >= 0 },
            uploadDate = item.uploadDate?.let { Either.right(it.offsetDateTime()) }
                ?: item.textualUploadDate?.let { Either.left(it) },
            decoration = Decoration.from(item.streamType, item.duration),
        )

        @JvmStatic
        fun fromStreamEntity(item: StreamEntity) = LongPressable(
            title = item.title,
            url = item.url.takeIf { it.isNotBlank() },
            thumbnailUrl = item.thumbnailUrl,
            uploader = item.uploader.takeIf { it.isNotBlank() },
            uploaderUrl = item.uploaderUrl?.takeIf { it.isNotBlank() },
            viewCount = item.viewCount?.takeIf { it >= 0 },
            uploadDate = item.uploadDate?.let { Either.right(it) }
                ?: item.textualUploadDate?.let { Either.left(it) },
            decoration = Decoration.from(item.streamType, item.duration),
        )
    }
}
