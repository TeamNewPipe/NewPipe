/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.newpipe.app.model.ChannelNotificationCount
import org.koin.core.annotation.Singleton

@Singleton(binds = [NotificationActions::class])
class AndroidNotificationActions(private val context: Context) : NotificationActions {

    private val host: NotificationHost? = loadHost(context)

    override val isPlayerNotificationExempt: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override val hasSystemMediaControls: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun recreatePlayerNotification() {
        host?.recreatePlayerNotification()
    }

    override fun areNotificationsEnabledOnDevice(): Boolean = host?.areNotificationsEnabledOnDevice() ?: true

    override fun openSystemNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts(SCHEME_PACKAGE, context.packageName, null)
            )
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (exception: ActivityNotFoundException) {
            Logger.e(messageString = "No activity found for notification settings", throwable = exception)
        }
    }

    override fun applyNewStreamsNotificationsEnabled() {
        host?.applyNewStreamsNotificationsEnabled()
    }

    override fun applyNewStreamsCheckSchedule() {
        host?.applyNewStreamsCheckSchedule()
    }

    override fun channelNotificationCounts(): Flow<ChannelNotificationCount> = host?.channelNotificationCounts() ?: emptyFlow()

    private fun loadHost(context: Context): NotificationHost? = runCatching {
        Class.forName(HOST_CLASS)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as NotificationHost
    }.onFailure { error ->
        Logger.w(messageString = "No host notification actions found, skipping host-side changes", throwable = error)
    }.getOrNull()

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeNotificationHost"
        const val SCHEME_PACKAGE = "package"
    }
}
