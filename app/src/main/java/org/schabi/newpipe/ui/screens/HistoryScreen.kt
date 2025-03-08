package org.schabi.newpipe.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.local.history.HistoryViewModel
import org.schabi.newpipe.local.history.SortKey
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.ui.components.common.DropdownTextMenuItem
import org.schabi.newpipe.ui.components.common.IconButtonWithLabel
import org.schabi.newpipe.ui.components.items.ItemList
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NavigationHelper

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val historyItems = viewModel.historyItems.collectAsLazyPagingItems()

    val streams = historyItems.itemSnapshotList.mapNotNull { it?.toStreamInfoItem() }
    val queue = SinglePlayQueue(streams, 0)
    val onClickBackground = {
        NavigationHelper.playOnBackgroundPlayer(context, queue, false)
    }
    val onClickPopup = {
        NavigationHelper.playOnPopupPlayer(context, queue, false)
    }
    val onClickPlayAll = {
        NavigationHelper.playOnMainPlayer(context.findFragmentActivity(), queue)
    }

    ItemList(historyItems, header = {
        HistoryHeader(
            sortKey = sortKey,
            onSelectSortKey = viewModel::updateOrder,
            onClickClear = viewModel::deleteWatchHistory,
            onClickBackground = onClickBackground,
            onClickPlayAll = onClickPlayAll,
            onClickPopup = onClickPopup,
        )
    })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryHeader(
    sortKey: SortKey,
    onSelectSortKey: (SortKey) -> Unit,
    onClickClear: () -> Unit,
    onClickBackground: () -> Unit,
    onClickPlayAll: () -> Unit,
    onClickPopup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            val selected = when (sortKey) {
                SortKey.MOST_PLAYED -> R.string.title_most_played
                SortKey.LAST_PLAYED -> R.string.title_last_played
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                TextField(
                    enabled = true,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    value = stringResource(selected),
                    readOnly = true,
                    onValueChange = {},
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    label = { Text(text = stringResource(R.string.history_sort_label)) },
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownTextMenuItem(
                        text = R.string.title_most_played,
                        onClick = {
                            expanded = false
                            onSelectSortKey(SortKey.MOST_PLAYED)
                        },
                    )
                    DropdownTextMenuItem(
                        text = R.string.title_last_played,
                        onClick = {
                            expanded = false
                            onSelectSortKey(SortKey.LAST_PLAYED)
                        },
                    )
                }
            }

            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip { Text(text = stringResource(R.string.clear_views_history_title)) }
                },
                state = rememberTooltipState(),
            ) {
                var openClearDialog by remember { mutableStateOf(false) }

                IconButton(onClick = { openClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = stringResource(R.string.clear_history_description),
                    )
                }

                ClearHistoryDialog(openClearDialog, onClickClear, onDismissRequest = { openClearDialog = false })
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            IconButtonWithLabel(
                icon = Icons.Default.Headphones,
                label = R.string.controls_background_title,
                onClick = onClickBackground,
            )

            IconButtonWithLabel(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                label = R.string.play_all,
                onClick = onClickPlayAll,
            )

            IconButtonWithLabel(
                icon = Icons.Default.PictureInPicture,
                label = R.string.controls_popup_title,
                onClick = onClickPopup,
            )
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    openClearDialog: Boolean,
    onClickClear: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (openClearDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(R.string.delete_view_history_alert)) },
            text = { Text(text = stringResource(R.string.delete_view_history_description)) },
            confirmButton = {
                TextButton(onClick = {
                    onClickClear()
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryHeaderPreview() {
    AppTheme {
        Surface {
            HistoryHeader(SortKey.MOST_PLAYED, {}, {}, {}, {}, {})
        }
    }
}
