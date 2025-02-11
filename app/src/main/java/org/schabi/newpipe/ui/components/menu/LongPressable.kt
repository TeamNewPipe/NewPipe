package org.schabi.newpipe.ui.components.menu

import androidx.compose.runtime.Stable
import org.schabi.newpipe.util.Either
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
}
