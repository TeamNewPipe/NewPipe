package org.schabi.newpipe.compose.playlist

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.PlaylistViewModel

@Composable
fun Playlist(playlistViewModel: PlaylistViewModel = viewModel()) {
    val playlistInfo by playlistViewModel.playlistInfo.collectAsState()
    val streams = playlistViewModel.streamItems.collectAsLazyPagingItems()
    val totalDuration = streams.itemSnapshotList.sumOf { it!!.duration }

    playlistInfo?.let {
        Surface(color = MaterialTheme.colorScheme.background) {
            LazyColumn {
                item {
                    PlaylistHeader(playlistInfo = it, totalDuration = totalDuration)
                    HorizontalDivider(thickness = 1.dp)
                }

                items(streams.itemCount) {
                    Text(text = streams[it]!!.name)
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
