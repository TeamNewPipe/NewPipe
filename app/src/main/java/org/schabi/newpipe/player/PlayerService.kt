/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * Part of NewPipe
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
package org.schabi.newpipe.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.player.notification.NotificationPlayerUi
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import java.lang.ref.WeakReference
import java.util.function.Consumer

/**
 * One service for all players.
 */
class PlayerService() : Service() {
    private var player: Player? = null
    private val mBinder: IBinder = LocalBinder(this)

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate() {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called")
        }
        Localization.assureCorrectAppLanguage(this)
        ThemeHelper.setTheme(this)
        player = Player(this)
        /*
        Create the player notification and start immediately the service in foreground,
        otherwise if nothing is played or initializing the player and its components (especially
        loading stream metadata) takes a lot of time, the app would crash on Android 8+ as the
        service would never be put in the foreground while we said to the system we would do so
         */player!!.UIs().get((NotificationPlayerUi::class.java))
                .ifPresent(Consumer({ obj: NotificationPlayerUi? -> obj!!.createNotificationAndStartForeground() }))
    }

    public override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(TAG, ("onStartCommand() called with: intent = [" + intent
                    + "], flags = [" + flags + "], startId = [" + startId + "]"))
        }

        /*
        Be sure that the player notification is set and the service is started in foreground,
        otherwise, the app may crash on Android 8+ as the service would never be put in the
        foreground while we said to the system we would do so
        The service is always requested to be started in foreground, so always creating a
        notification if there is no one already and starting the service in foreground should
        not create any issues
        If the service is already started in foreground, requesting it to be started shouldn't
        do anything
         */if (player != null) {
            player!!.UIs().get((NotificationPlayerUi::class.java))
                    .ifPresent(Consumer({ obj: NotificationPlayerUi? -> obj!!.createNotificationAndStartForeground() }))
        }
        if (((Intent.ACTION_MEDIA_BUTTON == intent.getAction()) && (player == null || player!!.getPlayQueue() == null))) {
            /*
            No need to process media button's actions if the player is not working, otherwise
            the player service would strangely start with nothing to play
            Stop the service in this case, which will be removed from the foreground and its
            notification cancelled in its destruction
             */
            stopSelf()
            return START_NOT_STICKY
        }
        if (player != null) {
            player!!.handleIntent(intent)
            player!!.UIs().get((MediaSessionPlayerUi::class.java))
                    .ifPresent(Consumer({ ui: MediaSessionPlayerUi? -> ui!!.handleMediaButtonIntent(intent) }))
        }
        return START_NOT_STICKY
    }

    fun stopForImmediateReusing() {
        if (DEBUG) {
            Log.d(TAG, "stopForImmediateReusing() called")
        }
        if (player != null && !player!!.exoPlayerIsNull()) {
            // Releases wifi & cpu, disables keepScreenOn, etc.
            // We can't just pause the player here because it will make transition
            // from one stream to a new stream not smooth
            player!!.smoothStopForImmediateReusing()
        }
    }

    public override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        if (player != null && !player!!.videoPlayerSelected()) {
            return
        }
        onDestroy()
        // Unload from memory completely
        Runtime.getRuntime().halt(0)
    }

    public override fun onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called")
        }
        cleanup()
    }

    private fun cleanup() {
        if (player != null) {
            player!!.destroy()
            player = null
        }
    }

    fun stopService() {
        cleanup()
        stopSelf()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AudioServiceLeakFix.Companion.preventLeakOf(base))
    }

    public override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    class LocalBinder internal constructor(playerService: PlayerService) : Binder() {
        private val playerService: WeakReference<PlayerService>

        init {
            this.playerService = WeakReference(playerService)
        }

        fun getService(): PlayerService? {
            return playerService.get()
        }

        fun getPlayer(): Player? {
            return playerService.get()!!.player
        }
    }

    companion object {
        private val TAG: String = PlayerService::class.java.getSimpleName()
        private val DEBUG: Boolean = Player.Companion.DEBUG
    }
}
