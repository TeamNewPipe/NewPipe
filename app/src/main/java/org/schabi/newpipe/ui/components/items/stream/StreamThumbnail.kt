package org.schabi.newpipe.ui.components.items.stream

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.ui.components.items.common.Thumbnail
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.viewmodels.StreamViewModel

@Composable
fun StreamThumbnail(
    stream: Stream,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    Column(modifier = modifier) {
        val isLive = StreamTypeUtil.isLiveStream(stream.type)
        Thumbnail(
            images = stream.thumbnails,
            imageDescription = stringResource(R.string.stream_content_description, stream.name),
            imagePlaceholder = R.drawable.placeholder_thumbnail_video,
            cornerBackgroundColor = if (isLive) Color.Red else Color.Black.copy(alpha = 0.5f),
            cornerIcon = null,
            cornerText = if (isLive) {
                stringResource(R.string.duration_live)
            } else {
                Localization.getDurationString(stream.duration)
            },
            contentScale = contentScale,
            modifier = modifier
        )

        if (showProgress) {
            val streamViewModel = viewModel<StreamViewModel>()
            var progress by rememberSaveable { mutableLongStateOf(0L) }

            LaunchedEffect(stream) {
                progress = streamViewModel.getStreamState(stream.toStreamInfoItem())?.progressMillis ?: 0L
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
