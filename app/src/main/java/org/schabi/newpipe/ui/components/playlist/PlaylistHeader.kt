package org.schabi.newpipe.ui.components.playlist

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.components.common.DescriptionText
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy
import java.util.concurrent.TimeUnit

@Composable
fun PlaylistHeader(playlistInfo: PlaylistInfo, totalDuration: Long) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = playlistInfo.name, style = MaterialTheme.typography.titleMedium)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val clickable = playlistInfo.uploaderName != null && playlistInfo.uploaderUrl != null

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(clickable) {
                    try {
                        NavigationHelper.openChannelFragment(
                            (context as FragmentActivity).supportFragmentManager,
                            playlistInfo.serviceId, playlistInfo.uploaderUrl,
                            playlistInfo.uploaderName!!
                        )
                    } catch (e: Exception) {
                        ErrorUtil.showUiErrorSnackbar(context, "Opening channel fragment", e)
                    }
                }
            ) {
                val imageModifier = Modifier
                    .size(24.dp)
                    .border(BorderStroke(1.dp, Color.White), CircleShape)
                    .padding(1.dp)
                    .clip(CircleShape)
                val isMix = YoutubeParsingHelper.isYoutubeMixId(playlistInfo.id) ||
                    YoutubeParsingHelper.isYoutubeMusicMixId(playlistInfo.id)

                if (playlistInfo.serviceId == ServiceList.YouTube.serviceId && isMix) {
                    Image(
                        painter = painterResource(R.drawable.ic_radio),
                        contentDescription = null,
                        modifier = imageModifier
                    )
                } else {
                    AsyncImage(
                        model = ImageStrategy.choosePreferredImage(playlistInfo.uploaderAvatars),
                        contentDescription = null,
                        placeholder = painterResource(R.drawable.placeholder_person),
                        error = painterResource(R.drawable.placeholder_person),
                        modifier = imageModifier
                    )
                }

                val uploader = playlistInfo.uploaderName.orEmpty()
                    .ifEmpty { stringResource(R.string.playlist_no_uploader) }
                Text(text = uploader, style = MaterialTheme.typography.bodySmall)
            }

            val count = Localization.localizeStreamCount(context, playlistInfo.streamCount)
            val formattedDuration = Localization.getDurationString(totalDuration, true, true)
            Text(text = "$count • $formattedDuration", style = MaterialTheme.typography.bodySmall)
        }

        if (playlistInfo.description != Description.EMPTY_DESCRIPTION) {
            var isExpanded by rememberSaveable { mutableStateOf(false) }
            var isExpandable by rememberSaveable { mutableStateOf(false) }

            DescriptionText(
                modifier = Modifier.animateContentSize(),
                description = playlistInfo.description,
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
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaylistHeaderPreview() {
    val description = Description("Example description", Description.PLAIN_TEXT)
    val playlistInfo = PlaylistInfo(
        "", 1, "", "Example playlist", description, listOf(), 1L,
        null, "Uploader", listOf(), null
    )

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlaylistHeader(
                playlistInfo = playlistInfo,
                totalDuration = TimeUnit.HOURS.toSeconds(1)
            )
        }
    }
}
