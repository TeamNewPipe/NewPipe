/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.newpipe.app.model.ChannelNotification

/**
 * Platform-specific actions triggered from the Notifications settings screen
 */
interface NotificationActions {

    val isPlayerNotificationExempt: Boolean

    val hasSystemMediaControls: Boolean

    fun areNotificationsEnabledOnDevice(): Boolean

    fun openSystemNotificationSettings()

    fun applyNewStreamsNotificationsEnabled()

    fun applyNewStreamsCheckSchedule()

    fun recreatePlayerNotification()

    fun channelNotifications(): Flow<List<ChannelNotification>>

    suspend fun setChannelNotificationsEnabled(channel: ChannelNotification, enabled: Boolean)
}

internal object NoOpNotificationActions : NotificationActions {

    override val isPlayerNotificationExempt = false

    override val hasSystemMediaControls = false

    override fun areNotificationsEnabledOnDevice() = true

    override fun openSystemNotificationSettings() = logUnsupported("openSystemNotificationSettings")

    override fun applyNewStreamsNotificationsEnabled() = logUnsupported("applyNewStreamsNotificationsEnabled")

    override fun applyNewStreamsCheckSchedule() = logUnsupported("applyNewStreamsCheckSchedule")

    override fun channelNotifications(): Flow<List<ChannelNotification>> = emptyFlow()

    override suspend fun setChannelNotificationsEnabled(channel: ChannelNotification, enabled: Boolean) = logUnsupported("setChannelNotificationsEnabled")

    override fun recreatePlayerNotification() = logUnsupported("recreatePlayerNotification")

    private fun logUnsupported(action: String) = Logger.w(messageString = "Notification action '$action' is unsupported on this platform")
}
