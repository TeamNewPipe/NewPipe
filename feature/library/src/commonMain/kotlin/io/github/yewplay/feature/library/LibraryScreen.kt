/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.data.LibraryRepository
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpSectionTitle
import io.github.yewplay.core.designsystem.YpShortcutRow
import io.github.yewplay.core.model.YpPlaylist
import io.github.yewplay.core.model.YpVideo

/** Texts of the screen, given by the app so that they follow its translations. */
data class LibraryStrings(
    val continueWatching: String,
    val history: String,
    val downloads: String,
    val savedPlaylists: String,
    val about: String,
    val yourPlaylists: String,
    val videoCount: (Long) -> String
)

/** Icons of the screen, given by the app (Phosphor icons in its resources). */
data class LibraryIcons(
    val history: Painter,
    val downloads: Painter,
    val savedPlaylists: Painter,
    val about: Painter,
    val chevron: Painter
)

/** What the screen asks the app to do. */
data class LibraryActions(
    val openVideo: (YpVideo) -> Unit,
    val openHistory: () -> Unit,
    val openDownloads: () -> Unit,
    val openSavedPlaylists: () -> Unit,
    val openAbout: () -> Unit,
    val openPlaylist: (YpPlaylist) -> Unit
)

/**
 * YewPlay: "Library" (proposal, page 14): continue where you left off, shortcuts with counts and
 * the user's playlists with square covers.
 */
@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    strings: LibraryStrings,
    icons: LibraryIcons,
    actions: LibraryActions,
    historyEnabled: Boolean
) {
    val continueFlow = remember(repository) { repository.continueWatching(CONTINUE_LIMIT) }
    val countFlow = remember(repository) { repository.historyCount() }
    val playlistsFlow = remember(repository) { repository.playlists() }
    val continueWatching by continueFlow.collectAsState(initial = emptyList())
    val historyCount by countFlow.collectAsState(initial = 0)
    val playlists by playlistsFlow.collectAsState(initial = emptyList())

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (historyEnabled && continueWatching.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    YpSectionTitle(strings.continueWatching)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = YpScreenMargin),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueWatching, key = { it.url }) { video ->
                            ContinueCard(video, onClick = { actions.openVideo(video) })
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                if (historyEnabled) {
                    Shortcut(
                        icons.history, strings.history, historyCount.toString(),
                        icons.chevron, actions.openHistory
                    )
                }
                Shortcut(icons.downloads, strings.downloads, null, icons.chevron, actions.openDownloads)
                Shortcut(
                    icons.savedPlaylists, strings.savedPlaylists,
                    playlists.size.toString(), icons.chevron, actions.openSavedPlaylists
                )
                Shortcut(icons.about, strings.about, null, icons.chevron, actions.openAbout)
            }
        }

        if (playlists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                YpSectionTitle(strings.yourPlaylists, modifier = Modifier.padding(top = 12.dp))
            }
            itemsIndexed(
                playlists,
                key = { _, it -> (if (it.isLocal) "l" else "r") + it.id }
            ) { index, playlist ->
                PlaylistCard(
                    playlist = playlist,
                    countText = strings.videoCount(playlist.streamCount),
                    leftColumn = index % 2 == 0,
                    onClick = { actions.openPlaylist(playlist) }
                )
            }
        }
    }
}

private const val CONTINUE_LIMIT = 10

/** "4:05", "12:30" or "1:02:03". */
private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mmss = "${minutes.toString().padStart(if (hours > 0) 2 else 1, '0')}:" +
        seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mmss" else mmss
}

@Composable
private fun Shortcut(
    icon: Painter,
    label: String,
    count: String?,
    chevron: Painter,
    onClick: () -> Unit
) {
    Column {
        YpShortcutRow(
            label = label,
            onClick = onClick,
            trailingText = count,
            leading = {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            trailing = {
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = chevron,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = YpScreenMargin + 38.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun ContinueCard(video: YpVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
            if (video.durationSeconds > 0) {
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC18141A))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    text = formatDuration(video.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF3EEE9)
                )
            }
            // ruby line for how much was watched
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(video.progressFraction)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = video.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = video.channelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: YpPlaylist,
    countText: String,
    leftColumn: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(
                start = if (leftColumn) YpScreenMargin else 6.dp,
                end = if (leftColumn) 6.dp else YpScreenMargin,
                bottom = 16.dp
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            model = playlist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
