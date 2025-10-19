package org.schabi.newpipe.ui.components.menu

import androidx.compose.runtime.Stable
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
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
    val streamType: StreamType?, // only used to format the view count properly
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
                    duration.takeIf { it > 0 }?.let { Duration(it) }
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
            streamType = item.streamType,
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
            streamType = item.streamType,
            uploadDate = item.uploadDate?.let { Either.right(it) }
                ?: item.textualUploadDate?.let { Either.left(it) },
            decoration = Decoration.from(item.streamType, item.duration),
        )

        @JvmStatic
        fun fromPlaylistMetadataEntry(item: PlaylistMetadataEntry) = LongPressable(
            // many fields are null because this is a local playlist
            title = item.orderingName ?: "",
            url = null,
            thumbnailUrl = item.thumbnailUrl,
            uploader = null,
            uploaderUrl = null,
            viewCount = null,
            streamType = null,
            uploadDate = null,
            decoration = Decoration.Playlist(item.streamCount),
        )

        @JvmStatic
        fun fromPlaylistRemoteEntity(item: PlaylistRemoteEntity) = LongPressable(
            title = item.orderingName ?: "",
            url = item.url,
            thumbnailUrl = item.thumbnailUrl,
            uploader = item.uploader,
            uploaderUrl = null,
            viewCount = null,
            streamType = null,
            uploadDate = null,
            decoration = Decoration.Playlist(
                item.streamCount ?: ListExtractor.ITEM_COUNT_UNKNOWN
            ),
        )

        @JvmStatic
        fun fromChannelInfoItem(item: ChannelInfoItem) = LongPressable(
            title = item.name,
            url = item.url?.takeIf { it.isNotBlank() },
            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            uploader = null,
            uploaderUrl = item.url?.takeIf { it.isNotBlank() },
            viewCount = null,
            streamType = null,
            uploadDate = null,
            decoration = null,
        )

        @JvmStatic
        fun fromPlaylistInfoItem(item: PlaylistInfoItem) = LongPressable(
            title = item.name,
            url = item.url?.takeIf { it.isNotBlank() },
            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            uploader = item.uploaderName.takeIf { it.isNotBlank() },
            uploaderUrl = item.uploaderUrl?.takeIf { it.isNotBlank() },
            viewCount = null,
            streamType = null,
            uploadDate = null,
            decoration = Decoration.Playlist(item.streamCount),
        )
    }
}
