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

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.player.PlayerService.LocalBinder
import org.schabi.newpipe.player.mediabrowser.MediaBrowserConnector
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.player.notification.NotificationPlayerUi
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import java.lang.ref.WeakReference
import java.util.Objects
import java.util.function.Consumer

/**
 * One service for all players.
 */
class PlayerService : MediaBrowserServiceCompat() {
    private var player: Player? = null

    private val mBinder: IBinder = LocalBinder(this)

    private var mediaBrowserConnector: MediaBrowserConnector? = null
    private val compositeDisposableLoadChildren = CompositeDisposable()

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    override fun onCreate() {
        super.onCreate()

        if (DEBUG) {
            Log.d(TAG, "onCreate() called")
        }
        Localization.assureCorrectAppLanguage(this)
        ThemeHelper.setTheme(this)

        mediaBrowserConnector = MediaBrowserConnector(this)
    }

    private fun initializePlayerIfNeeded() {
        if (player == null) {
            player = Player(this)
            /*
            Create the player notification and start immediately the service in foreground,
            otherwise if nothing is played or initializing the player and its components (especially
            loading stream metadata) takes a lot of time, the app would crash on Android 8+ as the
            service would never be put in the foreground while we said to the system we would do so
             */
            player!!.UIs().get<NotificationPlayerUi?>(NotificationPlayerUi::class.java)
                .ifPresent(Consumer { obj: NotificationPlayerUi? -> obj!!.createNotificationAndStartForeground() })
        }
    }

    // Suppress Sonar warning to not always return the same value, as we need to do some actions
    // before returning
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(
                TAG,
                (
                    "onStartCommand() called with: intent = [" + intent +
                        "], flags = [" + flags + "], startId = [" + startId + "]"
                    )
            )
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
         */
        if (player != null) {
            player!!.UIs().get<NotificationPlayerUi?>(NotificationPlayerUi::class.java)
                .ifPresent(Consumer { obj: NotificationPlayerUi? -> obj!!.createNotificationAndStartForeground() })
        }

        if (Intent.ACTION_MEDIA_BUTTON == intent.getAction() &&
            (player == null || player!!.getPlayQueue() == null)
        ) {
            /*
            No need to process media button's actions if the player is not working, otherwise
            the player service would strangely start with nothing to play
            Stop the service in this case, which will be removed from the foreground and its
            notification cancelled in its destruction
             */
            stopSelf()
            return START_NOT_STICKY
        }

        initializePlayerIfNeeded()
        Objects.requireNonNull<Player?>(player).handleIntent(intent)
        player!!.UIs().get<MediaSessionPlayerUi?>(MediaSessionPlayerUi::class.java)
            .ifPresent(Consumer { ui: MediaSessionPlayerUi? -> ui!!.handleMediaButtonIntent(intent) })

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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (player != null && !player!!.videoPlayerSelected()) {
            return
        }
        onDestroy()
        // Unload from memory completely
        Runtime.getRuntime().halt(0)
    }

    override fun onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called")
        }

        cleanup()

        if (mediaBrowserConnector != null) {
            mediaBrowserConnector!!.release()
            mediaBrowserConnector = null
        }

        compositeDisposableLoadChildren.clear()
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

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base))
    }

    override fun onBind(intent: Intent): IBinder? {
        if (SERVICE_INTERFACE == intent.getAction()) {
            return super.onBind(intent)
        }
        return mBinder
    }

    fun getSessionConnector(): MediaSessionConnector {
        return mediaBrowserConnector!!.getSessionConnector()
    }

    // MediaBrowserServiceCompat methods
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return mediaBrowserConnector!!.onGetRoot(clientPackageName, clientUid, rootHints)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem?>?>
    ) {
        result.detach()
        val disposable = mediaBrowserConnector!!.onLoadChildren(parentId)
            .subscribe(
                io.reactivex.rxjava3.functions.Consumer {
                    result.sendResult(
                        it
                    )
                }
            )
        compositeDisposableLoadChildren.add(disposable)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem?>?>
    ) {
        mediaBrowserConnector!!.onSearch(query, result)
    }

    class LocalBinder internal constructor(playerService: PlayerService?) : Binder() {
        private val playerService: WeakReference<PlayerService?>

        init {
            this.playerService = WeakReference<PlayerService?>(playerService)
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
        private val DEBUG = Player.DEBUG
    }
}
