package org.schabi.newpipe.compose.stream

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamCardItem(
    stream: StreamInfoItem,
    isSelected: Boolean = false,
    onClick: (StreamInfoItem) -> Unit = {},
    onLongClick: (StreamInfoItem) -> Unit = {},
    onDismissPopup: () -> Unit = {}
) {
    Box {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onLongClick = { onLongClick(stream) },
                    onClick = { onClick(stream) }
                )
                .padding(top = 12.dp, start = 2.dp, end = 2.dp)
        ) {
            StreamThumbnail(
                modifier = Modifier.fillMaxWidth(),
                stream = stream,
                contentScale = ContentScale.FillWidth
            )

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = stream.name,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stream.uploaderName.orEmpty(), style = MaterialTheme.typography.bodySmall)

                    Text(
                        text = getStreamInfoDetail(stream),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (isSelected) {
            StreamMenu(onDismissPopup)
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamCardItemPreview(
    @PreviewParameter(StreamItemPreviewProvider::class) stream: StreamInfoItem
) {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StreamCardItem(stream)
        }
    }
}
