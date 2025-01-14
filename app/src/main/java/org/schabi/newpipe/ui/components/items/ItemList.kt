package org.schabi.newpipe.ui.components.items

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.items.playlist.PlaylistListItem
import org.schabi.newpipe.ui.components.items.stream.StreamListItem
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.NavigationHelper

@Composable
fun ItemList(
    items: List<InfoItem>,
    mode: ItemViewMode = determineItemViewMode(),
    listHeader: LazyListScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val onClick = remember {
        { item: InfoItem ->
            val fragmentManager = context.findFragmentActivity().supportFragmentManager
            if (item is StreamInfoItem) {
                NavigationHelper.openVideoDetailFragment(
                    context, fragmentManager, item.serviceId, item.url, item.name, null, false
                )
            } else if (item is PlaylistInfoItem) {
                NavigationHelper.openPlaylistFragment(
                    fragmentManager, item.serviceId, item.url, item.name
                )
            }
        }
    }

    // Handle long clicks for stream items
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

    val showProgress = DependentPreferenceHelper.getPositionsInListsEnabled(context)
    val nestedScrollModifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())

    if (mode == ItemViewMode.GRID) {
        // TODO: Implement grid layout using LazyVerticalGrid and LazyVerticalGridScrollbar.
    } else {
        val state = rememberLazyListState()

        LazyColumnThemedScrollbar(state = state) {
            LazyColumn(modifier = nestedScrollModifier, state = state) {
                listHeader()

                items(items.size) {
                    val item = items[it]

                    if (item is StreamInfoItem) {
                        val isSelected = selectedStream == item
                        StreamListItem(
                            item, showProgress, isSelected, onClick, onLongClick, onDismissPopup
                        )
                    } else if (item is PlaylistInfoItem) {
                        PlaylistListItem(item, onClick)
                    }
                }
            }
        }
    }
}
