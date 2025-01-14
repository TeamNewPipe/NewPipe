package org.schabi.newpipe.ui.components.items.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import java.time.format.DateTimeFormatter

@Composable
internal fun getHistoryDetail(
    entry: StreamStatisticsEntry,
    dateTimeFormatter: DateTimeFormatter,
): String {
    val context = LocalContext.current

    return rememberSaveable(entry) {
        Localization.concatenateStrings(
            Localization.shortViewCount(context, entry.watchCount),
            dateTimeFormatter.format(entry.latestAccessDate),
            ServiceHelper.getNameOfServiceById(entry.streamEntity.serviceId),
        )
    }
}
