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

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import androidx.media3.session.MediaSession
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat

import java.lang.ref.WeakReference
import org.schabi.newpipe.DebugConstants
import org.schabi.newpipe.ktx.toDebugString
import org.schabi.newpipe.player.mediabrowser.MediaBrowserImpl

import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.player.notification.NotificationPlayerUi
import org.schabi.newpipe.player.notification.NotificationUtil
import org.schabi.newpipe.util.ThemeHelper

/**
 * One service for all players.
 */
class PlayerService : MediaBrowserServiceCompat() {
    private var mediaBrowserImpl: MediaBrowserImpl? = null

    var mediaSession: MediaSession? = null

    /**
     * @return the current active player instance. May be null, since the player service can outlive
     * the player e.g. to respond to Android Auto media browser queries.
     */
    var player: Player? = null
        private set

    private val mBinder: IBinder = LocalBinder(this)

    /**
     * The parameter taken by this lambda can be null to indicate the player is being
     * stopped.
     */
    private var onPlayerStartedOrStopped: ((Player?) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) {
            Log.d(TAG, "onCreate() called")
        }
        ThemeHelper.setTheme(this)

        mediaBrowserImpl = MediaBrowserImpl(this) { notifyChildrenChanged(it) }


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(
                TAG,
                "onStartCommand() called with: intent = [$intent], extras = [" +
                    intent?.extras.toDebugString() + "], flags = [$flags], startId = [$startId]"
            )
        }

        if (intent?.getBooleanExtra(SHOULD_START_FOREGROUND_EXTRA, false) == true) {
            val playerWasNull = player == null
            if (playerWasNull) {
                player = Player(this)
            }

            player?.startNotification()

            if (playerWasNull) {
                onPlayerStartedOrStopped?.invoke(player)
            }
        }

        val currentPlayer = player
        if (currentPlayer == null) {
            Log.d(TAG, "onStartCommand() got a useless intent, closing the service")
            NotificationUtil.startForegroundWithDummyNotification(this)
            destroyPlayerAndStopService()
            return START_NOT_STICKY
        }

        val oldPlayerType = currentPlayer.playerType
        currentPlayer.handleIntent(intent)
        currentPlayer.handleIntentPost(oldPlayerType)
        currentPlayer.handleMediaButtonIntent(intent)

        return START_NOT_STICKY
    }

    fun stopForImmediateReusing() {
        if (DEBUG) {
            Log.d(TAG, "stopForImmediateReusing() called")
        }
        if (player != null && !player!!.exoPlayerIsNull()) {
            player!!.smoothStopForImmediateReusing()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (player != null && !player!!.videoPlayerSelected()) {
            return
        }
        onDestroy()
        Runtime.getRuntime().halt(0)
    }

    override fun onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called")
        }
        super.onDestroy()
        cleanup()

        mediaSession?.release()
        mediaBrowserImpl?.dispose()
    }

    private fun cleanup() {
        if (player != null) {
            onPlayerStartedOrStopped?.invoke(null)
            player?.destroy()
            player = null
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    fun destroyPlayerAndStopService() {
        if (DEBUG) {
            Log.d(TAG, "destroyPlayerAndStopService() called")
        }
        cleanup()
        stopService(Intent(this, PlayerService::class.java))
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base))
    }

    override fun onBind(intent: Intent): IBinder? {
        if (DEBUG) {
            Log.d(
                TAG,
                "onBind() called with: intent = [$intent], extras = [" +
                    intent.extras.toDebugString() + "]"
            )
        }
        return when (intent.action) {
            BIND_PLAYER_HOLDER_ACTION -> mBinder
            SERVICE_INTERFACE -> super.onBind(intent)
            else -> null
        }
    }

    class LocalBinder(playerService: PlayerService) : Binder() {
        private val playerService: WeakReference<PlayerService> = WeakReference(playerService)
        val service: PlayerService?
            get() = playerService.get()
    }

    /**
     * Sets the listener that will be called when the player is started or stopped. If a
     * `null` listener is passed, then the current listener will be unset. The parameter taken
     * by the lambda can be null to indicate that the player is stopping.
     * @param listener the listener to set or unset
     */
    fun setPlayerListener(listener: ((Player?) -> Unit)?) {
        this.onPlayerStartedOrStopped = listener
        listener?.invoke(player)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return mediaBrowserImpl?.onGetRoot(clientPackageName, clientUid, rootHints)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        mediaBrowserImpl?.onLoadChildren(parentId, result)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        mediaBrowserImpl?.onSearch(query, result)
    }

    companion object {
        private val TAG = PlayerService::class.java.simpleName
        private val DEBUG = DebugConstants.DEBUG

        const val SHOULD_START_FOREGROUND_EXTRA = "should_start_foreground_extra"
        const val BIND_PLAYER_HOLDER_ACTION = "bind_player_holder_action"
    }
}
