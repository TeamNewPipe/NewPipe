package org.schabi.newpipe.compose.playlist

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.compose.status.LoadingIndicator
import org.schabi.newpipe.compose.stream.StreamList
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.PlaylistViewModel

@Composable
fun Playlist(playlistViewModel: PlaylistViewModel = viewModel()) {
    Surface(color = MaterialTheme.colorScheme.background) {
        val playlistInfo by playlistViewModel.playlistInfo.collectAsState()

        playlistInfo?.let {
            val streams = playlistViewModel.streamItems.collectAsLazyPagingItems()
            val totalDuration by remember {
                derivedStateOf {
                    streams.itemSnapshotList.sumOf { it!!.duration }
                }
            }

            StreamList(
                streams = streams,
                gridHeader = {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PlaylistHeader(it, totalDuration)
                    }
                },
                listHeader = {
                    item {
                        PlaylistHeader(it, totalDuration)
                    }
                }
            )
        } ?: LoadingIndicator()
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaylistPreview() {
    NewPipe.init(DownloaderImpl.init(null))

    val params = mapOf(
        KEY_SERVICE_ID to ServiceList.YouTube.serviceId,
        KEY_URL to "https://www.youtube.com/playlist?list=PLAIcZs9N4171hRrG_4v32Ca2hLvSuQ6QI"
    )
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Playlist(PlaylistViewModel(SavedStateHandle(params)))
        }
    }
}
