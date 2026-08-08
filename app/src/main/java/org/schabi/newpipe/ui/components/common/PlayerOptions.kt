package org.schabi.newpipe.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPicture
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.preview.ThemePreviewProvider
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper.playOnBackgroundPlayer
import org.schabi.newpipe.util.NavigationHelper.playOnMainPlayer
import org.schabi.newpipe.util.NavigationHelper.playOnPopupPlayer
import org.schabi.newpipe.util.PermissionHelper

@Composable
fun PlayerOptions(
    queue: PlayQueue,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedOption by rememberSaveable { mutableStateOf(PlayerType.NONE) }

    LaunchedEffect(selectedOption) {
        when (selectedOption) {
            PlayerType.MAIN -> playOnMainPlayer(context.findFragmentActivity(), queue)
            PlayerType.BACKGROUND -> playOnBackgroundPlayer(context, queue, true)
            PlayerType.POPUP -> playOnPopupPlayer(context, queue, false)
            else -> {}
        }
    }

    PlayerOptions(
        modifier = modifier,
        selectedOption = selectedOption,
        onSelectOption = {
            if (it != PlayerType.POPUP || PermissionHelper.checkSystemAlertWindowPermission(context)) {
                selectedOption = it
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptions(
    selectedOption: PlayerType,
    onSelectOption: (PlayerType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(R.string.player_options_label))

        val validTypes = PlayerType.entries.filter { it != PlayerType.NONE }
        SingleChoiceSegmentedButtonRow {
            for ((index, key) in validTypes.withIndex()) {
                if (key == PlayerType.NONE) {
                    continue
                }

                val tooltipState = rememberTooltipState()
                val (title, icon) = when (key) {
                    PlayerType.MAIN -> R.string.controls_main_title to Icons.AutoMirrored.Filled.PlaylistPlay
                    PlayerType.BACKGROUND -> R.string.controls_background_title to Icons.Default.Headphones
                    PlayerType.POPUP -> R.string.controls_popup_title to Icons.Default.PictureInPicture
                }

                TooltipBox(
                    positionProvider = rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                    tooltip = {
                        RichTooltip {
                            Text(text = stringResource(title))
                        }
                    },
                    state = tooltipState
                ) {
                    SegmentedButton(
                        selected = key == selectedOption,
                        onClick = { onSelectOption(key) },
                        shape = SegmentedButtonDefaults
                            .itemShape(index = index, count = validTypes.size)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(title)
                        )
                    }
                }
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerOptionsPreview() {
    PlayerOptions(
        selectedOption = PlayerType.MAIN,
        onSelectOption = {}
    )
}
