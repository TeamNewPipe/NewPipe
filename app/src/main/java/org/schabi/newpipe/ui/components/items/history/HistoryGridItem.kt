package org.schabi.newpipe.ui.components.items.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.ui.components.items.stream.StreamMenu
import org.schabi.newpipe.ui.components.items.stream.StreamThumbnail
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryGridItem(
    entry: StreamStatisticsEntry,
    dateTimeFormatter: DateTimeFormatter,
    showProgress: Boolean,
    isSelected: Boolean = false,
    isMini: Boolean = false,
    onClick: (StreamStatisticsEntry) -> Unit = {},
    onLongClick: (StreamStatisticsEntry) -> Unit = {},
    onDismissPopup: () -> Unit = {}
) {
    val stream = entry.toStreamInfoItem()

    Box {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onLongClick = { onLongClick(entry) },
                    onClick = { onClick(entry) }
                )
                .padding(12.dp)
        ) {
            val size = if (isMini) DpSize(150.dp, 85.dp) else DpSize(246.dp, 138.dp)

            StreamThumbnail(
                stream = stream,
                showProgress = showProgress,
                modifier = Modifier.size(size)
            )

            Text(
                text = entry.streamEntity.title,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2
            )

            Text(text = entry.streamEntity.uploader, style = MaterialTheme.typography.bodySmall)

            Text(
                text = getHistoryDetail(entry, dateTimeFormatter),
                style = MaterialTheme.typography.bodySmall
            )
        }

        StreamMenu(stream, isSelected, onDismissPopup)
    }
}
