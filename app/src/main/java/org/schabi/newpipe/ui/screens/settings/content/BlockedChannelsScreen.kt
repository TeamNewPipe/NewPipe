package org.schabi.newpipe.ui.screens.settings.content

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_NO
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.database.block.BlockedChannelEntity
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun BlockedChannelsScreen(
    channels: List<BlockedChannelEntity>,
    onUnblock: (BlockedChannelEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.blocked_channels_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_blocked_channels),
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
    channel: BlockedChannelEntity,
    onUnblock: (BlockedChannelEntity) -> Unit
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
                Text(stringResource(R.string.unblock))
            }
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_NO)
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BlockedChannelsScreenPreview() {
    AppTheme {
        Surface {
            BlockedChannelsScreen(
                listOf(
                    BlockedChannelEntity(url = "https://youtube.com/c/abc", name = "Propaganda channel"),
                    BlockedChannelEntity(url = "https://example.com/c/def", name = "Clickbait channel"),
                    BlockedChannelEntity(url = "https://example.com/c/ghi", name = "This is a channel with a very long name that does not seem to end")
                ),
                onUnblock = {}
            )
        }
    }
}
