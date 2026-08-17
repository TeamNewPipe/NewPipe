@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import org.schabi.newpipe.App
import org.schabi.newpipe.DebugConstants
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper

object PlayerHolder {
    private val DEBUG = DebugConstants.DEBUG
    private val TAG = PlayerHolder::class.java.simpleName

    private var listener: PlayerServiceExtendedEventListener? = null

    private val serviceConnection = PlayerServiceConnection()
    var isBound = false
        private set

    private var playerService: PlayerService? = null

    private val player: Player?
        get() = playerService?.player

    private val playQueue: PlayQueue?
        get() = player?.playQueue

    val type: PlayerType?
        get() = player?.playerType

    val isPlaying: Boolean
        get() = player?.isPlaying() ?: false

    val isPlayerOpen: Boolean
        get() = player != null

    val isPlayQueueReady: Boolean
        get() = playQueue != null

    val queueSize: Int
        get() = playQueue?.size() ?: 0

    val queuePosition: Int
        get() = playQueue?.index ?: 0

    fun setListener(newListener: PlayerServiceExtendedEventListener?) {
        listener = newListener

        if (listener == null) {
            return
        }

        playerService?.let {
            listener?.onServiceConnected(it)
            startPlayerListener()
        }
    }

    private val commonContext: Context
        get() = App.instance

    fun startService(playAfterConnect: Boolean, newListener: PlayerServiceExtendedEventListener?) {
        if (DEBUG) {
            Log.d(TAG, "startService() called with playAfterConnect=$playAfterConnect")
        }
        val context = commonContext
        setListener(newListener)
        if (isBound) {
            return
        }
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
        playerService?.destroyPlayerAndStopService()
        val context = commonContext
        unbind(context)
        context.stopService(Intent(context, PlayerService::class.java))
    }

    private class PlayerServiceConnection : ServiceConnection {
        var playAfterConnect = false
            private set

        fun doPlayAfterConnect(playAfterConnection: Boolean) {
            playAfterConnect = playAfterConnection
        }

        override fun onServiceDisconnected(compName: ComponentName) {
            if (DEBUG) {
                Log.d(TAG, "Player service is disconnected")
            }

            val context = App.instance
            unbind(context)
        }

        override fun onServiceConnected(compName: ComponentName, service: IBinder) {
            if (DEBUG) {
                Log.d(TAG, "Player service is connected")
            }
            val localBinder = service as PlayerService.LocalBinder

            playerService = localBinder.service
            playerService?.let {
                listener?.onServiceConnected(it)
            }
            startPlayerListener()

            if (playerService?.player != null) {
                // NavigationHelper.sendPlayerStartedEvent(localBinder.service)
            }
        }

        fun consumePlayAfterConnect() {
            playAfterConnect = false
        }
    }

    private fun bind(context: Context) {
        if (DEBUG) {
            Log.d(TAG, "bind() called")
        }
        isBound = bind(context, Context.BIND_AUTO_CREATE)
        if (!isBound) {
            context.unbindService(serviceConnection)
        }
    }

    fun tryBindIfNeeded(context: Context) {
        if (!isBound) {
            bind(context, 0)
        }
    }

    private fun bind(context: Context, flags: Int): Boolean {
        val serviceIntent = Intent(context, PlayerService::class.java)
        serviceIntent.action = PlayerService.BIND_PLAYER_HOLDER_ACTION
        return context.bindService(serviceIntent, serviceConnection, flags)
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
            listener?.let {
                it.onPlayerDisconnected()
                it.onServiceDisconnected()
            }
        }
    }

    private fun startPlayerListener() {
        playerService?.setPlayerListener { player ->
            if (listener != null) {
                if (player == null) {
                    listener?.onPlayerDisconnected()
                } else {
                    listener?.onPlayerConnected(player, serviceConnection.playAfterConnect)
                    serviceConnection.consumePlayAfterConnect()
                    player.setUiListener(internalListener)
                }
            }
        }
        player?.setUiListener(internalListener)
    }

    private fun stopPlayerListener() {
        playerService?.setPlayerListener(null)
        player?.removeUiListener(internalListener)
    }

    private val internalListener = object : PlayerServiceEventListener {
        override fun onViewCreated() {
            listener?.onViewCreated()
        }

        override fun onFullscreenStateChanged(fullscreen: Boolean) {
            listener?.onFullscreenStateChanged(fullscreen)
        }

        override fun onScreenRotationButtonClicked() {
            listener?.onScreenRotationButtonClicked()
        }

        override fun onMoreOptionsLongClicked() {
            listener?.onMoreOptionsLongClicked()
        }

        override fun onPlayerError(error: PlaybackException?, isCatchableException: Boolean) {
            listener?.onPlayerError(error, isCatchableException)
        }

        override fun hideSystemUiIfNeeded() {
            listener?.hideSystemUiIfNeeded()
        }

        override fun onQueueUpdate(queue: PlayQueue?) {
            listener?.onQueueUpdate(queue)
        }

        override fun onPlaybackUpdate(
            state: Int,
            repeatMode: Int,
            shuffled: Boolean,
            parameters: PlaybackParameters?
        ) {
            listener?.onPlaybackUpdate(state, repeatMode, shuffled, parameters)
        }

        override fun onProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int) {
            listener?.onProgressUpdate(currentProgress, duration, bufferPercent)
        }

        override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) {
            listener?.onMetadataUpdate(info, queue)
        }

        override fun onAudioTrackUpdate() {
            listener?.onAudioTrackUpdate()
        }

        override fun onServiceStopped() {
            listener?.onServiceStopped()
            unbind(commonContext)
        }
    }
}
