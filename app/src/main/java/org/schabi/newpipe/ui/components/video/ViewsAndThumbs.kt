package org.schabi.newpipe.ui.components.video

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID

@Composable
fun ViewAndThumbs(info: StreamInfo) {
    Column(
        modifier = Modifier.padding(start = 6.dp)
    ) {
        if (info.viewCount >= 0) {
            // View count
            val viewCount = when (info.streamType) {
                StreamType.AUDIO_LIVE_STREAM -> {
                    Localization.listeningCount(LocalContext.current, info.viewCount)
                }

                StreamType.LIVE_STREAM -> {
                    Localization.localizeWatchingCount(LocalContext.current, info.viewCount)
                }

                else -> {
                    Localization.localizeViewCount(LocalContext.current, info.viewCount)
                }
            }
            Text(
                text = viewCount,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 5.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val showLikes = info.likeCount >= 0
            val showDislikes = info.dislikeCount >= 0
            val showDisabled = !showLikes && !showDislikes

            // Like icon
            if (showLikes || showDisabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_thumb_up),
                    contentDescription = stringResource(R.string.detail_likes_img_view_description),
                    modifier = Modifier.size(16.dp)
                )
            }
            if (showLikes) {
                Text(
                    text = Localization.shortCount(LocalContext.current, info.likeCount),
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }

            if (showLikes || showDisabled) {
                Spacer(Modifier.size(8.dp))
            }

            // Dislike icon
            if (showDislikes || showDisabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_thumb_down),
                    contentDescription = stringResource(R.string.detail_dislikes_img_view_description),
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showDislikes) {
                Text(
                    text = Localization.shortCount(LocalContext.current, info.dislikeCount),
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }

            if (showDisabled) {
                Text(
                    text = "Disabled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun Preview(info: StreamInfo) {
    AppTheme {
        Surface {
            ViewAndThumbs(info)
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewNormal() {
    Preview(
        StreamInfo(NO_SERVICE_ID, "", "", StreamType.VIDEO_STREAM, "", "", 0).apply {
            viewCount = 1247
            likeCount = 1290
            dislikeCount = 327
        }
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewLikes() {
    Preview(
        StreamInfo(NO_SERVICE_ID, "", "", StreamType.VIDEO_STREAM, "", "", 0).apply {
            viewCount = 1247
            likeCount = 1290
            dislikeCount = -1
        }
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewDislikes() {
    Preview(
        StreamInfo(NO_SERVICE_ID, "", "", StreamType.VIDEO_STREAM, "", "", 0).apply {
            viewCount = 1247
            likeCount = -1
            dislikeCount = 327
        }
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewDisabled() {
    Preview(
        StreamInfo(NO_SERVICE_ID, "", "", StreamType.VIDEO_STREAM, "", "", 0).apply {
            viewCount = -1
            likeCount = -1
            dislikeCount = -1
        }
    )
}
