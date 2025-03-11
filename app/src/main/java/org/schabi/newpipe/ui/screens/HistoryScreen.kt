package org.schabi.newpipe.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        SingleChoiceSegmentedButtonRow {
            SortKey.entries.forEachIndexed { index, key ->
                SegmentedButton(
                    selected = key == sortKey,
                    onClick = { onSelectSortKey(key) },
                    shape = SegmentedButtonDefaults
                        .itemShape(index = index, count = SortKey.entries.size)
                ) {
                    Text(text = stringResource(key.title))
                }
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

            var openClearDialog by remember { mutableStateOf(false) }

            TextButton(onClick = { openClearDialog = true }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Default.ClearAll, contentDescription = null)
                    Text(text = stringResource(R.string.clear))
                }
            }

            ClearHistoryDialog(openClearDialog, onClickClear, onDismissRequest = { openClearDialog = false })
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
