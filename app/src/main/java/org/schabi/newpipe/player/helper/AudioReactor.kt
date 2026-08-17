@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.helper

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import androidx.core.content.ContextCompat
import androidx.media.AudioManagerCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

class AudioReactor(
    private val context: Context,
    private val player: ExoPlayer
) : AnalyticsListener {

    private val audioManager: AudioManager? = ContextCompat.getSystemService(context, AudioManager::class.java)

    init {
        player.addAnalyticsListener(this)
    }

    fun dispose() {
        player.removeAnalyticsListener(this)
        notifyAudioSessionUpdate(false, player.audioSessionId)
    }

    /* Audio Manager */

    var volume: Int
        get() = audioManager?.getStreamVolume(STREAM_TYPE) ?: 0
        set(volume) {
            audioManager?.setStreamVolume(STREAM_TYPE, volume, 0)
        }

    val maxVolume: Int
        get() = audioManager?.let { AudioManagerCompat.getStreamMaxVolume(it, STREAM_TYPE) } ?: 0

    /* Audio Processing */

    override fun onAudioSessionIdChanged(
        eventTime: AnalyticsListener.EventTime,
        audioSessionId: Int
    ) {
        notifyAudioSessionUpdate(true, audioSessionId)
    }

    private fun notifyAudioSessionUpdate(active: Boolean, audioSessionId: Int) {
        val intent = Intent(
            if (active) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
            } else {
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
            }
        ).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        }
        context.sendBroadcast(intent)
    }

    companion object {
        private const val STREAM_TYPE = AudioManager.STREAM_MUSIC
    }
}
