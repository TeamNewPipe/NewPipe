package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Watch History",
                style = MaterialTheme.typography.headlineMedium
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("Clear All")
                }
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(history) { entry ->
                    HistoryItem(
                        entry = entry,
                        onClick = {
                            navigator.navigateTo(AppDestination.VideoDetail(url = entry.streamEntity.url, title = entry.streamEntity.title))
                        },
                        onDeleteClick = {
                            viewModel.deleteHistoryEntry(entry)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    entry: StreamStatisticsEntry,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.streamEntity.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.streamEntity.title ?: "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2
            )
            Text(
                text = entry.streamEntity.uploader ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
