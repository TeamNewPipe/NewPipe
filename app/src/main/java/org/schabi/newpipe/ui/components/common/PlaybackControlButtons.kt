package org.schabi.newpipe.ui.components.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
fun PlaybackControlButtons(queue: PlayQueue) {
    val context = LocalContext.current
    var option by rememberSaveable { mutableStateOf<PlaybackOption?>(null) }

    LaunchedEffect(option) {
        when (option) {
            PlaybackOption.STANDARD -> playOnMainPlayer(context.findFragmentActivity(), queue)
            PlaybackOption.BACKGROUND -> playOnBackgroundPlayer(context, queue, true)
            PlaybackOption.POPUP -> playOnPopupPlayer(context, queue, false)
            else -> {}
        }
    }

    PlaybackControlButtons(
        option = option,
        onSelectOption = { option = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackControlButtons(
    option: PlaybackOption?,
    onSelectOption: (PlaybackOption) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(R.string.playback_options_label))

        SingleChoiceSegmentedButtonRow {
            PlaybackOption.entries.forEachIndexed { index, key ->
                val tooltipState = rememberTooltipState()

                TooltipBox(
                    positionProvider = rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                    tooltip = {
                        RichTooltip {
                            Text(text = stringResource(key.title))
                        }
                    },
                    state = tooltipState
                ) {
                    SegmentedButton(
                        selected = key == option,
                        onClick = { onSelectOption(key) },
                        shape = SegmentedButtonDefaults
                            .itemShape(index = index, count = PlaybackOption.entries.size)
                    ) {
                        Icon(
                            imageVector = key.icon,
                            contentDescription = stringResource(key.title)
                        )
                    }
                }
            }
        }
    }
}

enum class PlaybackOption(@StringRes val title: Int, val icon: ImageVector) {
    STANDARD(R.string.controls_standard_title, Icons.Default.PlayArrow),
    BACKGROUND(R.string.controls_background_title, Icons.AutoMirrored.Default.PlaylistPlay),
    POPUP(R.string.controls_popup_title, Icons.Default.PictureInPicture)
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlaybackControlButtonsPreview() {
    PlaybackControlButtons(
        option = PlaybackOption.STANDARD,
        onSelectOption = {}
    )
}
