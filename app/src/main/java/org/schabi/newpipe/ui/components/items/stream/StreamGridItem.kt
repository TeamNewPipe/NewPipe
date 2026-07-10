package org.schabi.newpipe.ui.components.items.stream

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.ui.theme.AppTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamGridItem(
    stream: Stream,
    showProgress: Boolean,
    isSelected: Boolean = false,
    isMini: Boolean = false,
    onClick: (Stream) -> Unit = {},
    onLongClick: (Stream) -> Unit = {},
    onDismissPopup: () -> Unit = {}
) {
    Box {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onLongClick = { onLongClick(stream) },
                    onClick = { onClick(stream) }
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
                text = stream.name,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2
            )

            Text(text = stream.uploaderName, style = MaterialTheme.typography.bodySmall)

            Text(
                text = stream.detailText,
                style = MaterialTheme.typography.bodySmall
            )
        }

        StreamMenu(stream, isSelected, onDismissPopup)
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamGridItemPreview(
    @PreviewParameter(StreamItemPreviewProvider::class) stream: Stream
) {
    AppTheme {
        Surface {
            StreamGridItem(stream, showProgress = false, isSelected = false)
        }
    }
}
