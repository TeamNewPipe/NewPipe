package org.schabi.newpipe.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.HistoryViewModel
import org.schabi.newpipe.local.history.SortKey
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.ui.components.common.PlaybackControlButtons
import org.schabi.newpipe.ui.components.items.ItemList
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val historyItems = viewModel.historyItems.collectAsLazyPagingItems()
    val streams = historyItems.itemSnapshotList.mapNotNull { it?.toStreamInfoItem() }
    val queue = SinglePlayQueue(streams, 0)

    ItemList(historyItems, header = {
        HistoryHeader(
            sortKey = sortKey,
            onSelectSortKey = viewModel::updateOrder,
            queue = queue
        )
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryHeader(
    sortKey: SortKey,
    queue: PlayQueue,
    onSelectSortKey: (SortKey) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        HistorySortRow(sortKey, onSelectSortKey)

        PlaybackControlButtons(queue)
    }
}

@Composable
private fun HistorySortRow(
    sortKey: SortKey,
    onSelectSortKey: (SortKey) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.history_sort_label))

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
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryHeaderPreview() {
    AppTheme {
        Surface {
            HistoryHeader(SortKey.MOST_PLAYED, SinglePlayQueue(listOf(), 0)) {}
        }
    }
}
