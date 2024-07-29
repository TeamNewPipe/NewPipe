package org.schabi.newpipe.ui.screens

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.components.playlist.PlaylistHeader
import org.schabi.newpipe.ui.components.playlist.PlaylistInfo
import org.schabi.newpipe.ui.components.stream.StreamInfoItem
import org.schabi.newpipe.ui.components.stream.StreamList
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.viewmodels.PlaylistViewModel

@Composable
fun PlaylistScreen(playlistViewModel: PlaylistViewModel = viewModel()) {
    Surface(color = MaterialTheme.colorScheme.background) {
        val playlistInfo by playlistViewModel.playlistInfo.collectAsState()
        PlaylistScreen(playlistInfo, playlistViewModel.streamItems)
    }
}

@Composable
private fun PlaylistScreen(
    playlistInfo: PlaylistInfo?,
    streamFlow: Flow<PagingData<StreamInfoItem>>
) {
    playlistInfo?.let {
        val streams = streamFlow.collectAsLazyPagingItems()

        // Paging's load states only indicate when loading is currently happening, not if it can/will
        // happen. As such, the duration initially displayed will be the incomplete duration if more
        // items can be loaded.
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

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaylistPreview() {
    val description = Description("Example description", Description.PLAIN_TEXT)
    val playlistInfo = PlaylistInfo(
        "", 1, "", "Example playlist", description, listOf(), 1L,
        null, "Uploader", listOf(), null
    )
    val stream = StreamInfoItem(streamType = StreamType.VIDEO_STREAM)
    val streamFlow = flowOf(PagingData.from(listOf(stream)))

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PlaylistScreen(playlistInfo, streamFlow)
        }
    }
}
