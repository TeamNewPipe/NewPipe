package org.newpipe.externalplayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MediaNotificationManager(private val context: Context) {

    fun buildNotification(player: com.google.android.exoplayer2.ExoPlayer?, token: android.os.Parcelable?): Notification {
        val playIntent = Intent(context, PlayerService::class.java).apply { action = PlayerService.ACTION_PLAY }
        val pauseIntent = Intent(context, PlayerService::class.java).apply { action = PlayerService.ACTION_PAUSE }
        val stopIntent = Intent(context, PlayerService::class.java).apply { action = PlayerService.ACTION_STOP }

        val playPending = PendingIntent.getService(context, 0, playIntent, PendingIntent.FLAG_IMMUTABLE)
        val pausePending = PendingIntent.getService(context, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopPending = PendingIntent.getService(context, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val isPlaying = player?.isPlaying == true
        val action = if (isPlaying) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pausePending)
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", playPending)
        }

        val mediaStyle = MediaStyle()
        token?.let { mediaStyle.setMediaSession(it as android.media.session.MediaSession.Token) }

        val builder = NotificationCompat.Builder(context, PlayerService.CHANNEL_ID)
            .setContentTitle("NewPipe External Player")
            .setContentText("Playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, android.R.mipmap.sym_def_app_icon))
            .addAction(action)
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, "Stop", stopPending))
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)

        return builder.build()
    }
}