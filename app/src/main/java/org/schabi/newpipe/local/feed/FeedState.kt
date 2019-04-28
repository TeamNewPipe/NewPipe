package org.schabi.newpipe.local.feed

import androidx.annotation.StringRes
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.*

sealed class FeedState {
    data class ProgressState(val currentProgress: Int = -1, val maxProgress: Int = -1, @StringRes val progressMessage: Int = 0) : FeedState()
    data class LoadedState(val lastUpdated: Calendar? = null, val items: List<StreamInfoItem>, var itemsErrors: List<Throwable> = emptyList()) : FeedState()
    data class ErrorState(val error: Throwable? = null) : FeedState()
}