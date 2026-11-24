/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.BlockedChannel
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.AppTheme
import net.newpipe.app.viewmodel.settings.BlockedChannelsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.blocked_channels
import newpipe.shared.generated.resources.blocked_channels_summary
import newpipe.shared.generated.resources.no_blocked_channels
import newpipe.shared.generated.resources.unblock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BlockedChannelsSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: BlockedChannelsViewModel = koinViewModel()
) {
    val channels by viewModel.blockedChannels.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.blocked_channels),
                onNavigateUp = { navigator.navigateUp() }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            BlockedChannelsSettingsScreenContent(channels, viewModel::unblockChannel)
        }
    }
}

@Composable
fun BlockedChannelsSettingsScreenContent(
    channels: List<BlockedChannel>,
    onUnblock: (BlockedChannel) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(Res.string.blocked_channels_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.no_blocked_channels),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channels, key = { it.url }) { channel ->
                    BlockedChannelItem(channel, onUnblock)
                }
            }
        }
    }
}

@Composable
private fun BlockedChannelItem(
    channel: BlockedChannel,
    onUnblock: (BlockedChannel) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { onUnblock(channel) }) {
                Text(stringResource(Res.string.unblock))
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun BlockedChannelsSettingsScreenPreview() {
    AppTheme {
        Surface {
            BlockedChannelsSettingsScreenContent(
                listOf(
                    BlockedChannel(url = "https://youtube.com/c/abc", name = "Clickbait channel"),
                    BlockedChannel(url = "https://example.com/c/def", name = "Another channel"),
                    BlockedChannel(url = "https://example.com/c/ghi", name = "This is a channel with a very long name that does not seem to end here")
                ),
                onUnblock = {}
            )
        }
    }
}
