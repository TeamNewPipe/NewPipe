/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import net.newpipe.app.model.ChannelNotificationCount
import net.newpipe.app.platform.NotificationHost
import org.schabi.newpipe.App
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.local.feed.notifications.ScheduleOptions
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.player.notification.NotificationConstants

class ComposeNotificationHost(private val context: Context) : NotificationHost {

    override fun areNotificationsEnabledOnDevice() = NotificationHelper.areNotificationsEnabledOnDevice(context)

    override fun applyNewStreamsNotificationsEnabled() {
        if (NotificationHelper.areNewStreamsNotificationsEnabled(context)) {
            NotificationWorker.schedule(context)
        } else {
            NotificationWorker.cancel(context)
        }
    }

    override fun recreatePlayerNotification() = context.sendBroadcast(
        Intent(NotificationConstants.ACTION_RECREATE_NOTIFICATION).setPackage(App.PACKAGE_NAME)
    )

    override fun applyNewStreamsCheckSchedule() = NotificationWorker.schedule(context, ScheduleOptions.from(context), force = true)

    override fun channelNotificationCounts(): Flow<ChannelNotificationCount> = SubscriptionManager(context)
        .subscriptions()
        .asFlow()
        .map { subscriptions ->
            ChannelNotificationCount(
                notified = subscriptions.count { it.notificationMode != NotificationMode.DISABLED },
                total = subscriptions.size
            )
        }
        .catch { error ->
            ErrorUtil.showSnackbar(
                context,
                ErrorInfo(error, UserAction.SUBSCRIPTION_GET, "Get subscriptions list")
            )
        }
}
