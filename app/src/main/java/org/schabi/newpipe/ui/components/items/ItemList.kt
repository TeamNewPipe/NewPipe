package org.schabi.newpipe.ui.components.items

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.window.core.layout.WindowWidthSizeClass
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.defaultThemedScrollbarSettings
import org.schabi.newpipe.ui.components.items.playlist.PlaylistListItem
import org.schabi.newpipe.ui.components.items.stream.StreamCardItem
import org.schabi.newpipe.ui.components.items.stream.StreamGridItem
import org.schabi.newpipe.ui.components.items.stream.StreamListItem
import org.schabi.newpipe.ui.emptystate.EmptyStateComposable
import org.schabi.newpipe.ui.emptystate.EmptyStateSpec
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.NavigationHelper

@Composable
fun ItemList(
    items: LazyPagingItems<out Info>,
    mode: ItemViewMode = determineItemViewMode(),
    header: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val onClick = remember {
        { item: Info ->
            val fragmentManager = context.findFragmentActivity().supportFragmentManager
            if (item is Stream) {
                NavigationHelper.openVideoDetailFragment(
                    context, fragmentManager, item.serviceId, item.url, item.name, null, false
                )
            } else if (item is Playlist) {
                NavigationHelper.openPlaylistFragment(
                    fragmentManager, item.serviceId, item.url, item.name
                )
            }
        }
    }

    // Handle long clicks for stream items
    // TODO: Adjust the menu display depending on where it was triggered
    var selectedStream by rememberSaveable { mutableStateOf<Stream?>(null) }
    val onLongClick = remember {
        { stream: Stream ->
            selectedStream = stream
        }
    }
    val onDismissPopup = remember {
        {
            selectedStream = null
        }
    }

    val showProgress = DependentPreferenceHelper.getPositionsInListsEnabled(context)
    val nestedScrollModifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())

    if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) {
        EmptyStateComposable(EmptyStateSpec.NoVideos)
    } else if (mode == ItemViewMode.GRID) {
        val state = rememberLazyGridState()

        LazyVerticalGridScrollbar(state = state, settings = defaultThemedScrollbarSettings()) {
            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            val isCompact = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
            val minSize = if (isCompact) 150.dp else 250.dp

            LazyVerticalGrid(
                modifier = nestedScrollModifier,
                state = state,
                columns = GridCells.Adaptive(minSize)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    header()
                }

                items(items.itemCount) {
                    val item = items[it]!!
                    val isSelected = selectedStream == item

                    // TODO: Implement playlist and channel grid items.
                    if (item is Stream) {
                        StreamGridItem(
                            item, showProgress, isSelected, isCompact, onClick, onLongClick,
                            onDismissPopup
                        )
                    }
                }
            }
        }
    } else {
        val state = rememberLazyListState()

        LazyColumnThemedScrollbar(state = state) {
            LazyColumn(modifier = nestedScrollModifier, state = state) {
                item {
                    header()
                }

                items(items.itemCount) {
                    val item = items[it]

                    // TODO: Implement playlist and channel items.
                    if (item is Stream) {
                        val isSelected = selectedStream == item

                        if (mode == ItemViewMode.CARD) {
                            StreamCardItem(
                                item, showProgress, isSelected, onClick, onLongClick, onDismissPopup
                            )
                        } else {
                            StreamListItem(
                                item, showProgress, isSelected, onClick, onLongClick, onDismissPopup
                            )
                        }
                    } else if (item is Playlist) {
                        PlaylistListItem(item, onClick)
                    }
                }
            }
        }
    }
}
