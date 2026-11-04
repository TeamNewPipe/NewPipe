/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.feature.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.model.YpChannel
import io.github.yewplay.core.model.YpGroup

/** Texts of the header, given by the app so that they follow its translations. */
data class SubscriptionsStrings(
    val all: String,
    val addGroup: String
)

/** What the header asks the app to do. */
data class SubscriptionsActions(
    val openChannel: (YpChannel) -> Unit,
    /** Selects a group; null means "All". */
    val selectGroup: (YpGroup?) -> Unit,
    val addGroup: () -> Unit
)

/**
 * YewPlay: top of "Subscriptions" (proposal, page 14): channel avatars with a ruby dot on the
 * ones with new videos, then the groups as text tabs.
 */
@Composable
fun SubscriptionsHeader(
    channels: List<YpChannel>,
    groups: List<YpGroup>,
    selectedGroupId: Long?,
    strings: SubscriptionsStrings,
    actions: SubscriptionsActions
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        if (channels.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = YpScreenMargin),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(channels, key = { it.url }) { channel ->
                    ChannelAvatar(channel, onClick = { actions.openChannel(channel) })
                }
            }
        }
        LazyRow(
            modifier = Modifier.padding(top = 12.dp),
            contentPadding = PaddingValues(horizontal = YpScreenMargin - 8.dp)
        ) {
            item {
                GroupTab(strings.all, selected = selectedGroupId == null) {
                    actions.selectGroup(null)
                }
            }
            items(groups, key = { it.id }) { group ->
                GroupTab(group.name, selected = selectedGroupId == group.id) {
                    actions.selectGroup(group)
                }
            }
            item {
                GroupTab(strings.addGroup, selected = false, onClick = actions.addGroup)
            }
        }
    }
}

@Composable
private fun ChannelAvatar(channel: YpChannel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(percent = 32))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                model = channel.avatarUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop
            )
            if (channel.hasNewVideos) {
                // ruby dot: this channel has new videos (a state, never decoration)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = channel.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GroupTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(width = 16.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.background
                )
        )
    }
}
