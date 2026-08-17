package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ui.components.VideoItem
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.PlaylistViewModel
import org.schabi.newpipe.util.ServiceHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    url: String,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val playlistInfo by viewModel.playlistInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(url) {
        viewModel.loadPlaylist(ServiceHelper.getSelectedServiceId(context), url)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading && playlistInfo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else {
            playlistInfo?.let { info ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = info.name ?: "", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = "${info.streamCount} videos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val items = info.relatedItems ?: emptyList()
                    items(items) { item ->
                        if (item is StreamInfoItem) {
                            VideoItem(
                                item = item,
                                onClick = {
                                    navigator.navigateTo(AppDestination.VideoDetail(url = item.url, title = item.name))
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
