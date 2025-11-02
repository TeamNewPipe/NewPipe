package org.newpipe.externalplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem

class PlayerService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "external_player_channel"
        const val NOTIF_ID = 1

        const val ACTION_PLAY = "org.newpipe.externalplayer.action.PLAY"
        const val ACTION_PAUSE = "org.newpipe.externalplayer.action.PAUSE"
        const val ACTION_STOP = "org.newpipe.externalplayer.action.STOP"
        const val ACTION_SET_URI = "org.newpipe.externalplayer.action.SET_URI"
        const val EXTRA_URI = "uri"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var player: ExoPlayer? = null
    private lateinit var notificationManager: MediaNotificationManager

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "ExternalPlayerSession").apply { isActive = true }
        notificationManager = MediaNotificationManager(this)
        initializePlayer()
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this).build()
            player?.addListener(object : com.google.android.exoplayer2.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackState()
                    startForegroundIfNeeded()
                }
            })
        }
    }

    private fun startForegroundIfNeeded() {
        val notification = notificationManager.buildNotification(player, mediaSession.sessionToken)
        if (player?.isPlaying == true) {
            startForeground(NOTIF_ID, notification)
        } else {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, notification)
        }
    }

    private fun updatePlaybackState() {
        val state = if (player?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val pos = player?.currentPosition ?: 0L
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, pos, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> player?.play()
                ACTION_PAUSE -> player?.pause()
                ACTION_STOP -> {
                    player?.stop()
                    stopForeground(true)
                    stopSelf()
                }
                ACTION_SET_URI -> {
                    val uri = intent.getStringExtra(EXTRA_URI)
                    if (uri != null) setMediaUri(uri)
                }
            }
        }
        startForegroundIfNeeded()
        return START_STICKY
    }

    private fun setMediaUri(uri: String) {
        initializePlayer()
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, uri)
        mediaSession.setMetadata(metadataBuilder.build())
        updatePlaybackState()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "External Player", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }
}