/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import net.newpipe.app.model.ChannelNotification
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

    private val subscriptionManager by lazy { SubscriptionManager(context) }

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

    override fun applyNewStreamsCheckSchedule() = NotificationWorker.schedule(
        context,
        ScheduleOptions.from(context),
        force = true
    )

    override fun channelNotifications(): Flow<List<ChannelNotification>> = subscriptionManager
        .subscriptions()
        .asFlow()
        .map { subscriptions ->
            subscriptions.map { subscription ->
                ChannelNotification(
                    id = subscription.uid,
                    serviceId = subscription.serviceId,
                    url = subscription.url.orEmpty(),
                    name = subscription.name.orEmpty(),
                    enabled = subscription.notificationMode != NotificationMode.DISABLED
                )
            }
        }
        .catch { error ->
            ErrorUtil.showSnackbar(
                context,
                ErrorInfo(error, UserAction.SUBSCRIPTION_GET, "Get subscriptions list")
            )
        }

    override suspend fun setChannelNotificationsEnabled(channel: ChannelNotification, enabled: Boolean) {
        val mode = if (enabled) NotificationMode.ENABLED else NotificationMode.DISABLED
        try {
            withContext(Dispatchers.IO) {
                val subscriptionTable = subscriptionManager.subscriptionTable()
                val subscription = subscriptionTable.getSubscription(channel.id)
                subscription.notificationMode = mode
                subscriptionTable.update(subscription)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ErrorUtil.showSnackbar(
                context,
                ErrorInfo(
                    error,
                    UserAction.SUBSCRIPTION_UPDATE,
                    "Update notification mode of ${channel.name}"
                )
            )
        }
    }
}
