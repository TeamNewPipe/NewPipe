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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem

private val ThumbnailWidth = 144.dp

/** Channel avatars are rounded squares, 32% of the side (proposal, p. 7). */
val YpAvatarShape = RoundedCornerShape(percent = 32)
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
    modifier: Modifier = Modifier,
    moreIcon: Painter? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                start = YpScreenMargin,
                end = if (moreIcon != null) 4.dp else YpScreenMargin,
                top = 8.dp,
                bottom = 8.dp
            ),
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
        if (moreIcon != null) {
            // the item menu, also reachable with a long press
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLongClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = moreIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    .align(if (isLive) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
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
                modifier = Modifier.fillMaxWidth(),
                height = 44
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
            .clip(YpAvatarShape)
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
    modifier: Modifier = Modifier,
    height: Int = 36
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(height.dp)
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

/** Icons of the playlist controls, from the app's drawables. */
data class YpPlaylistControlIcons(val play: Painter, val shuffle: Painter, val listen: Painter)

/** Labels of the playlist controls, from the app's strings. */
data class YpPlaylistControlLabels(val play: String, val shuffle: String, val listen: String)

/** Play, Shuffle and Listen above a list of videos (proposal, p. 14). Hold to enqueue. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YpPlaylistControls(
    icons: YpPlaylistControlIcons,
    labels: YpPlaylistControlLabels,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onListen: () -> Unit,
    onPlayLongClick: () -> Unit,
    onListenLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YpScreenMargin, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(shape)
                .background(colors.primary)
                .combinedClickable(onClick = onPlay, onLongClick = onPlayLongClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(19.dp),
                painter = icons.play,
                contentDescription = null,
                tint = colors.onPrimary
            )
            Spacer(Modifier.width(6.dp))
            Text(text = labels.play, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(shape)
                .background(colors.surfaceContainerHighest)
                .clickable(onClick = onShuffle),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(19.dp),
                painter = icons.shuffle,
                contentDescription = null,
                tint = colors.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(text = labels.shuffle, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .background(colors.surfaceContainerHighest)
                .combinedClickable(onClick = onListen, onLongClick = onListenLongClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = icons.listen,
                contentDescription = labels.listen,
                tint = colors.onSurface
            )
        }
    }
}
