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
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import org.schabi.newpipe.ktx.toDebugString
import org.schabi.newpipe.player.mediabrowser.MediaBrowserImpl
import org.schabi.newpipe.player.mediabrowser.MediaBrowserPlaybackPreparer
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.player.notification.NotificationPlayerUi
import org.schabi.newpipe.player.notification.NotificationUtil
import org.schabi.newpipe.util.ThemeHelper
import java.lang.ref.WeakReference
import java.util.function.Consumer

/**
 * One service for all players.
 */
class PlayerService : MediaBrowserServiceCompat() {
    // These objects are used to cleanly separate the Service implementation (in this file) and the
    // media browser and playback preparer implementations. At the moment the playback preparer is
    // only used in conjunction with the media browser.
    private lateinit var mediaBrowserImpl: MediaBrowserImpl
    private lateinit var mediaBrowserPlaybackPreparer: MediaBrowserPlaybackPreparer

    // these are instantiated in onCreate() as per
    // https://developer.android.com/training/cars/media#browser_workflow
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var sessionConnector: MediaSessionConnector

    /**
     * @return the current active player instance. May be null, since the player service can outlive
     * the player e.g. to respond to Android Auto media browser queries.
     */
    var player: Player? = null
        private set

    private val mBinder: IBinder = LocalBinder(this)

    /**
     * The parameter taken by this [Consumer] can be null to indicate the player is being
     * stopped.
     */
    private var onPlayerStartedOrStopped: ((player: Player?) -> Unit)? = null

    //region Service lifecycle
    override fun onCreate() {
        super.onCreate()

        if (DEBUG) {
            Log.d(TAG, "onCreate() called")
        }
        ThemeHelper.setTheme(this)

        mediaBrowserImpl = MediaBrowserImpl(this, this::notifyChildrenChanged)

        // see https://developer.android.com/training/cars/media#browser_workflow
        val session = MediaSessionCompat(this, "MediaSessionPlayerServ")
        mediaSession = session
        setSessionToken(session.sessionToken)
        val connector = MediaSessionConnector(session)
        sessionConnector = connector
        connector.setMetadataDeduplicationEnabled(true)

        mediaBrowserPlaybackPreparer = MediaBrowserPlaybackPreparer(
            context = this,
            setMediaSessionError = connector::setCustomErrorMessage,
            clearMediaSessionError = { connector.setCustomErrorMessage(null) },
            onPrepare = { player?.onPrepare() }
        )
        connector.setPlaybackPreparer(mediaBrowserPlaybackPreparer)

        // Note: you might be tempted to create the player instance and call startForeground here,
        // but be aware that the Android system might start the service just to perform media
        // queries. In those cases creating a player instance is a waste of resources, and calling
        // startForeground means creating a useless empty notification. In case it's really needed
        // the player instance can be created here, but startForeground() should definitely not be
        // called here unless the service is actually starting in the foreground, to avoid the
        // useless notification.
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(
                TAG,
                "onStartCommand() called with: intent = [$intent], extras = [${
                intent.extras.toDebugString()}], flags = [$flags], startId = [$startId]"
            )
        }

        // All internal NewPipe intents used to interact with the player, that are sent to the
        // PlayerService using startForegroundService(), will have SHOULD_START_FOREGROUND_EXTRA,
        // to ensure startForeground() is called (otherwise Android will force-crash the app).
        if (intent.getBooleanExtra(SHOULD_START_FOREGROUND_EXTRA, false)) {
            val playerWasNull = (player == null)
            if (playerWasNull) {
                // make sure the player exists, in case the service was resumed
                player = Player(this, mediaSession, sessionConnector)
            }

            // Be sure that the player notification is set and the service is started in foreground,
            // otherwise, the app may crash on Android 8+ as the service would never be put in the
            // foreground while we said to the system we would do so. The service is always
            // requested to be started in foreground, so always creating a notification if there is
            // no one already and starting the service in foreground should not create any issues.
            // If the service is already started in foreground, requesting it to be started
            // shouldn't do anything.
            player?.UIs()?.get(NotificationPlayerUi::class)?.createNotificationAndStartForeground()

            if (playerWasNull) {
                // notify that a new player was created (but do it after creating the foreground
                // notification just to make sure we don't incur, due to slowness, in
                // "Context.startForegroundService() did not then call Service.startForeground()")
                onPlayerStartedOrStopped?.invoke(player)
            }
        }

        if (player == null) {
            // No need to process media button's actions or other system intents if the player is
            // not running. However, since the current intent might have been issued by the system
            // with `startForegroundService()` (for unknown reasons), we need to ensure that we post
            // a (dummy) foreground notification, otherwise we'd incur in
            // "Context.startForegroundService() did not then call Service.startForeground()". Then
            // we stop the service again.
            Log.d(TAG, "onStartCommand() got a useless intent, closing the service")
            NotificationUtil.startForegroundWithDummyNotification(this)
            return START_NOT_STICKY
        }

        val oldPlayerType = player?.playerType
        player?.handleIntent(intent)
        player?.handleIntentPost(oldPlayerType)
        player?.UIs()?.get(MediaSessionPlayerUi::class.java)
            ?.handleMediaButtonIntent(intent)

        return START_NOT_STICKY
    }

    fun stopForImmediateReusing() {
        if (DEBUG) {
            Log.d(TAG, "stopForImmediateReusing() called")
        }

        val p = player
        if (p != null && !p.exoPlayerIsNull()) {
            // Releases wifi & cpu, disables keepScreenOn, etc.
            // We can't just pause the player here because it will make transition
            // from one stream to a new stream not smooth
            p.smoothStopForImmediateReusing()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val p = player
        if (p != null && !p.videoPlayerSelected()) {
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
        super.onDestroy()

        cleanup()

        mediaBrowserPlaybackPreparer.dispose()
        mediaSession.release()
        mediaBrowserImpl.dispose()
    }

    private fun cleanup() {
        val p = player
        if (p != null) {
            // notify that the player is being destroyed
            onPlayerStartedOrStopped?.invoke(null)
            p.saveAndShutdown()
            player = null
        }

        // Should already be handled by MediaSessionPlayerUi, but just to be sure.
        mediaSession.setActive(false)

        // Should already be handled by NotificationUtil.cancelNotificationAndStopForeground() in
        // NotificationPlayerUi, but let's make sure that the foreground service is stopped.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    /**
     * Destroys the player and allows the player instance to be garbage collected. Sets the media
     * session to inactive. Stops the foreground service and removes the player notification
     * associated with it. Tries to stop the [PlayerService] completely, but this step will
     * have no effect in case some service connection still uses the service (e.g. the Android Auto
     * system accesses the media browser even when no player is running).
     */
    fun destroyPlayerAndStopService() {
        if (DEBUG) {
            Log.d(TAG, "destroyPlayerAndStopService() called")
        }

        cleanup()

        // This only really stops the service if there are no other service connections (see docs):
        // for example the (Android Auto) media browser binder will block stopService().
        // This is why we also stopForeground() above, to make sure the notification is removed.
        // If we were to call stopSelf(), then the service would be surely stopped (regardless of
        // other service connections), but this would be a waste of resources since the service
        // would be immediately restarted by those same connections to perform the queries.
        stopService(Intent(this, PlayerService::class.java))
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base))
    }

    //endregion
    //region Bind
    override fun onBind(intent: Intent): IBinder? {
        if (DEBUG) {
            Log.d(
                TAG,
                "onBind() called with: intent = [$intent], extras = [${
                intent.extras.toDebugString()}]"
            )
        }

        return if (BIND_PLAYER_HOLDER_ACTION == intent.action) {
            // Note that this binder might be reused multiple times while the service is alive, even
            // after unbind() has been called: https://stackoverflow.com/a/8794930 .
            mBinder
        } else if (SERVICE_INTERFACE == intent.action) {
            // MediaBrowserService also uses its own binder, so for actions related to the media
            // browser service, pass the onBind to the superclass.
            super.onBind(intent)
        } else {
            // This is an unknown request, avoid returning any binder to not leak objects.
            null
        }
    }

    class LocalBinder internal constructor(playerService: PlayerService) : Binder() {
        private val playerService = WeakReference(playerService)

        val service: PlayerService?
            get() = playerService.get()
    }

    /**
     * Sets the listener that will be called when the player is started or stopped. If a
     * `null` listener is passed, then the current listener will be unset. The parameter taken
     * by the [Consumer] can be null to indicate that the player is stopping.
     * @param listener the listener to set or unset
     */
    fun setPlayerListener(listener: ((player: Player?) -> Unit)?) {
        this.onPlayerStartedOrStopped = listener
        listener?.invoke(player)
    }

    //endregion
    //region Media browser
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        // TODO check if the accessing package has permission to view data
        return mediaBrowserImpl.onGetRoot(clientPackageName, clientUid, rootHints)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        mediaBrowserImpl.onLoadChildren(parentId, result)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        mediaBrowserImpl.onSearch(query, result)
    } //endregion

    companion object {
        private val TAG: String = PlayerService::class.java.getSimpleName()
        private val DEBUG = Player.DEBUG

        const val SHOULD_START_FOREGROUND_EXTRA: String = "should_start_foreground_extra"
        const val BIND_PLAYER_HOLDER_ACTION: String = "bind_player_holder_action"
    }
}
