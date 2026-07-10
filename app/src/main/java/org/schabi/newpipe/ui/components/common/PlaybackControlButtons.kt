package org.schabi.newpipe.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.preview.ThemePreviewProvider
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper.playOnBackgroundPlayer
import org.schabi.newpipe.util.NavigationHelper.playOnMainPlayer
import org.schabi.newpipe.util.NavigationHelper.playOnPopupPlayer

@Composable
fun PlaybackControlButtons(
    playQueue: PlayQueue,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    PlaybackControlButtons(
        onClickBackground = { playOnBackgroundPlayer(context, playQueue, false) },
        onClickPlayAll = { playOnMainPlayer(context.findFragmentActivity(), playQueue) },
        onClickPopup = { playOnPopupPlayer(context, playQueue, false) },
        modifier = modifier
    )
}

@Composable
fun PlaybackControlButtons(
    onClickBackground: () -> Unit,
    onClickPlayAll: () -> Unit,
    onClickPopup: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        IconButtonWithLabel(
            icon = Icons.Default.Headphones,
            label = R.string.controls_background_title,
            onClick = onClickBackground
        )

        IconButtonWithLabel(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            label = R.string.play_all,
            onClick = onClickPlayAll
        )

        IconButtonWithLabel(
            icon = Icons.Default.PictureInPicture,
            label = R.string.controls_popup_title,
            onClick = onClickPopup
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlaybackControlButtonsPreview() {
    PlaybackControlButtons(
        onClickBackground = {},
        onClickPopup = {},
        onClickPlayAll = {}
    )
}
