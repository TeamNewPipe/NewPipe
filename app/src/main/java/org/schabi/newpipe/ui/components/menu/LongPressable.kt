package org.schabi.newpipe.ui.components.menu

import androidx.compose.runtime.Stable
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.Either
import java.time.OffsetDateTime

@Stable
interface LongPressable {
    val title: String
    val url: String?
    val thumbnailUrl: String?
    val uploader: String?
    val uploaderUrl: String?
    val viewCount: Long?
    val uploadDate: Either<String, OffsetDateTime>?
    val playlistSize: Long? // null if this is not a playlist
    val duration: Long?

    fun getPlayQueue(): PlayQueue
}
