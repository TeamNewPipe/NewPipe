package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.ui.components.formatDuration
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    downloadViewModel: DownloadViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val downloadItems by downloadViewModel.downloadItems.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Downloads", fontWeight = FontWeight.Bold)
                        if (downloadItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text("${downloadItems.size}")
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (downloadItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Downloads Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Downloaded videos and audios will appear here so you can watch or listen offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloadItems, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (!item.mission.source.isNullOrEmpty()) {
                                    navigator.navigateTo(AppDestination.VideoDetail(url = item.mission.source!!, title = item.title))
                                } else if (item.isFinished) {
                                    downloadViewModel.playMission(item.mission)
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. YouTube-style 16:9 Thumbnail Box
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!item.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = item.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (item.isAudio) Icons.Default.Audiotrack else Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    // Duration or Kind Badge
                                    if (item.duration > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Black.copy(alpha = 0.78f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = formatDuration(item.duration * 1000L),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // 2. Video Title & Channel Info
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.uploader,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 3. Action Buttons (Play / Pause / Delete)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.isFinished) {
                                        IconButton(
                                            onClick = {
                                                if (!item.mission.source.isNullOrEmpty()) {
                                                    navigator.navigateTo(AppDestination.VideoDetail(url = item.mission.source!!, title = item.title))
                                                } else {
                                                    downloadViewModel.playMission(item.mission)
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Play",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                if (item.isRunning) downloadViewModel.pauseMission(item.mission)
                                                else downloadViewModel.resumeMission(item.mission)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (item.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (item.isRunning) "Pause" else "Resume",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { downloadViewModel.deleteMission(item.mission) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 4. Downloading Progress Bar
                            if (!item.isFinished) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearWavyProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
