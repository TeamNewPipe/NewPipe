/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ChannelNotification
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.ChannelNotificationSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.channel_notifications_description
import newpipe.shared.generated.resources.channels
import newpipe.shared.generated.resources.ic_checklist
import newpipe.shared.generated.resources.toggle_all
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val CHANNEL_NAME_MAX_LINES = 2
private const val DESCRIPTION_KEY = "description"

@Composable
fun ChannelNotificationSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: ChannelNotificationSettingsViewModel = koinViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    ChannelNotificationSettingsScreenContent(
        channels = channels,
        onEnabledChange = viewModel::setEnabled,
        onToggleAll = viewModel::toggleAll,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun ChannelNotificationSettingsScreenContent(
    channels: List<ChannelNotification> = emptyList(),
    onEnabledChange: (channel: ChannelNotification, enabled: Boolean) -> Unit = { _, _ -> },
    onToggleAll: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.channels),
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = onToggleAll) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_checklist),
                            contentDescription = stringResource(Res.string.toggle_all)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item(key = DESCRIPTION_KEY) {
                Text(
                    text = stringResource(Res.string.channel_notifications_description),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            items(items = channels, key = { channel -> channel.id }) { channel ->
                ListItem(
                    modifier = Modifier.toggleable(
                        value = channel.enabled,
                        role = Role.Checkbox,
                        onValueChange = { enabled -> onEnabledChange(channel, enabled) }
                    ),
                    headlineContent = {
                        Text(
                            text = channel.name,
                            maxLines = CHANNEL_NAME_MAX_LINES,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = { Checkbox(checked = channel.enabled, onCheckedChange = null) }
                )
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ChannelNotificationSettingsScreenPreview() {
    ChannelNotificationSettingsScreenContent(
        channels = listOf(
            ChannelNotification(id = 1, serviceId = 0, url = "", name = "NewPipe", enabled = true),
            ChannelNotification(id = 2, serviceId = 0, url = "", name = "Kurzgesagt", enabled = false)
        )
    )
}
