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

class PlayerHolder private constructor() {
    private var listener: PlayerServiceExtendedEventListener? = null
    private val serviceConnection: PlayerServiceConnection = PlayerServiceConnection()
    var isBound: Boolean = false
        private set
    private var playerService: PlayerService? = null
    private var player: Player? = null
    val type: PlayerType?
        /**
         * Returns the current [PlayerType] of the [PlayerService] service,
         * otherwise `null` if no service is running.
         *
         * @return Current PlayerType
         */
        get() {
            if (player == null) {
                return null
            }
            return player!!.getPlayerType()
        }
    val isPlaying: Boolean
        get() {
            if (player == null) {
                return false
            }
            return player!!.isPlaying()
        }
    val isPlayerOpen: Boolean
        get() {
            return player != null
        }
    val isPlayQueueReady: Boolean
        /**
         * Use this method to only allow the user to manipulate the play queue (e.g. by enqueueing via
         * the stream long press menu) when there actually is a play queue to manipulate.
         * @return true only if the player is open and its play queue is ready (i.e. it is not null)
         */
        get() {
            return player != null && player!!.getPlayQueue() != null
        }
    val queueSize: Int
        get() {
            if (player == null || player!!.getPlayQueue() == null) {
                // player play queue might be null e.g. while player is starting
                return 0
            }
            return player!!.getPlayQueue()!!.size()
        }
    val queuePosition: Int
        get() {
            if (player == null || player!!.getPlayQueue() == null) {
                return 0
            }
            return player!!.getPlayQueue().getIndex()
        }

    fun setListener(newListener: PlayerServiceExtendedEventListener?) {
        listener = newListener
        if (listener == null) {
            return
        }

        // Force reload data from service
        if (player != null) {
            listener!!.onServiceConnected(player, playerService, false)
            startPlayerListener()
        }
    }

    private val commonContext: Context
        // helper to handle context in common place as using the same
        private get() {
            return App.Companion.getApp()
        }

    fun startService(playAfterConnect: Boolean,
                     newListener: PlayerServiceExtendedEventListener?) {
        val context: Context = commonContext
        setListener(newListener)
        if (isBound) {
            return
        }
        // startService() can be called concurrently and it will give a random crashes
        // and NullPointerExceptions inside the service because the service will be
        // bound twice. Prevent it with unbinding first
        unbind(context)
        ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
        serviceConnection.doPlayAfterConnect(playAfterConnect)
        bind(context)
    }

    fun stopService() {
        val context: Context = commonContext
        unbind(context)
        context.stopService(Intent(context, PlayerService::class.java))
    }

    internal inner class PlayerServiceConnection() : ServiceConnection {
        private var playAfterConnect: Boolean = false
        fun doPlayAfterConnect(playAfterConnection: Boolean) {
            playAfterConnect = playAfterConnection
        }

        public override fun onServiceDisconnected(compName: ComponentName) {
            if (DEBUG) {
                Log.d(TAG, "Player service is disconnected")
            }
            val context: Context = commonContext
            unbind(context)
        }

        public override fun onServiceConnected(compName: ComponentName, service: IBinder) {
            if (DEBUG) {
                Log.d(TAG, "Player service is connected")
            }
            val localBinder: LocalBinder = service as LocalBinder
            playerService = localBinder.getService()
            player = localBinder.getPlayer()
            if (listener != null) {
                listener!!.onServiceConnected(player, playerService, playAfterConnect)
            }
            startPlayerListener()
        }
    }

    private fun bind(context: Context) {
        if (DEBUG) {
            Log.d(TAG, "bind() called")
        }
        val serviceIntent: Intent = Intent(context, PlayerService::class.java)
        isBound = context.bindService(serviceIntent, serviceConnection,
                Context.BIND_AUTO_CREATE)
        if (!isBound) {
            context.unbindService(serviceConnection)
        }
    }

    private fun unbind(context: Context) {
        if (DEBUG) {
            Log.d(TAG, "unbind() called")
        }
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            stopPlayerListener()
            playerService = null
            player = null
            if (listener != null) {
                listener!!.onServiceDisconnected()
            }
        }
    }

    private fun startPlayerListener() {
        if (player != null) {
            player!!.setFragmentListener(internalListener)
        }
    }

    private fun stopPlayerListener() {
        if (player != null) {
            player!!.removeFragmentListener(internalListener)
        }
    }

    private val internalListener: PlayerServiceEventListener = object : PlayerServiceEventListener {
        public override fun onViewCreated() {
            if (listener != null) {
                listener!!.onViewCreated()
            }
        }

        public override fun onFullscreenStateChanged(fullscreen: Boolean) {
            if (listener != null) {
                listener!!.onFullscreenStateChanged(fullscreen)
            }
        }

        public override fun onScreenRotationButtonClicked() {
            if (listener != null) {
                listener!!.onScreenRotationButtonClicked()
            }
        }

        public override fun onMoreOptionsLongClicked() {
            if (listener != null) {
                listener!!.onMoreOptionsLongClicked()
            }
        }

        public override fun onPlayerError(error: PlaybackException?,
                                          isCatchableException: Boolean) {
            if (listener != null) {
                listener!!.onPlayerError(error, isCatchableException)
            }
        }

        public override fun hideSystemUiIfNeeded() {
            if (listener != null) {
                listener!!.hideSystemUiIfNeeded()
            }
        }

        public override fun onQueueUpdate(queue: PlayQueue?) {
            if (listener != null) {
                listener!!.onQueueUpdate(queue)
            }
        }

        public override fun onPlaybackUpdate(state: Int,
                                             repeatMode: Int,
                                             shuffled: Boolean,
                                             parameters: PlaybackParameters?) {
            if (listener != null) {
                listener!!.onPlaybackUpdate(state, repeatMode, shuffled, parameters)
            }
        }

        public override fun onProgressUpdate(currentProgress: Int,
                                             duration: Int,
                                             bufferPercent: Int) {
            if (listener != null) {
                listener!!.onProgressUpdate(currentProgress, duration, bufferPercent)
            }
        }

        public override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) {
            if (listener != null) {
                listener!!.onMetadataUpdate(info, queue)
            }
        }

        public override fun onServiceStopped() {
            if (listener != null) {
                listener!!.onServiceStopped()
            }
            unbind(commonContext)
        }
    }

    companion object {
        @get:Synchronized
        var instance: PlayerHolder? = null
            get() {
                if (field == null) {
                    field = PlayerHolder()
                }
                return field
            }
            private set
        private val DEBUG: Boolean = MainActivity.Companion.DEBUG
        private val TAG: String = PlayerHolder::class.java.getSimpleName()
    }
}
