/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem

private val ThumbnailWidth = 160.dp
private val ThumbnailShape = RoundedCornerShape(10.dp)
private val BadgeBackground = Color(0xCC18141A)
private val BadgeText = Color(0xFFF3EEE9)

/** Labels of the subscribe button, from the app's strings. */
data class YpSubscribeLabels(val subscribe: String, val subscribed: String)

/** Compact video row: thumbnail on the left, title, channel and details (proposal, p. 13). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpVideoRow(
    item: YpStreamItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = YpScreenMargin, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        YpThumbnail(
            url = item.thumbnailUrl,
            durationText = item.durationText,
            isLive = item.isLive,
            progress = item.progress
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            if (item.uploaderName.isNotEmpty()) {
                YpMetaText(item.uploaderName)
            }
            if (item.details.isNotEmpty()) {
                YpMetaText(item.details)
            }
        }
    }
}

/** 16:9 thumbnail with the duration (or live) badge and the watched progress line. */
@Composable
fun YpThumbnail(
    url: String?,
    durationText: String?,
    isLive: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(ThumbnailWidth)
            .aspectRatio(16f / 9f)
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
        if (durationText != null) {
            Text(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isLive) MaterialTheme.colorScheme.tertiary else BadgeBackground)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = durationText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isLive) MaterialTheme.colorScheme.onTertiary else BadgeText
            )
        }
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.tertiary)
            )
        }
    }
}

/** Channel row: avatar, name, details and the subscribe button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpChannelRow(
    item: YpChannelItem,
    subscribed: Boolean?,
    labels: YpSubscribeLabels,
    onClick: () -> Unit,
    onToggleSubscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = YpScreenMargin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        YpAvatar(url = item.avatarUrl, size = 56)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.details?.let { YpMetaText(it) }
            item.description?.takeIf { it.isNotBlank() }?.let { YpMetaText(it, maxLines = 1) }
        }
        if (subscribed != null) {
            YpSubscribeButton(subscribed, labels, onToggleSubscription)
        }
    }
}

/** First channel of the search results as a card (proposal, p. 13: "Canal em destaque"). */
@Composable
fun YpFeaturedChannelCard(
    item: YpChannelItem,
    subscribed: Boolean?,
    labels: YpSubscribeLabels,
    onClick: () -> Unit,
    onToggleSubscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YpScreenMargin, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            YpAvatar(url = item.avatarUrl, size = 72)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.details?.let { YpMetaText(it) }
            }
        }
        item.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            YpMetaText(it, maxLines = 2)
        }
        if (subscribed != null) {
            Spacer(Modifier.height(14.dp))
            YpSubscribeButton(
                subscribed = subscribed,
                labels = labels,
                onClick = onToggleSubscription,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Playlist row: thumbnail with the video count, name and uploader. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpPlaylistRow(
    item: YpPlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = YpScreenMargin, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(ThumbnailWidth)
                .aspectRatio(16f / 9f)
                .clip(ThumbnailShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
            if (item.countText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(56.dp)
                        .background(BadgeBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.countText,
                        style = MaterialTheme.typography.labelSmall,
                        color = BadgeText,
                        maxLines = 2
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            item.uploaderName?.takeIf { it.isNotEmpty() }?.let { YpMetaText(it) }
        }
    }
}

/** Round avatar of a channel. */
@Composable
fun YpAvatar(url: String?, size: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop
    )
}

/** "Subscribe" in ink, "Subscribed" in the secondary surface. */
@Composable
fun YpSubscribeButton(
    subscribed: Boolean,
    labels: YpSubscribeLabels,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (subscribed) colors.surfaceContainerHighest else colors.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (subscribed) labels.subscribed else labels.subscribe,
            style = MaterialTheme.typography.labelLarge,
            color = if (subscribed) colors.onSurface else colors.onPrimary
        )
    }
}

@Composable
private fun YpMetaText(text: String, maxLines: Int = 1) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
