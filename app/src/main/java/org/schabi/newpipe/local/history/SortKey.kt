package org.schabi.newpipe.local.history

import androidx.annotation.StringRes
import org.schabi.newpipe.R

enum class SortKey(@StringRes val title: Int) {
    LAST_PLAYED(R.string.history_sort_date),
    MOST_PLAYED(R.string.history_sort_views)
}
