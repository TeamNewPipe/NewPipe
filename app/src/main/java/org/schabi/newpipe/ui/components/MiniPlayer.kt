package org.schabi.newpipe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.PlayerManager
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    streamInfo: StreamInfo,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember { PlayerManager.getOrCreatePlayer(context) }
    
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                delay(1000.milliseconds)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val thumbnailUrl = remember(streamInfo) {
                    ImageStrategy.choosePreferredImage(streamInfo.thumbnails, PreferredImageQuality.HIGH)
                }
                if (!thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Text info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = streamInfo.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = streamInfo.uploaderName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Controls
            IconButton(
                onClick = { 
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play() 
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }
            
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
        
        // Progress bar
        LinearWavyProgressIndicator(
            progress = { if (duration > 0) currentPosition.toFloat() / duration else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = Color.Red,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
