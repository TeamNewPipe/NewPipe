package org.schabi.newpipe.player.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerService.LocalBinder
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function

class PlayerHolder private constructor() {
    private var listener: PlayerServiceExtendedEventListener? = null

    private val serviceConnection = PlayerServiceConnection()
    var isBound: Boolean = false
        private set
    private var playerService: PlayerService? = null

    private val player: Player?
        get() = playerService?.player

    private val playQueue: PlayQueue?
        get() = // player play queue might be null e.g. while player is starting
            this.player?.playQueue

    val type: PlayerType?
        /**
         * Returns the current [PlayerType] of the [PlayerService] service,
         * otherwise `null` if no service is running.
         *
         * @return Current PlayerType
         */
        get() = this.player?.playerType

    val isPlaying: Boolean
        get() = this.player?.isPlaying == true

    val isPlayerOpen: Boolean
        get() = this.player != null

    val isPlayQueueReady: Boolean
        /**
         * Use this method to only allow the user to manipulate the play queue (e.g. by enqueueing via
         * the stream long press menu) when there actually is a play queue to manipulate.
         * @return true only if the player is open and its play queue is ready (i.e. it is not null)
         */
        get() = this.playQueue != null

    val queueSize: Int
        get() = this.playQueue?.size() ?: 0

    val queuePosition: Int
        get() = this.playQueue?.index ?: 0

    fun setListener(newListener: PlayerServiceExtendedEventListener?) {
        listener = newListener

        // Force reload data from service
        newListener?.let { listener ->
            playerService?.let {
                listener.onServiceConnected(it)
                startPlayerListener()
                // ^ will call listener.onPlayerConnected() down the line if there is an active player
            }
        }
    }

    private val commonContext: Context
        // helper to handle context in common place as using the same
        get() = App.instance

    /**
     * Connect to (and if needed start) the [PlayerService]
     * and bind [PlayerServiceConnection] to it.
     * If the service is already started, only set the listener.
     * @param playAfterConnect If this holder’s service was already started,
     * start playing immediately
     * @param newListener set this listener
     */
    fun startService(
        playAfterConnect: Boolean,
        newListener: PlayerServiceExtendedEventListener?
    ) {
        if (DEBUG) {
            Log.d(TAG, "startService() called with playAfterConnect=" + playAfterConnect)
        }
        val context = this.commonContext
        setListener(newListener)
        if (this.isBound) {
            return
        }
        // startService() can be called concurrently and it will give a random crashes
        // and NullPointerExceptions inside the service because the service will be
        // bound twice. Prevent it with unbinding first
        unbind(context)
        val intent = Intent(context, PlayerService::class.java)
        intent.putExtra(PlayerService.SHOULD_START_FOREGROUND_EXTRA, true)
        ContextCompat.startForegroundService(context, intent)
        serviceConnection.doPlayAfterConnect(playAfterConnect)
        bind(context)
    }

    fun stopService() {
        if (DEBUG) {
            Log.d(TAG, "stopService() called")
        }
        if (playerService != null) {
            playerService!!.destroyPlayerAndStopService()
        }
        val context = this.commonContext
        unbind(context)
        // destroyPlayerAndStopService() already runs the next line of code, but run it again just
        // to make sure to stop the service even if playerService is null by any chance.
        context.stopService(Intent(context, PlayerService::class.java))
    }

    internal inner class PlayerServiceConnection : ServiceConnection {
        internal var playAfterConnect = false

        /**
         * @param playAfterConnection Sets the value of [playAfterConnect] to pass to the
         * [PlayerServiceExtendedEventListener.onPlayerConnected] the next time it
         * is called. The value of [playAfterConnect] will be reset to false after that.
         */
        fun doPlayAfterConnect(playAfterConnection: Boolean) {
            this.playAfterConnect = playAfterConnection
        }

        override fun onServiceDisconnected(compName: ComponentName?) {
            if (DEBUG) {
                Log.d(TAG, "Player service is disconnected")
            }

            val context: Context = this@PlayerHolder.commonContext
            unbind(context)
        }

        override fun onServiceConnected(compName: ComponentName?, service: IBinder?) {
            if (DEBUG) {
                Log.d(TAG, "Player service is connected")
            }
            val localBinder = service as LocalBinder

            val s = localBinder.service
            requireNotNull(s) {
                (
                    "PlayerService.LocalBinder.getService() must never be" +
                        "null after the service connects"
                    )
            }
            playerService = s
            val l = listener
            if (l != null) {
                l.onServiceConnected(s)
                player?.let {
                    l.onPlayerConnected(it, playAfterConnect)
                }
            }
            startPlayerListener()

            // ^ will call listener.onPlayerConnected() down the line if there is an active player

            // notify the main activity that binding the service has completed, so that it can
            // open the bottom mini-player
            NavigationHelper.sendPlayerStartedEvent(s)
        }
    }

    private fun bind(context: Context) {
        if (DEBUG) {
            Log.d(TAG, "bind() called")
        }
        // BIND_AUTO_CREATE starts the service if it's not already running
        this.isBound = bind(context, Context.BIND_AUTO_CREATE)
        if (!this.isBound) {
            context.unbindService(serviceConnection)
        }
    }

    fun tryBindIfNeeded(context: Context) {
        if (!this.isBound) {
            // flags=0 means the service will not be started if it does not already exist. In this
            // case the return value is not useful, as a value of "true" does not really indicate
            // that the service is going to be bound.
            bind(context, 0)
        }
    }

    private fun bind(context: Context, flags: Int): Boolean {
        val serviceIntent = Intent(context, PlayerService::class.java)
        serviceIntent.setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION)
        return context.bindService(serviceIntent, serviceConnection, flags)
    }

    private fun unbind(context: Context) {
        if (DEBUG) {
            Log.d(TAG, "unbind() called")
        }

        if (this.isBound) {
            context.unbindService(serviceConnection)
            this.isBound = false
            stopPlayerListener()
            playerService = null
            if (listener != null) {
                listener!!.onPlayerDisconnected()
                listener!!.onServiceDisconnected()
            }
        }
    }

    private fun startPlayerListener() {
        if (playerService != null) {
            // setting the player listener will take care of calling relevant callbacks if the
            // player in the service is (not) already active, also see playerStateListener below
            playerService!!.setPlayerListener(playerStateListener)
        }
        this.player?.setFragmentListener(internalListener)
    }

    private fun stopPlayerListener() {
        if (playerService != null) {
            playerService!!.setPlayerListener(null)
        }
        this.player?.removeFragmentListener(internalListener)
    }

    /**
     * This listener will be held by the players created by [PlayerService].
     */
    private val internalListener: PlayerServiceEventListener = object : PlayerServiceEventListener {
        override fun onViewCreated() {
            if (listener != null) {
                listener!!.onViewCreated()
            }
        }

        override fun onFullscreenStateChanged(fullscreen: Boolean) {
            if (listener != null) {
                listener!!.onFullscreenStateChanged(fullscreen)
            }
        }

        override fun onScreenRotationButtonClicked() {
            if (listener != null) {
                listener!!.onScreenRotationButtonClicked()
            }
        }

        override fun onMoreOptionsLongClicked() {
            if (listener != null) {
                listener!!.onMoreOptionsLongClicked()
            }
        }

        override fun onPlayerError(
            error: PlaybackException?,
            isCatchableException: Boolean
        ) {
            if (listener != null) {
                listener!!.onPlayerError(error, isCatchableException)
            }
        }

        override fun hideSystemUiIfNeeded() {
            if (listener != null) {
                listener!!.hideSystemUiIfNeeded()
            }
        }

        override fun onQueueUpdate(queue: PlayQueue?) {
            if (listener != null) {
                listener!!.onQueueUpdate(queue)
            }
        }

        override fun onPlaybackUpdate(
            state: Int,
            repeatMode: Int,
            shuffled: Boolean,
            parameters: PlaybackParameters?
        ) {
            if (listener != null) {
                listener!!.onPlaybackUpdate(state, repeatMode, shuffled, parameters)
            }
        }

        override fun onProgressUpdate(
            currentProgress: Int,
            duration: Int,
            bufferPercent: Int
        ) {
            if (listener != null) {
                listener!!.onProgressUpdate(currentProgress, duration, bufferPercent)
            }
        }

        override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) {
            if (listener != null) {
                listener!!.onMetadataUpdate(info, queue)
            }
        }

        override fun onServiceStopped() {
            if (listener != null) {
                listener!!.onServiceStopped()
            }
            unbind(this@PlayerHolder.commonContext)
        }
    }

    /**
     * This listener will be held by bound [PlayerService]s to notify of the player starting
     * or stopping. This is necessary since the service outlives the player e.g. to answer Android
     * Auto media browser queries.
     */
    private val playerStateListener = Consumer { player: Player? ->
        if (listener != null) {
            if (player == null) {
                // player.fragmentListener=null is already done by player.stopActivityBinding(),
                // which is called by player.destroy(), which is in turn called by PlayerService
                // before setting its player to null
                listener!!.onPlayerDisconnected()
            } else {
                listener!!.onPlayerConnected(player, serviceConnection.playAfterConnect)
                // reset the value of playAfterConnect: if it was true before, it is now "consumed"
                serviceConnection.playAfterConnect = false;
                player.setFragmentListener(internalListener)
            }
        }
    }

    companion object {
        private var instance: PlayerHolder? = null

        @Synchronized
        fun getInstance(): PlayerHolder {
            if (instance == null) {
                instance = PlayerHolder()
            }
            return instance!!
        }

        private val DEBUG = MainActivity.DEBUG
        private val TAG: String = PlayerHolder::class.java.getSimpleName()
    }
}
