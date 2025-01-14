package org.schabi.newpipe.ui.components.items.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.ui.components.items.stream.StreamMenu
import org.schabi.newpipe.ui.components.items.stream.StreamThumbnail
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryListItem(
    entry: StreamStatisticsEntry,
    dateTimeFormatter: DateTimeFormatter,
    showProgress: Boolean,
    isSelected: Boolean,
    onClick: (StreamStatisticsEntry) -> Unit = {},
    onLongClick: (StreamStatisticsEntry) -> Unit = {},
    onDismissPopup: () -> Unit = {}
) {
    val stream = entry.toStreamInfoItem()

    // Box serves as an anchor for the dropdown menu
    Box(
        modifier = Modifier
            .combinedClickable(
                onLongClick = { onLongClick(entry) },
                onClick = { onClick(entry) }
            )
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamThumbnail(
                stream = stream,
                showProgress = showProgress,
                modifier = Modifier.size(width = 140.dp, height = 78.dp)
            )

            Column {
                Text(
                    text = stream.name,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2
                )

                Text(text = stream.uploaderName.orEmpty(), style = MaterialTheme.typography.bodySmall)

                Text(
                    text = getHistoryDetail(entry, dateTimeFormatter),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        StreamMenu(stream, isSelected, onDismissPopup)
    }
}
