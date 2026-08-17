/*
 * Copyright 2019 Mauricio Colli <mauriciocolli@outlook.com>
 * FeedLoadService.kt is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.local.feed.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.schabi.newpipe.App
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.postEvent

class FeedLoadService : Service() {
    companion object {
        private val TAG = FeedLoadService::class.java.simpleName
        const val NOTIFICATION_ID = 7293450
        private const val ACTION_CANCEL = App.PACKAGE_NAME + ".local.feed.service.FeedLoadService.CANCEL"

        /**
         * How often the notification will be updated.
         */
        private const val NOTIFICATION_SAMPLING_PERIOD = 1500L

        const val EXTRA_GROUP_ID: String = "FeedLoadService.EXTRA_GROUP_ID"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var feedLoadManager: FeedLoadManager

    // /////////////////////////////////////////////////////////////////////////
    // Lifecycle
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreate() {
        super.onCreate()
        feedLoadManager = FeedLoadManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(
                TAG,
                "onStartCommand() called with: intent = [" + intent + "]," +
                    " flags = [" + flags + "], startId = [" + startId + "]"
            )
        }

        if (intent == null) {
            return START_NOT_STICKY
        }

        setupNotification()
        setupBroadcastReceiver()

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())

        serviceScope.launch {
            try {
                feedLoadManager.startLoading(groupId)
                stopService()
            } catch (e: Exception) {
                Log.e(TAG, "Error while storing result", e)
                handleError(e)
            }
        }

        return START_NOT_STICKY
    }

    private fun disposeAll() {
        unregisterReceiver(broadcastReceiver)
        serviceScope.cancel()
    }

    private fun stopService() {
        disposeAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // /////////////////////////////////////////////////////////////////////////
    // Loading & Handling
    // /////////////////////////////////////////////////////////////////////////

    class RequestException(val subscriptionId: Long, message: String, cause: Throwable) : Exception(message, cause)

    // /////////////////////////////////////////////////////////////////////////
    // Notification
    // /////////////////////////////////////////////////////////////////////////

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private fun createNotification(): NotificationCompat.Builder {
        val cancelActionIntent = PendingIntentCompat
            .getBroadcast(this, NOTIFICATION_ID, Intent(ACTION_CANCEL), 0, false)

        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setOngoing(true)
            .setProgress(-1, -1, true)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.cancel), cancelActionIntent)
            .setContentTitle(getString(R.string.feed_notification_loading))
    }

    private fun setupNotification() {
        notificationManager = NotificationManagerCompat.from(this)
        notificationBuilder = createNotification()

        feedLoadManager.notification
            .debounce(NOTIFICATION_SAMPLING_PERIOD)
            .flowOn(Dispatchers.IO)
            .onEach { updateNotificationProgress(it) }
            .launchIn(serviceScope)
    }

    private fun updateNotificationProgress(state: FeedLoadState) {
        notificationBuilder.setProgress(state.maxProgress, state.currentProgress, state.maxProgress == -1)

        if (state.maxProgress == -1) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) notificationBuilder.setContentInfo(null)
            if (state.updateDescription.isNotEmpty()) notificationBuilder.setContentText(state.updateDescription)
            notificationBuilder.setContentText(state.updateDescription)
        } else {
            val progressText = state.currentProgress.toString() + "/" + state.maxProgress

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (state.updateDescription.isNotEmpty()) {
                    notificationBuilder.setContentText("${state.updateDescription}  ($progressText)")
                }
            } else {
                notificationBuilder.setContentInfo(progressText)
                if (state.updateDescription.isNotEmpty()) {
                    notificationBuilder.setContentText(state.updateDescription)
                }
            }
        }

        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Notification Actions
    // /////////////////////////////////////////////////////////////////////////

    private lateinit var broadcastReceiver: BroadcastReceiver

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_CANCEL) {
                    feedLoadManager.cancel()
                }
            }
        }
        ContextCompat.registerReceiver(this, broadcastReceiver, IntentFilter(ACTION_CANCEL), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Error handling
    // /////////////////////////////////////////////////////////////////////////

    private fun handleError(error: Throwable) {
        postEvent(ErrorResultEvent(error))
        stopService()
    }
}
