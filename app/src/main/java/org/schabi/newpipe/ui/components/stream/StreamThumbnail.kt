package org.schabi.newpipe.ui.components.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun StreamThumbnail(
    stream: StreamInfoItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(stream.thumbnails),
            contentDescription = null,
            placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
            error = painterResource(R.drawable.placeholder_thumbnail_video),
            contentScale = contentScale,
            modifier = modifier
        )

        val isLive = StreamTypeUtil.isLiveStream(stream.streamType)
        val background = if (isLive) Color.Red else Color.Black
        Text(
            text = if (isLive) {
                stringResource(R.string.duration_live)
            } else {
                Localization.getDurationString(stream.duration)
            },
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(2.dp)
                .background(background.copy(alpha = 0.5f))
                .padding(2.dp)
        )
    }
}
