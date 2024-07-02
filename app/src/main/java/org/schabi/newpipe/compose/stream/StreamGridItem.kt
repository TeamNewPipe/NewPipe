package org.schabi.newpipe.compose.stream

import android.content.res.Configuration
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun StreamGridItem(stream: StreamInfoItem, onClick: (StreamInfoItem) -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = { onClick(stream) })
            .padding(12.dp)
    ) {
        StreamThumbnail(stream = stream, modifier = Modifier.size(width = 246.dp, height = 138.dp))

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

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamGridItemPreview(
    @PreviewParameter(StreamItemPreviewProvider::class) stream: StreamInfoItem
) {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StreamGridItem(stream, onClick = {})
        }
    }
}
