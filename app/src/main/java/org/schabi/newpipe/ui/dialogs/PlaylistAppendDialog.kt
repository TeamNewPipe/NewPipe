package org.schabi.newpipe.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager

@Composable
fun PlaylistAppendDialog(
    streamEntities: List<StreamEntity>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlistManager = remember { LocalPlaylistManager(NewPipeDatabase.getInstance(context)) }
    
    var playlists by remember { mutableStateOf<List<PlaylistDuplicatesEntry>>(emptyList()) }
    
    LaunchedEffect(streamEntities) {
        if (streamEntities.isNotEmpty()) {
            playlistManager.getPlaylistDuplicates(streamEntities[0].url)
                .collectLatest { playlists = it }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlists)) },
        text = {
            Column {
                if (playlists.any { it.timesStreamIsContained > 0 }) {
                    Text(
                        text = stringResource(R.string.duplicate_in_playlist),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.orderingName ?: "") },
                            supportingContent = {
                                if (playlist.timesStreamIsContained > 0) {
                                    Text(stringResource(R.string.playlist_add_stream_success_duplicate, playlist.timesStreamIsContained.toInt()))
                                }
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    try {
                                        playlistManager.appendToPlaylist(playlist.uid, streamEntities)
                                        if (playlist.thumbnailStreamId == PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                                            playlistManager.changePlaylistThumbnail(playlist.uid, streamEntities[0].uid, false)
                                        }
                                        Toast.makeText(context, R.string.playlist_add_stream_success, Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, R.string.general_error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
                
                TextButton(
                    onClick = onCreatePlaylist,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.create_playlist))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
