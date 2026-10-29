/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.Flow
import net.newpipe.app.model.ChannelNotificationCount

/**
 * Hooks into the notification helpers, worker and database that still live in the current Android app
 */
interface NotificationHost {

    fun areNotificationsEnabledOnDevice(): Boolean

    fun applyNewStreamsNotificationsEnabled()

    fun applyNewStreamsCheckSchedule()

    fun channelNotificationCounts(): Flow<ChannelNotificationCount>
}
