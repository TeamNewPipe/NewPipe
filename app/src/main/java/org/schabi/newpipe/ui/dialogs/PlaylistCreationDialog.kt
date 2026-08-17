package org.schabi.newpipe.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager

@Composable
fun PlaylistCreationDialog(
    streamEntities: List<StreamEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlistManager = remember { LocalPlaylistManager(NewPipeDatabase.getInstance(context)) }
    
    var name by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        scope.launch {
                            try {
                                playlistManager.createPlaylist(name, streamEntities)
                                Toast.makeText(context, R.string.playlist_creation_success, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, R.string.general_error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
