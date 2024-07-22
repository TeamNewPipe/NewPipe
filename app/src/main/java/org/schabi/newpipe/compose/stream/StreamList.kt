package org.schabi.newpipe.compose.stream

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.paging.compose.LazyPagingItems
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import org.schabi.newpipe.compose.util.determineItemViewMode
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
            LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(250.dp)) {
                gridHeader()

                items(streams.itemCount) {
                    val stream = streams[it]!!
                    StreamGridItem(
                        stream, selectedStream == stream, onClick, onLongClick,
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
