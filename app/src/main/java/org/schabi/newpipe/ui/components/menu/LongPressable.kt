package org.schabi.newpipe.ui.components.menu

import androidx.compose.runtime.Stable
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.Either
import java.time.OffsetDateTime

// TODO move within LongPressable
sealed interface LongPressableDecoration {
    data class Duration(val duration: Long) : LongPressableDecoration
    data object Live : LongPressableDecoration
    data class Playlist(val itemCount: Long) : LongPressableDecoration
}

// TODO this can be a data class
@Stable
interface LongPressable {
    val title: String
    val url: String?
    val thumbnailUrl: String?
    val uploader: String?
    val uploaderUrl: String?
    val viewCount: Long?
    val uploadDate: Either<String, OffsetDateTime>?
    val decoration: LongPressableDecoration?

    fun getPlayQueue(): PlayQueue
}
