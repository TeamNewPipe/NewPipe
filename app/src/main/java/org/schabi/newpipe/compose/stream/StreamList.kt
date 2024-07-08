package org.schabi.newpipe.compose.stream

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    gridHeader: LazyGridScope.() -> Unit = {},
    listHeader: LazyListScope.() -> Unit = {}
) {
    val mode = determineItemViewMode()
    val context = LocalContext.current
    val onClick = remember {
        { stream: StreamInfoItem ->
            NavigationHelper.openVideoDetailFragment(
                context, (context as FragmentActivity).supportFragmentManager,
                stream.serviceId, stream.url, stream.name, null, false
            )
        }
    }
    // TODO: Handle long-click by showing a dropdown menu instead of a dialog.

    if (mode == ItemViewMode.GRID) {
        val gridState = rememberLazyGridState()

        LazyVerticalGridScrollbar(state = gridState) {
            LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(250.dp)) {
                gridHeader()

                items(streams.itemCount) {
                    StreamGridItem(streams[it]!!, onClick)
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
                    if (mode == ItemViewMode.CARD) {
                        StreamCardItem(stream, onClick)
                    } else {
                        StreamListItem(stream, onClick)
                    }
                }
            }
        }
    }
}
