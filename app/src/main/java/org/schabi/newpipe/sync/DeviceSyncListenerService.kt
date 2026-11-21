/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R

class DeviceSyncListenerService : Service() {
    private val listenerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DeviceSyncListener").apply {
            isDaemon = true
        }
    }
    private val startScheduled = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DeviceSyncListenerPolicy.shouldStopSystemRestart(intent != null)) {
            Log.i(TAG, "Ignoring system resurrection of the device sync foreground listener")
            stopListenerAndSelf()
            return START_NOT_STICKY
        }

        val startIntent = requireNotNull(intent)
        val explicitlyEnabled = startIntent.takeIf {
            it.hasExtra(EXTRA_BACKGROUND_SYNC_ENABLED)
        }?.getBooleanExtra(EXTRA_BACKGROUND_SYNC_ENABLED, false)
        val explicitlyRemote = startIntent.takeIf {
            it.hasExtra(EXTRA_REMOTE_PLAYBACK_ENABLED)
        }?.getBooleanExtra(EXTRA_REMOTE_PLAYBACK_ENABLED, false)
        val canRun = if (explicitlyEnabled != null || explicitlyRemote != null) {
            DeviceSyncListenerPolicy.shouldRun(
                backgroundSyncEnabled = explicitlyEnabled == true || explicitlyRemote == true,
                hasTrustedPeers = DeviceSyncManager.hasTrustedPeers(this)
            )
        } else {
            shouldRun(this)
        }
        if (!canRun) {
            stopListenerAndSelf()
            return START_NOT_STICKY
        }

        try {
            promoteToForeground()
        } catch (error: RuntimeException) {
            if (!DeviceSyncListenerPolicy.isForegroundPromotionRejected(error.javaClass.name)) {
                throw error
            }
            Log.w(
                TAG,
                "Android rejected foreground promotion for the device sync listener; " +
                    "stopping until the next user-visible start",
                error
            )
            stopListenerAndSelf()
            return START_NOT_STICKY
        }

        startListener()
        // A sticky restart is delivered with a null Intent and can occur while the app is fully
        // backgrounded. Modern Android may then reject startForeground(), so rely on boot/package
        // events and the next visible MainActivity start instead of asking Android to resurrect us.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listenerExecutor.execute {
            runCatching {
                DeviceSyncManager.get(applicationContext).stopListening()
            }.onFailure { error ->
                Log.w(TAG, "Could not stop the device sync listener cleanly", error)
            }
        }
        listenerExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListener() {
        if (!startScheduled.compareAndSet(false, true)) {
            return
        }
        listenerExecutor.execute {
            try {
                DeviceSyncManager.get(applicationContext).startListening()
                Log.i(TAG, "Paired-device synchronization listener is active")
            } catch (error: Throwable) {
                Log.e(TAG, "Could not start paired-device synchronization listener", error)
                stopSelf()
            } finally {
                startScheduled.set(false)
            }
        }
    }

    private fun stopListenerAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun promoteToForeground() {
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_wizestream_triangle_white)
                .setContentTitle(getString(R.string.device_sync_listener_notification_title))
                .setContentText(getString(R.string.device_sync_listener_notification_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build(),
            foregroundServiceType
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.device_sync_listener_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.device_sync_listener_channel_description)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val TAG = "DeviceSyncListener"
        private const val NOTIFICATION_CHANNEL_ID = "device_sync_listener"
        private const val NOTIFICATION_ID = 0x57535
        private const val EXTRA_BACKGROUND_SYNC_ENABLED =
            "org.schabi.newpipe.sync.BACKGROUND_SYNC_ENABLED"
        private const val EXTRA_REMOTE_PLAYBACK_ENABLED =
            "org.schabi.newpipe.sync.REMOTE_PLAYBACK_ENABLED"

        fun startIfEnabled(context: Context) {
            val appContext = context.applicationContext
            if (!shouldRun(appContext)) {
                stop(appContext)
                return
            }
            try {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, DeviceSyncListenerService::class.java)
                )
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not start foreground device sync listener", error)
            }
        }

        fun update(
            context: Context,
            backgroundSyncEnabled: Boolean,
            hasTrustedPeers: Boolean,
            remotePlaybackEnabled: Boolean = RemotePlaybackReceiver.isEnabled(context)
        ) {
            if (DeviceSyncListenerPolicy.shouldRun(
                    backgroundSyncEnabled || remotePlaybackEnabled,
                    hasTrustedPeers
                )
            ) {
                try {
                    ContextCompat.startForegroundService(
                        context.applicationContext,
                        Intent(context.applicationContext, DeviceSyncListenerService::class.java)
                            .putExtra(EXTRA_BACKGROUND_SYNC_ENABLED, backgroundSyncEnabled)
                            .putExtra(EXTRA_REMOTE_PLAYBACK_ENABLED, remotePlaybackEnabled)
                    )
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Could not start foreground device sync listener", error)
                }
            } else {
                stop(context)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, DeviceSyncListenerService::class.java)
            )
        }

        private fun shouldRun(context: Context): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val enabled = preferences.getBoolean(
                context.getString(R.string.device_sync_background_key),
                true
            )
            return DeviceSyncListenerPolicy.shouldRun(
                backgroundSyncEnabled = enabled || RemotePlaybackReceiver.isEnabled(context),
                hasTrustedPeers = DeviceSyncManager.hasTrustedPeers(context)
            )
        }
    }
}
