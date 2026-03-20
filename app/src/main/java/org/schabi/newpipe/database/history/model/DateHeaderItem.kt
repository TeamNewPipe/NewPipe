package org.schabi.newpipe.database.history.model

import java.time.LocalDate
import org.schabi.newpipe.database.LocalItem

data class DateHeaderItem(
    val date: LocalDate
) : LocalItem {
    override val localItemType: LocalItem.LocalItemType
        get() = LocalItem.LocalItemType.DATE_HEADER
}
