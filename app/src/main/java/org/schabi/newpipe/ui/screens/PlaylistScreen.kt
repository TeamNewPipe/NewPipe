package org.schabi.newpipe.ui.screens

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.components.items.ItemList
import org.schabi.newpipe.ui.components.items.stream.StreamInfoItem
import org.schabi.newpipe.ui.components.playlist.PlaylistHeader
import org.schabi.newpipe.ui.components.playlist.PlaylistInfo
import org.schabi.newpipe.ui.emptystate.EmptyStateComposable
import org.schabi.newpipe.ui.emptystate.EmptyStateSpec
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.viewmodels.PlaylistViewModel
import org.schabi.newpipe.viewmodels.util.Resource

@Composable
fun PlaylistScreen(playlistViewModel: PlaylistViewModel = viewModel()) {
    Surface(color = MaterialTheme.colorScheme.background) {
        val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        PlaylistScreen(uiState, playlistViewModel.streamItems)
    }
}

@Composable
private fun PlaylistScreen(
    uiState: Resource<PlaylistInfo>,
    streamFlow: Flow<PagingData<StreamInfoItem>>
) {
    when (uiState) {
        is Resource.Success -> {
            val info = uiState.data
            val streams = streamFlow.collectAsLazyPagingItems()

            // Paging's load states only indicate when loading is currently happening, not if it can/will
            // happen. As such, the duration initially displayed will be the incomplete duration if more
            // items can be loaded.
            val totalDuration by remember {
                derivedStateOf {
                    streams.itemSnapshotList.sumOf { it?.duration ?: 0 }
                }
            }

            ItemList(streams, header = { PlaylistHeader(info, totalDuration) })
        }

        is Resource.Loading -> {
            LoadingIndicator()
        }

        is Resource.Error -> {
            // TODO use error panel instead
            EmptyStateComposable(
                EmptyStateSpec.DisabledComments.copy(
                    descriptionText = { stringResource(R.string.error_unable_to_load_streams) },
                )
            )
        }
    }
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
            PlaylistScreen(Resource.Success(playlistInfo), streamFlow)
        }
    }
}
