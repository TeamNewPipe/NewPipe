package org.schabi.newpipe.compose.stream

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun StreamListItem(stream: StreamInfoItem, onClick: (StreamInfoItem) -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = { onClick(stream) })
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StreamThumbnail(stream = stream, modifier = Modifier.size(width = 98.dp, height = 55.dp))

        Column {
            Text(
                text = stream.name,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )

            Text(text = stream.uploaderName.orEmpty(), style = MaterialTheme.typography.bodySmall)

            Text(
                text = getStreamInfoDetail(stream),
                style = MaterialTheme.typography.bodySmall
            )
        }
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
            StreamListItem(stream, onClick = {})
        }
    }
}
