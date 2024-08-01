package org.schabi.newpipe.ui.components.items

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun ItemThumbnail(
    item: InfoItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(item.thumbnails),
            contentDescription = null,
            placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
            error = painterResource(R.drawable.placeholder_thumbnail_video),
            contentScale = contentScale,
            modifier = modifier
        )

        val isLive = item is StreamInfoItem && StreamTypeUtil.isLiveStream(item.streamType)
        val background = if (isLive) Color.Red else Color.Black
        val nestedModifier = Modifier
            .padding(2.dp)
            .background(background.copy(alpha = 0.5f))
            .padding(2.dp)

        if (item is StreamInfoItem) {
            Text(
                text = if (isLive) {
                    stringResource(R.string.duration_live)
                } else {
                    Localization.getDurationString(item.duration)
                },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = nestedModifier
            )
        } else if (item is PlaylistInfoItem) {
            Row(modifier = nestedModifier, verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_playlist_play),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(18.dp)
                )

                val context = LocalContext.current
                Text(
                    text = Localization.localizeStreamCountMini(context, item.streamCount),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
