package org.schabi.newpipe.ui.components.items.playlist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.items.Playlist
import org.schabi.newpipe.ui.components.items.common.Thumbnail
import org.schabi.newpipe.util.Localization

@Composable
fun PlaylistThumbnail(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Thumbnail(
        images = playlist.thumbnails,
        imageDescription = stringResource(R.string.playlist_content_description, playlist.name),
        imagePlaceholder = R.drawable.placeholder_thumbnail_playlist,
        cornerBackgroundColor = Color.Black.copy(alpha = 0.5f),
        cornerIcon = Icons.AutoMirrored.Default.PlaylistPlay,
        cornerText = Localization.localizeStreamCountMini(LocalContext.current, playlist.streamCount),
        contentScale = contentScale,
        modifier = modifier
    )
}
