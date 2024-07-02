package org.schabi.newpipe.compose.playlist

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.compose.stream.StreamCardItem
import org.schabi.newpipe.compose.stream.StreamGridItem
import org.schabi.newpipe.compose.stream.StreamListItem
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.compose.util.determineItemViewMode
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.viewmodels.PlaylistViewModel

@Composable
fun Playlist(playlistViewModel: PlaylistViewModel = viewModel()) {
    val playlistInfo by playlistViewModel.playlistInfo.collectAsState()
    val streams = playlistViewModel.streamItems.collectAsLazyPagingItems()
    val totalDuration by remember {
        derivedStateOf {
            streams.itemSnapshotList.sumOf { it!!.duration }
        }
    }

    val context = LocalContext.current
    val onClick = { stream: StreamInfoItem ->
        NavigationHelper.openVideoDetailFragment(
            context, (context as FragmentActivity).supportFragmentManager,
            stream.serviceId, stream.url, stream.name, null, false
        )
    }

    playlistInfo?.let {
        Surface(color = MaterialTheme.colorScheme.background) {
            val mode = determineItemViewMode()

            if (mode == ItemViewMode.GRID) {
                val gridState = rememberLazyGridState()

                LazyVerticalGridScrollbar(state = gridState) {
                    LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(250.dp)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PlaylistHeader(playlistInfo = it, totalDuration = totalDuration)
                        }

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
                        item {
                            PlaylistHeader(playlistInfo = it, totalDuration = totalDuration)
                        }

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
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaylistPreview() {
    NewPipe.init(DownloaderImpl.init(null))
    val params =
        mapOf(
            KEY_SERVICE_ID to ServiceList.YouTube.serviceId,
            KEY_URL to "https://www.youtube.com/playlist?list=PLAIcZs9N4171hRrG_4v32Ca2hLvSuQ6QI",
        )

    AppTheme {
        Playlist(PlaylistViewModel(SavedStateHandle(params)))
    }
}
