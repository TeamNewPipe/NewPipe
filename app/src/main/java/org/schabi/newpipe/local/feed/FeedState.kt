package org.schabi.newpipe.local.feed

import androidx.annotation.StringRes
import java.util.Calendar
import org.schabi.newpipe.extractor.stream.StreamInfoItem

sealed class FeedState {
    data class ProgressState(
        val currentProgress: Int = -1,
        val maxProgress: Int = -1,
        @StringRes val progressMessage: Int = 0
    ) : FeedState()

    data class LoadedState(
        val items: List<StreamInfoItem>,
        val oldestUpdate: Calendar? = null,
        val notLoadedCount: Long,
        val itemsErrors: List<Throwable> = emptyList()
    ) : FeedState()

    data class ErrorState(
        val error: Throwable? = null
    ) : FeedState()
}
