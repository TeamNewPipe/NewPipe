package org.schabi.newpipe.ui.components.items.stream

import android.content.res.Configuration
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ui.theme.AppTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamListItem(
    stream: StreamInfoItem,
    showProgress: Boolean,
    isSelected: Boolean,
    onClick: (StreamInfoItem) -> Unit = {},
    onLongClick: (StreamInfoItem) -> Unit = {},
    onDismissPopup: () -> Unit = {}
) {
    // Box serves as an anchor for the dropdown menu
    Box(
        modifier = Modifier
            .combinedClickable(onLongClick = { onLongClick(stream) }, onClick = { onClick(stream) })
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
                    text = getStreamInfoDetail(stream),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        StreamMenu(stream, isSelected, onDismissPopup)
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamListItemPreview(
    @PreviewParameter(StreamItemPreviewProvider::class) stream: StreamInfoItem
) {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StreamListItem(stream, showProgress = false, isSelected = false)
        }
    }
}
