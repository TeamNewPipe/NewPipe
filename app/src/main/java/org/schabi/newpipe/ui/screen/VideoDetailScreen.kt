package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import android.content.res.Configuration
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.app.Activity
import org.schabi.newpipe.util.external_communication.ShareUtils
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.R
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ui.components.AndroidMediaControllerCard
import org.schabi.newpipe.ui.components.VideoItem
import org.schabi.newpipe.ui.components.CompactVideoItem
import org.schabi.newpipe.ui.components.formatViewCount
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.VideoDetailViewModel
import org.schabi.newpipe.ui.viewmodel.LocalPlaylistsViewModel
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.ui.components.DownloadDialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlin.time.Duration.Companion.milliseconds

@Suppress("EffectKeys")
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    url: String,
    playlistId: Long? = null,
    modifier: Modifier = Modifier,
    viewModel: VideoDetailViewModel = koinViewModel(),
    playlistsViewModel: LocalPlaylistsViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    var currentUrl by remember(url) { mutableStateOf(url) }
    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val localContext = LocalContext.current
    val isPipMode = rememberIsInPipMode()
    var isAudioOnly by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current

    LaunchedEffect(isLandscape) {
        val activity = localContext as? Activity
        if (activity != null) {
            val window = activity.window
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (isLandscape) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Gesture states
    var isSpeeding by remember { mutableStateOf(false) }
    var doubleTapState by remember { mutableStateOf<Pair<Boolean, Long>?>(null) } // isLeft, timestamp
    var selectedVideoStream by remember { mutableStateOf<org.schabi.newpipe.extractor.stream.VideoStream?>(null) }
    var selectedAudioStream by remember { mutableStateOf<org.schabi.newpipe.extractor.stream.AudioStream?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val exoPlayer = remember { org.schabi.newpipe.player.PlayerManager.getOrCreatePlayer(localContext) }
    val playerDataSource = remember { org.schabi.newpipe.player.helper.PlayerDataSource(localContext, null) }

    LaunchedEffect(Unit) {
        val intent = android.content.Intent(localContext, org.schabi.newpipe.player.PlaybackService::class.java)
        try {
            localContext.startService(intent)
        } catch (_: Exception) {}

        val activity = localContext as? Activity
        if (activity != null) {
            org.schabi.newpipe.util.PermissionHelper.checkPostNotificationsPermission(activity, org.schabi.newpipe.util.PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE)
        }
    }

    // Update progress
    LaunchedEffect(isPlaying, isSeeking) {
        if (isPlaying && !isSeeking) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                delay(500.milliseconds)
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, isPipMode) {
        if (isPipMode) {
            showControls = false
        } else if (showControls && isPlaying) {
            delay(3000.milliseconds)
            showControls = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    isBuffering = false
                } else if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                    isBuffering = true
                } else if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    isBuffering = false
                    viewModel.playNext()
                } else {
                    isBuffering = false
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) showControls = true
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(currentUrl, playlistId) {
        viewModel.loadVideo(ServiceHelper.getSelectedServiceId(localContext), currentUrl, playlistId)
    }

    LaunchedEffect(Unit) {
        viewModel.nextVideoUrl.collect { nextUrl ->
            currentUrl = nextUrl
        }
    }

    LaunchedEffect(streamInfo, currentUrl) {
        streamInfo?.let { info ->
            org.schabi.newpipe.player.PlayerManager.setCurrentVideo(currentUrl, info, localContext)
        }
    }

    if (showDownloadDialog && !isPipMode) {
        streamInfo?.let { info ->
            DownloadDialog(
                streamInfo = info,
                onDismiss = { showDownloadDialog = false },
                onDownload = { downloadOptions ->
                    showDownloadDialog = false
                    org.schabi.newpipe.download.DownloadHelper.startDownload(localContext, info, downloadOptions)
                }
            )
        }
    }

    if (showQualitySelector && !isPipMode) {
        val currentInfo = streamInfo
        val streams = remember(currentInfo) {
            if (currentInfo != null) {
                org.schabi.newpipe.util.ListHelper.getSortedStreamVideosList(
                    localContext,
                    currentInfo.videoStreams,
                    currentInfo.videoOnlyStreams,
                    false,
                    true
                )
            } else emptyList()
        }

        ModalBottomSheet(
            onDismissRequest = { showQualitySelector = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quality for current video",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn {
                    items(streams) { stream ->
                        val isSelected = stream == selectedVideoStream
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stream.resolution,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.clickable {
                                selectedVideoStream = stream
                                showQualitySelector = false
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    var showPlaylistSelector by remember { mutableStateOf(false) }

    if (showDownloadDialog && streamInfo != null) {
        org.schabi.newpipe.ui.components.DownloadDialog(
            streamInfo = streamInfo!!,
            onDismiss = { showDownloadDialog = false },
            onDownload = { options ->
                showDownloadDialog = false
                org.schabi.newpipe.download.DownloadHelper.startDownload(localContext, streamInfo!!, options)
            }
        )
    }

    if (showPlaylistSelector && !isPipMode) {
        val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
        var newPlaylistName by remember { mutableStateOf("") }
        var isCreating by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showPlaylistSelector = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    text = "Save to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isCreating) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { isCreating = false }) { Text("Cancel") }
                        Button(onClick = {
                            if (newPlaylistName.isNotBlank() && streamInfo != null) {
                                viewModel.createAndSaveToPlaylist(newPlaylistName, streamInfo!!) {
                                    Toast.makeText(localContext, "Saved to $newPlaylistName", Toast.LENGTH_SHORT).show()
                                    showPlaylistSelector = false
                                }
                            }
                        }) { Text("Create & Save") }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.orderingName ?: "Unknown") },
                                modifier = Modifier.clickable {
                                    if (streamInfo != null) {
                                        viewModel.saveVideoToPlaylist(playlist.uid, streamInfo!!) {
                                            Toast.makeText(localContext, "Saved to ${playlist.orderingName ?: "Unknown"}", Toast.LENGTH_SHORT).show()
                                            showPlaylistSelector = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create New Playlist")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading && streamInfo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else {

            // Dynamic Modifier: Fills screen in landscape, maintains 16:9 in portrait (or wraps card in audio mode)
            val videoModifier = if (isLandscape || isPipMode) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else if (isAudioOnly) {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            }

            // Player Area
            Box(
                modifier = videoModifier,
                contentAlignment = Alignment.Center
            ) {
                val currentInfo = streamInfo
                if (isAudioOnly && currentInfo != null) {
                    AndroidMediaControllerCard(
                        streamInfo = currentInfo,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPosition,
                        durationMs = duration,
                        onPlayPauseClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onSeekTo = { pos ->
                            currentPosition = pos
                            exoPlayer.seekTo(pos)
                        },
                        onNextClick = {
                            viewModel.playNext()
                        },
                        onPreviousClick = {
                            exoPlayer.seekTo(0)
                        }
                    )
                } else if (currentInfo != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        update = { playerView ->
                            if (playerView.player != exoPlayer) {
                                playerView.player = exoPlayer
                            }
                        },
                        onReset = { playerView ->
                            playerView.player = null
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isPipMode) {
                                if (isPipMode) return@pointerInput
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                    },
                                    onDoubleTap = { offset ->
                                        val halfWidth = size.width / 2
                                        val seekAmount = 10000L
                                        val currentPos = exoPlayer.currentPosition
                                        val isLeft = offset.x < halfWidth
                                        if (isLeft) {
                                            exoPlayer.seekTo((currentPos - seekAmount).coerceAtLeast(0))
                                        } else {
                                            exoPlayer.seekTo((currentPos + seekAmount).coerceAtMost(exoPlayer.duration))
                                        }
                                        doubleTapState = Pair(isLeft, System.currentTimeMillis())
                                        showControls = false
                                    },
                                    onPress = { offset ->
                                        // Wait for long press timeout
                                        val pressStart = System.currentTimeMillis()
                                        val job = scope.launch {
                                            kotlinx.coroutines.delay(viewConfiguration.longPressTimeoutMillis.milliseconds)
                                            if (exoPlayer.isPlaying) {
                                                isSpeeding = true
                                                exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(2f)
                                            }
                                        }
                                        tryAwaitRelease()
                                        job.cancel()
                                        if (isSpeeding) {
                                            isSpeeding = false
                                            exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(1f)
                                        }
                                    }
                                )
                            }
                            .pointerInput(isPipMode) {
                                if (isPipMode) return@pointerInput
                                var totalDrag = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        totalDrag += dragAmount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        val activity = localContext as? Activity
                                        if (totalDrag < -150f) {
                                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        } else if (totalDrag > 150f) {
                                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    }
                                )
                            }
                            // NEW: Slide to Seek everywhere on the player (YouTube style)
                            .pointerInput(isPipMode) {
                                if (isPipMode) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        isSeeking = true
                                        showControls = true
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val dragRatio = dragAmount / size.width
                                        val seekAmount = (dragRatio * duration).toLong()
                                        currentPosition = (currentPosition + seekAmount).coerceIn(0L, duration)
                                    },
                                    onDragEnd = {
                                        isSeeking = false
                                        exoPlayer.seekTo(currentPosition)
                                    },
                                    onDragCancel = {
                                        isSeeking = false
                                        exoPlayer.seekTo(currentPosition)
                                    }
                                )
                            }
                    )
                    var lastPreparedSourceKey by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(currentInfo) {
                        if (currentInfo != null) {
                            if (selectedVideoStream == null) {
                                val allVideos = org.schabi.newpipe.util.ListHelper.getSortedStreamVideosList(
                                    localContext,
                                    currentInfo.videoStreams,
                                    currentInfo.videoOnlyStreams,
                                    false,
                                    true
                                )
                                selectedVideoStream = allVideos.find { it.resolution.contains("720p") }
                                    ?: allVideos.firstOrNull()
                                    ?: currentInfo.videoStreams?.firstOrNull()
                                    ?: currentInfo.videoOnlyStreams?.firstOrNull()
                            }
                            if (selectedAudioStream == null) {
                                selectedAudioStream = currentInfo.audioStreams?.firstOrNull()
                            }
                        }
                    }

                    LaunchedEffect(selectedVideoStream, selectedAudioStream, isAudioOnly, currentInfo) {
                        if (currentInfo == null) return@LaunchedEffect

                        val videoStream = if (isAudioOnly) null else (
                            selectedVideoStream ?: run {
                                val allVideos = org.schabi.newpipe.util.ListHelper.getSortedStreamVideosList(
                                    localContext,
                                    currentInfo.videoStreams,
                                    currentInfo.videoOnlyStreams,
                                    false,
                                    true
                                )
                                allVideos.find { it.resolution.contains("720p") }
                                    ?: allVideos.firstOrNull()
                                    ?: currentInfo.videoStreams?.firstOrNull()
                                    ?: currentInfo.videoOnlyStreams?.firstOrNull()
                            }
                        )
                        val audioStream = selectedAudioStream ?: currentInfo.audioStreams?.firstOrNull()

                        val videoUrl = videoStream?.content
                        val audioUrl = audioStream?.content

                        val sourceKey = "${videoUrl}_${audioUrl}_$isAudioOnly"
                        if (sourceKey == lastPreparedSourceKey) return@LaunchedEffect
                        lastPreparedSourceKey = sourceKey

                        val metadata = androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(currentInfo.name ?: "Unknown")
                            .setArtist(currentInfo.uploaderName ?: "Unknown")
                            .setArtworkUri(
                                (ImageStrategy.choosePreferredImage(
                                    currentInfo.thumbnails ?: emptyList(),
                                    PreferredImageQuality.HIGH
                                ) ?: "").toUri())
                            .setIsPlayable(true)
                            .build()

                        val tag = org.schabi.newpipe.player.mediaitem.StreamInfoTag.of(currentInfo)

                        val mediaSource = try {
                            if (isAudioOnly && audioStream != null) {
                                org.schabi.newpipe.player.resolver.PlaybackResolver.buildMediaSource(
                                    playerDataSource,
                                    audioStream,
                                    currentInfo,
                                    org.schabi.newpipe.player.resolver.PlaybackResolver.cacheKeyOf(currentInfo, audioStream),
                                    tag
                                )
                            } else if (videoStream != null) {
                                val videoSource = org.schabi.newpipe.player.resolver.PlaybackResolver.buildMediaSource(
                                    playerDataSource,
                                    videoStream,
                                    currentInfo,
                                    org.schabi.newpipe.player.resolver.PlaybackResolver.cacheKeyOf(currentInfo, videoStream),
                                    tag
                                )
                                if (videoStream.isVideoOnly && audioStream != null) {
                                    val audioSource = org.schabi.newpipe.player.resolver.PlaybackResolver.buildMediaSource(
                                        playerDataSource,
                                        audioStream,
                                        currentInfo,
                                        org.schabi.newpipe.player.resolver.PlaybackResolver.cacheKeyOf(currentInfo, audioStream),
                                        tag
                                    )
                                    androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
                                } else {
                                    videoSource
                                }
                            } else if (audioStream != null) {
                                org.schabi.newpipe.player.resolver.PlaybackResolver.buildMediaSource(
                                    playerDataSource,
                                    audioStream,
                                    currentInfo,
                                    org.schabi.newpipe.player.resolver.PlaybackResolver.cacheKeyOf(currentInfo, audioStream),
                                    tag
                                )
                            } else null
                        } catch (_: Exception) {
                            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                .setUserAgent(org.schabi.newpipe.DownloaderImpl.USER_AGENT)
                                .setConnectTimeoutMs(8000)
                                .setReadTimeoutMs(8000)
                                .setAllowCrossProtocolRedirects(true)
                                .setKeepPostFor302Redirects(true)
                            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(localContext, httpDataSourceFactory)
                            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

                            if (isAudioOnly && audioUrl != null) {
                                mediaSourceFactory.createMediaSource(MediaItem.Builder().setUri(audioUrl).setMediaMetadata(metadata).build())
                            } else if (videoUrl != null) {
                                val videoMediaItem = MediaItem.Builder().setUri(videoUrl).setMediaMetadata(metadata).build()
                                val videoSource = mediaSourceFactory.createMediaSource(videoMediaItem)
                                if (videoStream?.isVideoOnly == true && audioUrl != null) {
                                    val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
                                    androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
                                } else {
                                    videoSource
                                }
                            } else if (audioUrl != null) {
                                mediaSourceFactory.createMediaSource(MediaItem.Builder().setUri(audioUrl).setMediaMetadata(metadata).build())
                            } else null
                        }

                        if (mediaSource != null) {
                            val currentPos = exoPlayer.currentPosition
                            val wasPlaying = exoPlayer.playWhenReady
                            exoPlayer.setMediaSource(mediaSource)
                            exoPlayer.prepare()
                            if (currentPos > 0) {
                                exoPlayer.seekTo(currentPos)
                            }
                            exoPlayer.playWhenReady = if (currentPos == 0L) true else wasPlaying
                        }
                    }

                    if (isBuffering) {
                        CircularWavyProgressIndicator(
                            color = Color.Red,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Custom Controls Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls && !isPipMode,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Top Gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                        )
                                    )
                            )

                            // Bottom Gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                        )
                                    )
                            )

                            // Controls Content Wrapper: Adds padding for Cutouts and curved screens in landscape
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.displayCutout)
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                                    .padding(horizontal = if (isLandscape) 32.dp else 8.dp)
                            ) {
                                // Top Action Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(onClick = { navigator.navigateUp() }) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Minimize",
                                            tint = Color.White
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { showQualitySelector = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }

                                // Center Playback Controls
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                                ) {
                                    IconButton(
                                        onClick = { exoPlayer.seekTo(maxOf(0, currentPosition - 10000)) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous",
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { exoPlayer.seekTo(minOf(duration, currentPosition + 10000)) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next",
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }

                                // Bottom Control Bar
                                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatDuration(currentPosition)} / ${formatDuration(duration)}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        IconButton(
                                            onClick = {
                                                val activity = localContext as? Activity
                                                if (isLandscape) {
                                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                } else {
                                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                // Dynamically switch icon based on screen orientation
                                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                contentDescription = if (isLandscape) "Exit Fullscreen" else "Fullscreen",
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    // Interactive Slider for seeking
                                    Slider(
                                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                        onValueChange = { newValue ->
                                            isSeeking = true
                                            currentPosition = (newValue * duration).toLong()
                                        },
                                        onValueChangeFinished = {
                                            isSeeking = false
                                            exoPlayer.seekTo(currentPosition)
                                            showControls = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.Red,
                                            activeTrackColor = Color.Red,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // NEW: Hidden Progress Bar is now fully Scrubbable (YouTube Style)
                    if (!showControls && !isPipMode && !isLandscape) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(24.dp) // Invisible taller hit-box for easy grabbing
                                .pointerInput(duration) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        showControls = true
                                        isSeeking = true

                                        var newProgress = (down.position.x / size.width).coerceIn(0f, 1f)
                                        currentPosition = (newProgress * duration).toLong()

                                        // Track continuous dragging
                                        do {
                                            val event = awaitPointerEvent()
                                            val pointer = event.changes.first()
                                            newProgress = (pointer.position.x / size.width).coerceIn(0f, 1f)
                                            currentPosition = (newProgress * duration).toLong()
                                            pointer.consume()
                                        } while (event.changes.any { it.pressed })

                                        // Apply seek when finger is lifted
                                        isSeeking = false
                                        exoPlayer.seekTo(currentPosition)
                                    }
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            LinearWavyProgressIndicator(
                                progress = { if (duration > 0) currentPosition.toFloat() / duration else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = Color.Red,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Double Tap Ripple Animation
                    if (doubleTapState != null) {
                        val isLeft = doubleTapState!!.first
                        val timestamp = doubleTapState!!.second

                        var showRipple by remember(timestamp) { mutableStateOf(false) }
                        LaunchedEffect(timestamp) {
                            showRipple = true
                            kotlinx.coroutines.delay(500.milliseconds)
                            showRipple = false
                        }

                        if (showRipple) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                                    .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                                    .background(
                                        Color.White.copy(alpha = 0.2f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                            topStart = if (isLeft) 0.dp else 150.dp,
                                            bottomStart = if (isLeft) 0.dp else 150.dp,
                                            topEnd = if (isLeft) 150.dp else 0.dp,
                                            bottomEnd = if (isLeft) 150.dp else 0.dp
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (isLeft) Icons.Default.FastRewind else Icons.Default.FastForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text("10 seconds", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // 2x Speed Banner
                    if (isSpeeding) {
                        Text(
                            text = "Playing at 2x speed",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    val currentInfoThumbnail = streamInfo
                    AsyncImage(
                        model = currentInfoThumbnail?.thumbnails?.let { ImageStrategy.choosePreferredImage(it, PreferredImageQuality.HIGH) },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(onClick = { isPlaying = true }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            // Hide the details list when in Landscape (Fullscreen)
            if (!isLandscape && !isPipMode) {
                val currentInfoDetail = streamInfo
                if (currentInfoDetail != null) {
                    VideoDetailContent(
                        info = currentInfoDetail,
                        navigator = navigator,
                        isAudioOnly = isAudioOnly,
                        onAudioOnlyToggle = { isAudioOnly = !isAudioOnly },
                        onDownloadClick = { showDownloadDialog = true },
                        onSaveClick = {
                            showPlaylistSelector = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun rememberIsInPipMode(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is ComponentActivity) break
            currentContext = currentContext.baseContext
        }
        currentContext as? ComponentActivity
    }
    var isPip by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode ?: false
            } else {
                false
            }
        )
    }

    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isPip = info.isInPictureInPictureMode
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity?.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity?.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }

    return isPip
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailContent(
    info: StreamInfo,
    navigator: AppNavigator,
    isAudioOnly: Boolean,
    onAudioOnlyToggle: () -> Unit,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localContext = LocalContext.current
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isCommentsExpanded by remember { mutableStateOf(false) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var isCompactView by remember { mutableStateOf(true) }

    val rawItems = remember(info.relatedItems) {
        info.relatedItems?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>() ?: emptyList()
    }

    val filters = remember(info.uploaderName) {
        listOf("All", "From ${info.uploaderName ?: "Channel"}", "Related")
    }

    val displayItems = remember(rawItems, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> rawItems.filter { it.uploaderName.equals(info.uploaderName, ignoreCase = true) }.ifEmpty { rawItems }
            else -> rawItems
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 1. Video Title
                Text(
                    text = info.name ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 2. Views & Description Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val viewsText = formatViewCount(info.viewCount)
                            if (viewsText.isNotEmpty()) {
                                Text(
                                    text = viewsText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!info.textualUploadDate.isNullOrEmpty()) {
                                Text(
                                    text = if (viewsText.isNotEmpty()) "  •  ${info.textualUploadDate}" else info.textualUploadDate!!,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (isDescriptionExpanded) "Show less" else "...more",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isDescriptionExpanded && !info.description?.content.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.description?.content ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Channel Info Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageStrategy.choosePreferredImage(info.uploaderAvatars),
                        contentDescription = info.uploaderName,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.uploaderName ?: "Unknown",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (info.uploaderSubscriberCount > 0) {
                            Text(
                                text = "${formatViewCount(info.uploaderSubscriberCount).replace("views", "")}subscribers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { Toast.makeText(localContext, "Subscribed to ${info.uploaderName}", Toast.LENGTH_SHORT).show() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Subscribe", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Action Buttons Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionPillButton(
                        imageVector = Icons.Default.ThumbUp,
                        label = if (info.likeCount > 0) formatViewCount(info.likeCount).replace("views", "").trim() else "Like",
                        onClick = { Toast.makeText(localContext, "Liked video", Toast.LENGTH_SHORT).show() }
                    )
                    ActionPillButton(
                        imageVector = Icons.Default.Share,
                        label = "Share",
                        onClick = { ShareUtils.shareText(localContext, info.name ?: "", info.url) }
                    )
                    ActionPillButton(
                        imageVector = Icons.Default.FileDownload,
                        label = "Download",
                        isHighlighted = true,
                        onClick = onDownloadClick
                    )
                    ActionPillButton(
                        imageVector = Icons.Default.Headset,
                        label = "Audio",
                        isActive = isAudioOnly,
                        onClick = onAudioOnlyToggle
                    )
                    ActionPillButton(
                        imageVector = Icons.Default.PictureInPicture,
                        label = "Popup",
                        onClick = {
                            val activity = localContext as? Activity
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val params = android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(android.util.Rational(16, 9))
                                    .build()
                                activity?.enterPictureInPictureMode(params)
                            }
                        }
                    )
                    ActionPillButton(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        label = "Save",
                        onClick = onSaveClick
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Comments Teaser Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isCommentsExpanded = !isCommentsExpanded }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Comments",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = if (isCommentsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCommentsExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Comments loading is available in online player.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 6. Section Header for Recommended / Up Next with Filter Chips & Layout Toggle
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (displayItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text("${displayItems.size}")
                            }
                        }
                    }

                    // Layout Mode Switcher (Compact List vs Large Card)
                    IconButton(
                        onClick = { isCompactView = !isCompactView },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompactView) Icons.Default.ViewAgenda else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = if (isCompactView) "Switch to Card View" else "Switch to Compact View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEachIndexed { index, title ->
                        FilterChip(
                            selected = selectedFilterIndex == index,
                            onClick = { selectedFilterIndex = index },
                            label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // 7. Render Recommendations
        if (displayItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No related videos found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(displayItems, key = { it.url }) { item ->
                if (isCompactView) {
                    CompactVideoItem(
                        item = item,
                        onClick = {
                            navigator.navigateTo(AppDestination.VideoDetail(url = item.url, title = item.name))
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                } else {
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

fun formatDuration(durationMs: Long): String {
    val totalSeconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun ActionPillButton(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isHighlighted: Boolean = false
) {
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isHighlighted -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isHighlighted -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = contentColor
            )
        }
    }
}