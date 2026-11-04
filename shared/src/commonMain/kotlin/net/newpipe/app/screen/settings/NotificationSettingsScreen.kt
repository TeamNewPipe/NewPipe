/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.seconds
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.composable.localizedDuration
import net.newpipe.app.model.ChannelNotificationCount
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.NotificationActions
import net.newpipe.app.preferences.NotificationPreferences.DEFAULT_ENABLE_STREAMS_NOTIFICATIONS
import net.newpipe.app.preferences.NotificationPreferences.DEFAULT_STREAMS_NOTIFICATIONS_INTERVAL
import net.newpipe.app.preferences.NotificationPreferences.DEFAULT_STREAMS_NOTIFICATIONS_NETWORK
import net.newpipe.app.preferences.NotificationPreferences.NETWORK_ANY
import net.newpipe.app.preferences.NotificationPreferences.NETWORK_WIFI
import net.newpipe.app.preferences.NotificationPreferences.STREAMS_NOTIFICATIONS_INTERVALS
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.NotificationSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.any_network
import newpipe.shared.generated.resources.channels
import newpipe.shared.generated.resources.enable_streams_notifications_summary
import newpipe.shared.generated.resources.enable_streams_notifications_title
import newpipe.shared.generated.resources.notifications
import newpipe.shared.generated.resources.notifications_disabled
import newpipe.shared.generated.resources.settings
import newpipe.shared.generated.resources.settings_category_player_notification_summary
import newpipe.shared.generated.resources.settings_category_player_notification_title
import newpipe.shared.generated.resources.streams_notifications_interval_title
import newpipe.shared.generated.resources.streams_notifications_network_title
import newpipe.shared.generated.resources.wifi_only
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: NotificationSettingsViewModel = koinViewModel(),
    notificationActions: NotificationActions = koinInject()
) {
    val newStreamsNotificationsEnabled by viewModel.newStreamsNotificationsEnabled
        .collectAsStateWithLifecycle()
    val checkInterval by viewModel.checkInterval.collectAsStateWithLifecycle()
    val requiredNetwork by viewModel.requiredNetwork.collectAsStateWithLifecycle()
    val deviceNotificationsEnabled by viewModel.deviceNotificationsEnabled
        .collectAsStateWithLifecycle()
    val channelNotificationCount by viewModel.channelNotificationCount
        .collectAsStateWithLifecycle()

    // Notifications may have been turned off in the system settings while this screen was hidden
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDeviceNotificationsEnabled()
    }

    NotificationSettingsScreenContent(
        newStreamsNotificationsEnabled = newStreamsNotificationsEnabled,
        checkInterval = checkInterval,
        requiredNetwork = requiredNetwork,
        deviceNotificationsEnabled = deviceNotificationsEnabled,
        isPlayerNotificationExempt = notificationActions.isPlayerNotificationExempt,
        channelNotificationCount = channelNotificationCount,
        onNewStreamsNotificationsEnabledChange = viewModel::setNewStreamsNotificationsEnabled,
        onCheckIntervalChange = viewModel::setCheckInterval,
        onRequiredNetworkChange = viewModel::setRequiredNetwork,
        onOpenSystemNotificationSettings = notificationActions::openSystemNotificationSettings,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun NotificationSettingsScreenContent(
    newStreamsNotificationsEnabled: Boolean = DEFAULT_ENABLE_STREAMS_NOTIFICATIONS,
    checkInterval: String = DEFAULT_STREAMS_NOTIFICATIONS_INTERVAL,
    requiredNetwork: String = DEFAULT_STREAMS_NOTIFICATIONS_NETWORK,
    deviceNotificationsEnabled: Boolean = true,
    isPlayerNotificationExempt: Boolean = false,
    channelNotificationCount: ChannelNotificationCount? = null,
    onNewStreamsNotificationsEnabledChange: (Boolean) -> Unit = {},
    onCheckIntervalChange: (String) -> Unit = {},
    onRequiredNetworkChange: (String) -> Unit = {},
    onOpenSystemNotificationSettings: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationsDisabledText = stringResource(Res.string.notifications_disabled)
    val settingsText = stringResource(Res.string.settings)
    val currentOnOpenSystemNotificationSettings by rememberUpdatedState(onOpenSystemNotificationSettings)

    LaunchedEffect(deviceNotificationsEnabled) {
        // Shown again after the action, so the warning is still there when coming back from the
        // system settings without allowing notifications. Allowing them restarts this effect,
        // which cancels the loop and hides the snackbar
        while (!deviceNotificationsEnabled) {
            val result = snackbarHostState.showSnackbar(
                message = notificationsDisabledText,
                actionLabel = settingsText,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                currentOnOpenSystemNotificationSettings()
            }
        }
    }

    val intervalEntries = STREAMS_NOTIFICATIONS_INTERVALS.map { seconds ->
        ListPreferenceEntry(seconds, localizedDuration(seconds.toInt().seconds))
    }
    val networkEntries = listOf(
        ListPreferenceEntry(NETWORK_ANY, stringResource(Res.string.any_network)),
        ListPreferenceEntry(NETWORK_WIFI, stringResource(Res.string.wifi_only))
    )

    // Before Android 13 the player notification is blocked together with all other notifications
    val playerNotificationEnabled = deviceNotificationsEnabled || isPlayerNotificationExempt
    val newStreamsOptionsEnabled = deviceNotificationsEnabled && newStreamsNotificationsEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.notifications),
                onNavigateUp = onNavigateUp
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            // TODO: open the player notification screen once it is migrated
            PreferenceRow(
                title = stringResource(Res.string.settings_category_player_notification_title),
                summary = stringResource(Res.string.settings_category_player_notification_summary),
                enabled = playerNotificationEnabled
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_streams_notifications_title),
                summary = stringResource(Res.string.enable_streams_notifications_summary),
                checked = newStreamsNotificationsEnabled,
                onCheckedChange = onNewStreamsNotificationsEnabledChange,
                enabled = deviceNotificationsEnabled
            )
            ListPreference(
                title = stringResource(Res.string.streams_notifications_interval_title),
                entries = intervalEntries,
                selectedValue = checkInterval,
                onValueSelected = onCheckIntervalChange,
                enabled = newStreamsOptionsEnabled
            )
            ListPreference(
                title = stringResource(Res.string.streams_notifications_network_title),
                entries = networkEntries,
                selectedValue = requiredNetwork,
                onValueSelected = onRequiredNetworkChange,
                enabled = newStreamsOptionsEnabled
            )
            // TODO: open the channel notification modes screen once it is migrated
            PreferenceRow(
                title = stringResource(Res.string.channels),
                summary = channelNotificationCount?.let { count -> "${count.notified}/${count.total}" },
                enabled = newStreamsOptionsEnabled
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun NotificationSettingsScreenPreview() {
    NotificationSettingsScreenContent(
        newStreamsNotificationsEnabled = true,
        channelNotificationCount = ChannelNotificationCount(notified = 3, total = 5)
    )
}
