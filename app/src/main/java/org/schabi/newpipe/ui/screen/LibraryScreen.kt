package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.HistoryViewModel
import org.schabi.newpipe.ui.viewmodel.LocalPlaylistsViewModel
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun LibraryScreen(
    navigator: AppNavigator = koinInject(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    playlistsViewModel: LocalPlaylistsViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val historyEntries by historyViewModel.history.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // User Profile Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "User Avatar",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Local Library & Saved Content",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { navigator.navigateTo(AppDestination.Settings) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        // History Section
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyEntries.isNotEmpty()) {
                        TextButton(onClick = { navigator.navigateTo(AppDestination.History) }) {
                            Text("View all")
                        }
                    }
                }

                if (historyEntries.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyEntries.take(10)) { entry ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        navigator.navigateTo(AppDestination.VideoDetail(url = entry.streamEntity.url, title = entry.streamEntity.title))
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = ImageStrategy.dbUrlToImageList(entry.streamEntity.thumbnailUrl).firstOrNull()?.url ?: entry.streamEntity.thumbnailUrl,
                                        contentDescription = entry.streamEntity.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.streamEntity.title ?: "Unknown video",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.streamEntity.uploader ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No watch history yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Playlists Section
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { navigator.navigateTo(AppDestination.LocalPlaylists) }) {
                        Text("View all")
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists) { playlist ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .width(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    navigator.navigateTo(AppDestination.LocalPlaylistDetail(playlist.uid))
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = playlist.orderingName ?: "Playlist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.streamCount} streams",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Quick Action Items
        item {
            LibraryActionRow(
                icon = Icons.Default.FileDownload,
                title = "Downloads",
                subtitle = "Manage offline saved videos & songs",
                onClick = { navigator.navigateTo(AppDestination.Downloads) }
            )
        }

        item {
            LibraryActionRow(
                icon = Icons.Default.History,
                title = "Your Watch History",
                subtitle = "Recently viewed videos and statistics",
                onClick = { navigator.navigateTo(AppDestination.History) }
            )
        }

        item {
            LibraryActionRow(
                icon = Icons.Default.Settings,
                title = "Settings",
                subtitle = "Appearance, video player, and downloads",
                onClick = { navigator.navigateTo(AppDestination.Settings) }
            )
        }
    }
}

@Composable
fun LibraryActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        },
        headlineContent = {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    )
}

