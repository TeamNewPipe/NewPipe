package org.schabi.newpipe.ui.components.stream

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.paging.compose.LazyPagingItems
import androidx.preference.PreferenceManager
import androidx.window.core.layout.WindowWidthSizeClass
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.util.NavigationHelper

@Composable
fun StreamList(
    streams: LazyPagingItems<StreamInfoItem>,
    itemViewMode: ItemViewMode = determineItemViewMode(),
    gridHeader: LazyGridScope.() -> Unit = {},
    listHeader: LazyListScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val onClick = remember {
        { stream: StreamInfoItem ->
            NavigationHelper.openVideoDetailFragment(
                context, (context as FragmentActivity).supportFragmentManager,
                stream.serviceId, stream.url, stream.name, null, false
            )
        }
    }

    // Handle long clicks
    // TODO: Adjust the menu display depending on where it was triggered
    var selectedStream by remember { mutableStateOf<StreamInfoItem?>(null) }
    val onLongClick = remember {
        { stream: StreamInfoItem ->
            selectedStream = stream
        }
    }
    val onDismissPopup = remember {
        {
            selectedStream = null
        }
    }

    if (itemViewMode == ItemViewMode.GRID) {
        val gridState = rememberLazyGridState()

        LazyVerticalGridScrollbar(state = gridState) {
            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            val isCompact = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
            val minSize = if (isCompact) 150.dp else 250.dp

            LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(minSize)) {
                gridHeader()

                items(streams.itemCount) {
                    val stream = streams[it]!!
                    StreamGridItem(
                        stream, selectedStream == stream, isCompact, onClick, onLongClick,
                        onDismissPopup
                    )
                }
            }
        }
    } else {
        // Card or list views
        val listState = rememberLazyListState()

        LazyColumnScrollbar(state = listState) {
            LazyColumn(state = listState) {
                listHeader()

                items(streams.itemCount) {
                    val stream = streams[it]!!
                    val isSelected = selectedStream == stream

                    if (itemViewMode == ItemViewMode.CARD) {
                        StreamCardItem(stream, isSelected, onClick, onLongClick, onDismissPopup)
                    } else {
                        StreamListItem(stream, isSelected, onClick, onLongClick, onDismissPopup)
                    }
                }
            }
        }
    }
}

@Composable
private fun determineItemViewMode(): ItemViewMode {
    val listMode = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        .getString(
            stringResource(R.string.list_view_mode_key),
            stringResource(R.string.list_view_mode_value)
        )

    return when (listMode) {
        stringResource(R.string.list_view_mode_list_key) -> ItemViewMode.LIST
        stringResource(R.string.list_view_mode_grid_key) -> ItemViewMode.GRID
        stringResource(R.string.list_view_mode_card_key) -> ItemViewMode.CARD
        else -> {
            // Auto mode - evaluate whether to use Grid based on screen real estate.
            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
                ItemViewMode.GRID
            } else {
                ItemViewMode.LIST
            }
        }
    }
}
