package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.ui.components.VideoItem
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.LocalPlaylistDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistDetailScreen(
    playlistId: Long,
    modifier: Modifier = Modifier,
    viewModel: LocalPlaylistDetailViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val playlistName by viewModel.playlistName.collectAsStateWithLifecycle()
    val streams by viewModel.playlistStreams.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (streams.isNotEmpty()) {
                        IconButton(onClick = {
                            // Play playlist (start from first video)
                            navigator.navigateTo(AppDestination.VideoDetail(url = streams.first().url, title = streams.first().name, playlistId = playlistId))
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (streams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Playlist is empty.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(streams, key = { index, item -> "${item.url}_$index" }) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoItem(
                            item = item,
                            onClick = {
                                navigator.navigateTo(AppDestination.VideoDetail(url = item.url, title = item.name, playlistId = playlistId))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.removeVideo(index) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}
