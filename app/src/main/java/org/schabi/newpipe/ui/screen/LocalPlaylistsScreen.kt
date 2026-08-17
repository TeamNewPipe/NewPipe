package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.LocalPlaylistsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistsScreen(
    modifier: Modifier = Modifier,
    viewModel: LocalPlaylistsViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Playlists") },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Playlist")
            }
        }
    ) { paddingValues ->
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No playlists created yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.uid }) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navigator.navigateTo(AppDestination.LocalPlaylistDetail(playlist.uid))
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.orderingName ?: "Unknown",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${playlist.streamCount} streams",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deletePlaylist(playlist.uid) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Playlist")
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            var newPlaylistName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create Playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName)
                                showCreateDialog = false
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
