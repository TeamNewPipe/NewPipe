package org.schabi.newpipe.ui.components.items.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.viewmodels.StreamViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun StreamThumbnail(
    stream: StreamInfoItem,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Column(modifier = modifier) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = ImageStrategy.choosePreferredImage(stream.thumbnails),
                contentDescription = null,
                placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
                error = painterResource(R.drawable.placeholder_thumbnail_video),
                contentScale = contentScale,
                modifier = modifier
            )

            val isLive = StreamTypeUtil.isLiveStream(stream.streamType)
            Text(
                modifier = Modifier
                    .padding(2.dp)
                    .background(if (isLive) Color.Red else Color.Black.copy(alpha = 0.5f))
                    .padding(2.dp),
                text = if (isLive) {
                    stringResource(R.string.duration_live)
                } else {
                    Localization.getDurationString(stream.duration)
                },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (showProgress) {
            val streamViewModel = viewModel<StreamViewModel>()
            var progress by rememberSaveable { mutableLongStateOf(0L) }

            LaunchedEffect(stream) {
                progress = streamViewModel.getStreamState(stream)?.progressMillis ?: 0L
            }

            if (progress != 0L) {
                LinearProgressIndicator(
                    modifier = Modifier.requiredHeight(2.dp),
                    progress = {
                        (progress.milliseconds / stream.duration.seconds).toFloat()
                    },
                    gapSize = 0.dp,
                    drawStopIndicator = {} // Hide stop indicator
                )
            }
        }
    }
}
