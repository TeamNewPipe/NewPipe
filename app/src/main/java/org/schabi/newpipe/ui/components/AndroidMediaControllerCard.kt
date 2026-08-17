package org.schabi.newpipe.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ui.screen.formatDuration
import org.schabi.newpipe.util.AudioOutputHelper
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality
import kotlin.math.PI
import kotlin.math.sin

/**
 * Android 13/14+ Material You Lockscreen & MediaStyle Notification Card.
 * Matches the native OS media controller with Output Switcher ("This phone"),
 * animated squiggly sinusoidal wave seekbar, thumbnail background, and pill play/pause button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AndroidMediaControllerCard(
    streamInfo: StreamInfo,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNextClick: (() -> Unit)? = null,
    onPreviousClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var audioOutputName by remember { mutableStateOf(AudioOutputHelper.getCurrentAudioOutputName(context)) }
    val audioOutputIcon = remember(audioOutputName) { AudioOutputHelper.getCurrentAudioOutputIcon(context) }

    // Periodically update audio output route in case user connected/disconnected headphones
    LaunchedEffect(Unit) {
        while (true) {
            audioOutputName = AudioOutputHelper.getCurrentAudioOutputName(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    val rawProgress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayProgress = if (isScrubbing) scrubProgress else rawProgress

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Background Artwork with dark gradient overlay
            val artworkUrl = remember(streamInfo) {
                ImageStrategy.choosePreferredImage(streamInfo.thumbnails, PreferredImageQuality.HIGH)
            }

            if (!artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            // Darkening gradient overlay for legibility & Material You depth
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            // Card Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Top Row: App Badge & Output Switcher Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Logo Badge (YouTube/NewPipe red rounded badge)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFF0033),
                        modifier = Modifier.size(width = 26.dp, height = 20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Output Switcher Pill ("📱 This phone")
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                AudioOutputHelper.openAudioOutputSwitcher(context)
                                audioOutputName = AudioOutputHelper.getCurrentAudioOutputName(context)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = audioOutputIcon,
                                contentDescription = "Audio output",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = audioOutputName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Middle Row: Title/Artist on Left & Pill Play/Pause on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = streamInfo.name ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                lineHeight = 22.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = streamInfo.uploaderName ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp
                            ),
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Rounded Pill / Squircle Play/Pause button
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(width = 56.dp, height = 56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(onClick = onPlayPauseClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Row: Squiggly / Wavy Progress Bar with Scrubber & Skip Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wavy progress bar
                    AndroidMediaWaveBar(
                        progress = displayProgress,
                        isPlaying = isPlaying,
                        onSeekRatio = { ratio ->
                            if (durationMs > 0) {
                                onSeekTo((ratio * durationMs).toLong().coerceIn(0L, durationMs))
                            }
                        },
                        onScrubbingChange = { scrubbing, ratio ->
                            isScrubbing = scrubbing
                            scrubProgress = ratio
                            if (!scrubbing && durationMs > 0) {
                                onSeekTo((ratio * durationMs).toLong().coerceIn(0L, durationMs))
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                    )

                    // Skip Next Button (matching the Android 13 lock screen media player)
                    if (onNextClick != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onNextClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Time indicators (Elapsed / Total)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentDisplayMs = if (isScrubbing) (displayProgress * durationMs).toLong() else currentPositionMs
                    Text(
                        text = formatDuration(currentDisplayMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    Text(
                        text = formatDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

/**
 * Animated squiggly sinusoidal wave seekbar that mimics Android 13/14's native media progress bar.
 */
@Composable
private fun AndroidMediaWaveBar(
    progress: Float,
    isPlaying: Boolean,
    onSeekRatio: (Float) -> Unit,
    onScrubbingChange: (Boolean, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeekRatio(ratio)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrubbingChange(true, ratio)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrubbingChange(true, ratio)
                    },
                    onDragEnd = {
                        onScrubbingChange(false, progress)
                    },
                    onDragCancel = {
                        onScrubbingChange(false, progress)
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val currentX = width * progress.coerceIn(0f, 1f)

        val strokeWidthPx = 4.dp.toPx()
        val waveAmplitude = 4.dp.toPx()
        val waveLength = 22.dp.toPx()

        // 1. Draw Active Wave (from 0 to currentX)
        if (currentX > 0f) {
            val wavePath = Path()
            wavePath.moveTo(0f, centerY)

            var x = 0f
            val step = 2f
            while (x <= currentX) {
                val angle = ((x / waveLength) * 2 * PI.toFloat()) - wavePhase
                val y = centerY + sin(angle) * waveAmplitude
                wavePath.lineTo(x, y)
                x += step
            }

            drawPath(
                path = wavePath,
                color = activeColor,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // 2. Draw Inactive Straight Line (from currentX to width)
        if (currentX < width) {
            drawLine(
                color = inactiveColor,
                start = Offset(currentX, centerY),
                end = Offset(width, centerY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        // 3. Draw Vertical Pill / Thumb Scrubber at currentX
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = 16.dp.toPx()
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(currentX - thumbWidth / 2f, centerY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
        )
    }
}
