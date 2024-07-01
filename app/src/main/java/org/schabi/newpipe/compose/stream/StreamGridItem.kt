package org.schabi.newpipe.compose.stream

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy
import java.util.concurrent.TimeUnit

@Composable
fun StreamGridItem(stream: StreamInfoItem) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .clickable {
                NavigationHelper.openVideoDetailFragment(
                    context, (context as FragmentActivity).supportFragmentManager,
                    stream.serviceId, stream.url, stream.name, null, false
                )
            }
            .padding(12.dp)
    ) {
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(stream.thumbnails),
            contentDescription = null,
            placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
            error = painterResource(R.drawable.placeholder_thumbnail_video),
            modifier = Modifier.size(width = 164.dp, height = 92.dp)
        )

        Text(
            text = stream.name,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2
        )

        Text(text = stream.uploaderName.orEmpty(), style = MaterialTheme.typography.bodySmall)

        Text(text = getStreamInfoDetail(context, stream), style = MaterialTheme.typography.bodySmall)
    }
}

private fun getStreamInfoDetail(context: Context, stream: StreamInfoItem): String {
    val views = if (stream.viewCount >= 0) {
        when (stream.streamType) {
            StreamType.AUDIO_LIVE_STREAM -> Localization.listeningCount(context, stream.viewCount)
            StreamType.LIVE_STREAM -> Localization.shortWatchingCount(context, stream.viewCount)
            else -> Localization.shortViewCount(context, stream.viewCount)
        }
    } else {
        ""
    }
    val date =
        Localization.relativeTimeOrTextual(context, stream.uploadDate, stream.textualUploadDate)

    return if (views.isEmpty()) {
        date
    } else if (date.isNullOrEmpty()) {
        views
    } else {
        "$views • $date"
    }
}

fun StreamInfoItem(
    serviceId: Int = NO_SERVICE_ID,
    url: String = "",
    name: String = "Stream",
    streamType: StreamType,
    uploaderName: String? = "Uploader",
    uploaderUrl: String? = null,
    uploaderAvatars: List<Image> = emptyList(),
    duration: Long = TimeUnit.HOURS.toSeconds(1),
    viewCount: Long = 10,
    textualUploadDate: String = "1 month ago"
) = StreamInfoItem(serviceId, url, name, streamType).apply {
    this.uploaderName = uploaderName
    this.uploaderUrl = uploaderUrl
    this.uploaderAvatars = uploaderAvatars
    this.duration = duration
    this.viewCount = viewCount
    this.textualUploadDate = textualUploadDate
}

private class StreamItemPreviewProvider : PreviewParameterProvider<StreamInfoItem> {
    override val values = sequenceOf(
        StreamInfoItem(streamType = StreamType.NONE),
        StreamInfoItem(streamType = StreamType.LIVE_STREAM),
        StreamInfoItem(streamType = StreamType.AUDIO_LIVE_STREAM),
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamGridItemPreview(
    @PreviewParameter(StreamItemPreviewProvider::class) stream: StreamInfoItem
) {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StreamGridItem(stream)
        }
    }
}
