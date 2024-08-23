package org.schabi.newpipe.ui.components.items.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun PlaylistThumbnail(
    playlist: PlaylistInfoItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(playlist.thumbnails),
            contentDescription = null,
            placeholder = painterResource(R.drawable.placeholder_thumbnail_playlist),
            error = painterResource(R.drawable.placeholder_thumbnail_playlist),
            contentScale = contentScale,
            modifier = modifier
        )

        Row(
            modifier = Modifier
                .padding(2.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_playlist_play),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(18.dp)
            )

            val context = LocalContext.current
            Text(
                text = Localization.localizeStreamCountMini(context, playlist.streamCount),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
