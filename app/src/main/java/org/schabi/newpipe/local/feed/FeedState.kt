package org.schabi.newpipe.local.feed

import androidx.annotation.StringRes
import java.time.OffsetDateTime
import org.schabi.newpipe.database.stream.StreamWithState

sealed class FeedState {
    data object IdleState : FeedState()

    data class ProgressState(
        val progress: Int,
        val max: Int,
        @StringRes val progressMessage: Int = 0
    ) : FeedState()

    data class LoadedState(
        val items: List<StreamWithState>,
        val oldestUpdate: OffsetDateTime?,
        val notLoadedCount: Long,
        val itemsErrors: List<Throwable>
    ) : FeedState()

    data class ErrorState(val error: Throwable?) : FeedState()
}
