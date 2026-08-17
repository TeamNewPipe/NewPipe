package org.schabi.newpipe.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.image.ImageStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue

@Composable
fun VideoItemShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        start = Offset(10f, 10f),
        end = Offset(translateAnim, translateAnim)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(brush)
        )
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun VideoItem(
    item: StreamInfoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailUrl = remember(item.thumbnails) { ImageStrategy.choosePreferredImage(item.thumbnails) }
    val avatarUrl = remember(item.uploaderAvatars) { ImageStrategy.choosePreferredImage(item.uploaderAvatars) }
    val durationText = remember(item.duration) { if (item.duration > 0) formatDuration(item.duration) else null }
    val subtitleText = remember(item.uploaderName, item.viewCount, item.textualUploadDate) {
        val views = formatViewCount(item.viewCount)
        val date = item.textualUploadDate.orEmpty()
        val uploader = item.uploaderName ?: "Unknown"
        when {
            views.isNotEmpty() && date.isNotEmpty() -> "$uploader • $views • $date"
            views.isNotEmpty() -> "$uploader • $views"
            else -> uploader
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            // Duration tag
            if (durationText != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = durationText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = item.uploaderName,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CompactVideoItem(
    item: StreamInfoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailUrl = remember(item.thumbnails) { ImageStrategy.choosePreferredImage(item.thumbnails) }
    val avatarUrl = remember(item.uploaderAvatars) { ImageStrategy.choosePreferredImage(item.uploaderAvatars) }
    val durationText = remember(item.duration) { if (item.duration > 0) formatDuration(item.duration) else null }
    val metaText = remember(item.viewCount, item.textualUploadDate) {
        val views = formatViewCount(item.viewCount)
        val date = item.textualUploadDate.orEmpty()
        when {
            views.isNotEmpty() && date.isNotEmpty() -> "$views • $date"
            views.isNotEmpty() -> views
            date.isNotEmpty() -> date
            else -> ""
        }
    }

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 16:9 Thumbnail Box
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (durationText != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.82f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = durationText,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = item.name ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = item.uploaderName ?: "Unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                if (metaText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun formatDuration(duration: Long): String {
    val hours = duration / 3600
    val minutes = (duration % 3600) / 60
    val seconds = duration % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun formatViewCount(viewCount: Long): String {
    return when {
        viewCount >= 1_000_000_000 -> String.format(java.util.Locale.getDefault(), "%.1fB views", viewCount / 1_000_000_000.0)
        viewCount >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1fM views", viewCount / 1_000_000.0)
        viewCount >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1fK views", viewCount / 1_000.0)
        viewCount > 0 -> "$viewCount views"
        else -> ""
    }
}

@Composable
fun ChannelItem(
    item: ChannelInfoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailUrl = remember(item.thumbnails) { ImageStrategy.choosePreferredImage(item.thumbnails) }
    val subtitleText = remember(item.subscriberCount, item.streamCount) {
        "${item.subscriberCount} subscribers • ${item.streamCount} videos"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!item.description.isNullOrEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlaylistItem(
    item: PlaylistInfoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailUrl = remember(item.thumbnails) { ImageStrategy.choosePreferredImage(item.thumbnails) }
    val subtitleText = remember(item.uploaderName, item.streamCount) {
        "${item.uploaderName} • ${item.streamCount} videos"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
