/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.newpipe.app.model.ChannelNotificationCount
import net.newpipe.app.platform.NotificationActions
import net.newpipe.app.preferences.NotificationPreferences.DEFAULT_ENABLE_STREAMS_NOTIFICATIONS
import net.newpipe.app.preferences.NotificationPreferences.DEFAULT_STREAMS_NOTIFICATIONS_INTERVAL
import net.newpipe.app.preferences.NotificationPreferences.DEFAULT_STREAMS_NOTIFICATIONS_NETWORK
import net.newpipe.app.preferences.NotificationPreferences.KEY_ENABLE_STREAMS_NOTIFICATIONS
import net.newpipe.app.preferences.NotificationPreferences.KEY_STREAMS_NOTIFICATIONS_INTERVAL
import net.newpipe.app.preferences.NotificationPreferences.KEY_STREAMS_NOTIFICATIONS_NETWORK
import org.koin.core.annotation.KoinViewModel

private const val STOP_TIMEOUT_MILLIS = 5_000L

@KoinViewModel
class NotificationSettingsViewModel(
    private val settings: Settings,
    private val notificationActions: NotificationActions
) : ViewModel() {

    val newStreamsNotificationsEnabled: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(KEY_ENABLE_STREAMS_NOTIFICATIONS, DEFAULT_ENABLE_STREAMS_NOTIFICATIONS)
        )

    val checkInterval: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(KEY_STREAMS_NOTIFICATIONS_INTERVAL, DEFAULT_STREAMS_NOTIFICATIONS_INTERVAL)
        )

    val requiredNetwork: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(KEY_STREAMS_NOTIFICATIONS_NETWORK, DEFAULT_STREAMS_NOTIFICATIONS_NETWORK)
        )

    val deviceNotificationsEnabled: StateFlow<Boolean>
        field = MutableStateFlow(notificationActions.areNotificationsEnabledOnDevice())

    val channelNotificationCount: StateFlow<ChannelNotificationCount?> = notificationActions
        .channelNotificationCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setNewStreamsNotificationsEnabled(value: Boolean) {
        settings.putBoolean(KEY_ENABLE_STREAMS_NOTIFICATIONS, value)
        newStreamsNotificationsEnabled.value = value
        notificationActions.applyNewStreamsNotificationsEnabled()
    }

    fun setCheckInterval(value: String) = checkInterval.persistAndReschedule(KEY_STREAMS_NOTIFICATIONS_INTERVAL, value)

    fun setRequiredNetwork(value: String) = requiredNetwork.persistAndReschedule(KEY_STREAMS_NOTIFICATIONS_NETWORK, value)

    fun refreshDeviceNotificationsEnabled() {
        deviceNotificationsEnabled.value = notificationActions.areNotificationsEnabledOnDevice()
    }

    private fun MutableStateFlow<String>.persistAndReschedule(key: String, newValue: String) {
        settings.putString(key, newValue)
        value = newValue
        notificationActions.applyNewStreamsCheckSchedule()
    }
}
