package org.schabi.newpipe.ui.components.items.playlist

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
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NO_SERVICE_ID

@Composable
fun PlaylistListItem(
    playlist: PlaylistInfoItem,
    onClick: (InfoItem) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .clickable { onClick(playlist) }
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistThumbnail(
            playlist = playlist,
            modifier = Modifier.size(width = 140.dp, height = 78.dp)
        )

        Column {
            Text(
                text = playlist.name,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2
            )

            Text(
                text = playlist.uploaderName.orEmpty(),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaylistListItemPreview() {
    val playlist = PlaylistInfoItem(NO_SERVICE_ID, "", "Playlist")
    playlist.uploaderName = "Uploader"

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlaylistListItem(playlist)
        }
    }
}
