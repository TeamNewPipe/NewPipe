package org.schabi.newpipe.ui.components.playlist

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.ui.components.common.DescriptionText
import org.schabi.newpipe.ui.components.common.PlaybackControlButtons
import org.schabi.newpipe.ui.components.items.stream.StreamInfoItem
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun PlaylistHeader(
    playlistScreenInfo: PlaylistScreenInfo,
    streams: List<StreamInfoItem?>,
) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = playlistScreenInfo.name, style = MaterialTheme.typography.titleMedium)

        // Paging's load states only indicate when loading is currently happening, not if it can/will
        // happen. As such, the duration initially displayed will be the incomplete duration if more
        // items can be loaded.
        PlaylistStats(playlistScreenInfo, streams.sumOf { it?.duration ?: 0 })

        if (playlistScreenInfo.description != Description.EMPTY_DESCRIPTION) {
            var isExpanded by rememberSaveable { mutableStateOf(false) }
            var isExpandable by rememberSaveable { mutableStateOf(false) }

            DescriptionText(
                modifier = Modifier.animateContentSize(),
                description = playlistScreenInfo.description,
                maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    if (it.hasVisualOverflow) {
                        isExpandable = true
                    }
                }
            )

            if (isExpandable) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(if (isExpanded) R.string.show_less else R.string.show_more)
                    )
                }
            }
        }

        PlaybackControlButtons(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            queue = SinglePlayQueue(streams.filterNotNull(), 0),
        )
    }
}

@Composable
private fun PlaylistStats(
    playlistScreenInfo: PlaylistScreenInfo,
    totalDuration: Long
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(
                playlistScreenInfo.uploaderName != null && playlistScreenInfo.uploaderUrl != null,
            ) {
                try {
                    NavigationHelper.openChannelFragment(
                        context.findFragmentActivity().supportFragmentManager,
                        playlistScreenInfo.serviceId,
                        playlistScreenInfo.uploaderUrl,
                        playlistScreenInfo.uploaderName!!,
                    )
                } catch (e: Exception) {
                    ErrorUtil.showUiErrorSnackbar(context, "Opening channel fragment", e)
                }
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val isMix = YoutubeParsingHelper.isYoutubeMixId(playlistScreenInfo.id) ||
                YoutubeParsingHelper.isYoutubeMusicMixId(playlistScreenInfo.id)
            val isYoutubeMix = playlistScreenInfo.serviceId == ServiceList.YouTube.serviceId && isMix
            val url = ImageStrategy.choosePreferredImage(playlistScreenInfo.uploaderAvatars)

            AsyncImage(
                model = url?.takeUnless { isYoutubeMix },
                contentDescription = stringResource(R.string.playlist_uploader_icon_description),
                placeholder = painterResource(R.drawable.placeholder_person),
                error = painterResource(R.drawable.placeholder_person),
                fallback = painterResource(if (isYoutubeMix) R.drawable.ic_radio else R.drawable.placeholder_person),
                modifier = Modifier
                    .size(24.dp)
                    .border(BorderStroke(1.dp, Color.White), CircleShape)
                    .padding(1.dp)
                    .clip(CircleShape),
            )

            val uploader = playlistScreenInfo.uploaderName.orEmpty()
                .ifEmpty { stringResource(R.string.playlist_no_uploader) }
            Text(text = uploader, style = MaterialTheme.typography.bodySmall)
        }

        val count = Localization.localizeStreamCount(context, playlistScreenInfo.streamCount)
        val formattedDuration = Localization.getDurationString(totalDuration, true, true)
        Text(text = "$count • $formattedDuration", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaylistHeaderPreview() {
    val description = Description("Example description", Description.PLAIN_TEXT)
    val playlistScreenInfo = PlaylistScreenInfo(
        id = "",
        serviceId = 1,
        url = "",
        name = "Example playlist",
        description = description,
        relatedItems = listOf(),
        streamCount = 1L,
        uploaderUrl = null,
        uploaderName = "Uploader",
        uploaderAvatars = listOf(),
        thumbnails = listOf(),
        nextPage = null
    )

    AppTheme {
        Surface {
            PlaylistHeader(
                playlistScreenInfo = playlistScreenInfo,
                streams = listOf(StreamInfoItem(streamType = StreamType.NONE)),
            )
        }
    }
}
