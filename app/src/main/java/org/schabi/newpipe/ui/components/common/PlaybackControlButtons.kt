package org.schabi.newpipe.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackControlButtons(
    queue: PlayQueue,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        IconButtonWithLabel(
            icon = Icons.Default.Headphones,
            label = R.string.controls_background_title,
            onClick = { NavigationHelper.playOnBackgroundPlayer(context, queue, false) },
        )

        IconButtonWithLabel(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            label = R.string.play_all,
            onClick = { NavigationHelper.playOnMainPlayer(context.findFragmentActivity(), queue) },
        )

        IconButtonWithLabel(
            icon = Icons.Default.PictureInPicture,
            label = R.string.controls_popup_title,
            onClick = { NavigationHelper.playOnPopupPlayer(context, queue, false) },
        )
    }
}
