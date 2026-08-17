package org.schabi.newpipe.player

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.ThemeHelper

class PlayQueueActivity : ComponentActivity() {

    private var player: Player? = null
    private var serviceConnection: ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setDayNightMode(this)

        setContent {
            var playQueue by remember { mutableStateOf<PlayQueue?>(null) }

            DisposableEffect(Unit) {
                serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        if (service is PlayerService.LocalBinder) {
                            player = service.service?.player
                            playQueue = player?.playQueue
                        }
                    }
                    override fun onServiceDisconnected(name: ComponentName) {}
                }

                val intent = Intent(this@PlayQueueActivity, PlayerService::class.java).apply {
                    action = PlayerService.BIND_PLAYER_HOLDER_ACTION
                }
                bindService(intent, serviceConnection!!, BIND_AUTO_CREATE)

                onDispose {
                    serviceConnection?.let { unbindService(it) }
                }
            }

            PlayQueueScreen(playQueue = playQueue, onBack = { finish() })
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun PlayQueueScreen(
        playQueue: PlayQueue?,
        onBack: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("Play Queue") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (playQueue == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularWavyProgressIndicator()
                }
            } else {
                val items = remember { mutableStateListOf<PlayQueueItem>() }
                LaunchedEffect(playQueue) {
                    items.clear()
                    items.addAll(playQueue.getStreams())
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    items(items) { item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = { Text(item.uploader) }
                        )
                    }
                }
            }
        }
    }
}
