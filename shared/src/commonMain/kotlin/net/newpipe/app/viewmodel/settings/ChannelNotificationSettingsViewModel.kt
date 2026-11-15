/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.newpipe.app.model.ChannelNotification
import net.newpipe.app.platform.NotificationActions
import org.koin.core.annotation.KoinViewModel

private const val STOP_TIMEOUT_MILLIS = 5_000L

@KoinViewModel
class ChannelNotificationSettingsViewModel(
    private val notificationActions: NotificationActions
) : ViewModel() {

    val channels: StateFlow<List<ChannelNotification>> = notificationActions
        .channelNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun setEnabled(channel: ChannelNotification, enabled: Boolean) {
        viewModelScope.launch { notificationActions.setChannelNotificationsEnabled(channel, enabled) }
    }

    fun toggleAll() {
        val current = channels.value
        val enabled = !(current.firstOrNull()?.enabled ?: return)
        viewModelScope.launch {
            current.forEach { channel -> notificationActions.setChannelNotificationsEnabled(channel, enabled) }
        }
    }
}
