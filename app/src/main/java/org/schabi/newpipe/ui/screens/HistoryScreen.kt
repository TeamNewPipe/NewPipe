package org.schabi.newpipe.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.window.core.layout.WindowWidthSizeClass
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.local.history.HistoryViewModel
import org.schabi.newpipe.local.history.SortKey
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.defaultThemedScrollbarSettings
import org.schabi.newpipe.ui.components.items.determineItemViewMode
import org.schabi.newpipe.ui.components.items.history.HistoryCardItem
import org.schabi.newpipe.ui.components.items.history.HistoryGridItem
import org.schabi.newpipe.ui.components.items.history.HistoryListItem
import org.schabi.newpipe.ui.emptystate.EmptyStateComposable
import org.schabi.newpipe.ui.emptystate.EmptyStateSpec
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.NavigationHelper
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val historyItems = viewModel.historyItems.collectAsLazyPagingItems()
    val onSelectItem = viewModel::updateOrder

    HistoryScreen(historyItems, sortKey, onSelectItem)
}

@Composable
private fun HistoryScreen(
    items: LazyPagingItems<StreamStatisticsEntry>,
    sortKey: SortKey,
    onSelectItem: (SortKey) -> Unit,
) {
    val mode = determineItemViewMode()
    val context = LocalContext.current
    val onClick = remember {
        { item: StreamStatisticsEntry ->
            val fragmentManager = context.findFragmentActivity().supportFragmentManager
            NavigationHelper.openVideoDetailFragment(
                context, fragmentManager, item.streamEntity.serviceId, item.streamEntity.url,
                item.streamEntity.title, null, false
            )
        }
    }
    val dateTimeFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    // Handle long clicks for stream items
    // TODO: Adjust the menu display depending on where it was triggered
    var selectedStream by remember { mutableStateOf<StreamStatisticsEntry?>(null) }
    val onLongClick = remember {
        { stream: StreamStatisticsEntry ->
            selectedStream = stream
        }
    }
    val onDismissPopup = remember {
        {
            selectedStream = null
        }
    }

    val showProgress = DependentPreferenceHelper.getPositionsInListsEnabled(context)

    if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) {
        EmptyStateComposable(EmptyStateSpec.NoVideos)
    } else if (mode == ItemViewMode.GRID) {
        val state = rememberLazyGridState()

        LazyVerticalGridScrollbar(state = state, settings = defaultThemedScrollbarSettings()) {
            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            val isCompact = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
            val minSize = if (isCompact) 150.dp else 250.dp

            LazyVerticalGrid(state = state, columns = GridCells.Adaptive(minSize)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HistoryHeader(sortKey, onSelectItem)
                }

                items(items.itemCount) {
                    val item = items[it]!!
                    val isSelected = selectedStream == item

                    HistoryGridItem(
                        item, dateTimeFormatter, showProgress, isSelected, isCompact, onClick,
                        onLongClick, onDismissPopup
                    )
                }
            }
        }
    } else {
        val state = rememberLazyListState()

        LazyColumnThemedScrollbar(state = state) {
            LazyColumn(state = state) {
                item {
                    HistoryHeader(sortKey, onSelectItem)
                }

                items(items.itemCount) {
                    val item = items[it]!!
                    val isSelected = selectedStream == item

                    if (mode == ItemViewMode.CARD) {
                        HistoryCardItem(
                            item, dateTimeFormatter, showProgress, isSelected, onClick, onLongClick,
                            onDismissPopup
                        )
                    } else {
                        HistoryListItem(
                            item, dateTimeFormatter, showProgress, isSelected, onClick, onLongClick,
                            onDismissPopup
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryHeader(
    sortKey: SortKey,
    onSelectItem: (SortKey) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = when (sortKey) {
        SortKey.MOST_PLAYED -> R.string.title_most_played
        SortKey.LAST_PLAYED -> R.string.title_last_played
    }

    ExposedDropdownMenuBox(
        modifier = Modifier.padding(top = 12.dp, start = 12.dp),
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
            label = { Text(text = stringResource(R.string.history_sort_label)) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.title_most_played),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectItem(SortKey.MOST_PLAYED)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.title_last_played),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectItem(SortKey.LAST_PLAYED)
                }
            )
        }
    }
}
