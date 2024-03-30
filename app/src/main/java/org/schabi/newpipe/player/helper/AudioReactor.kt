package org.schabi.newpipe.player.helper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime

class AudioReactor(private val context: Context,
                   private val player: ExoPlayer) : OnAudioFocusChangeListener, AnalyticsListener {
    private val audioManager: AudioManager?
    private val request: AudioFocusRequestCompat

    init {
        audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)
        player.addAnalyticsListener(this)
        request = AudioFocusRequestCompat.Builder(FOCUS_GAIN_TYPE) //.setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this)
                .build()
    }

    fun dispose() {
        abandonAudioFocus()
        player.removeAnalyticsListener(this)
        notifyAudioSessionUpdate(false, player.getAudioSessionId())
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Manager
    ////////////////////////////////////////////////////////////////////////// */
    fun requestAudioFocus() {
        AudioManagerCompat.requestAudioFocus((audioManager)!!, request)
    }

    fun abandonAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest((audioManager)!!, request)
    }

    var volume: Int
        get() {
            return audioManager!!.getStreamVolume(STREAM_TYPE)
        }
        set(volume) {
            audioManager!!.setStreamVolume(STREAM_TYPE, volume, 0)
        }
    val maxVolume: Int
        get() {
            return AudioManagerCompat.getStreamMaxVolume((audioManager)!!, STREAM_TYPE)
        }

    /*//////////////////////////////////////////////////////////////////////////
    // AudioFocus
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "onAudioFocusChange() called with: focusChange = [" + focusChange + "]")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> onAudioFocusGain()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onAudioFocusLossCanDuck()
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onAudioFocusLoss()
        }
    }

    private fun onAudioFocusGain() {
        Log.d(TAG, "onAudioFocusGain() called")
        player.setVolume(DUCK_AUDIO_TO)
        animateAudio(DUCK_AUDIO_TO, 1.0f)
        if (PlayerHelper.isResumeAfterAudioFocusGain(context)) {
            player.play()
        }
    }

    private fun onAudioFocusLoss() {
        Log.d(TAG, "onAudioFocusLoss() called")
        player.pause()
    }

    private fun onAudioFocusLossCanDuck() {
        Log.d(TAG, "onAudioFocusLossCanDuck() called")
        // Set the volume to 1/10 on ducking
        player.setVolume(DUCK_AUDIO_TO)
    }

    private fun animateAudio(from: Float, to: Float) {
        val valueAnimator: ValueAnimator = ValueAnimator()
        valueAnimator.setFloatValues(from, to)
        valueAnimator.setDuration(DUCK_DURATION.toLong())
        valueAnimator.addListener(object : AnimatorListenerAdapter() {
            public override fun onAnimationStart(animation: Animator) {
                player.setVolume(from)
            }

            public override fun onAnimationCancel(animation: Animator) {
                player.setVolume(to)
            }

            public override fun onAnimationEnd(animation: Animator) {
                player.setVolume(to)
            }
        })
        valueAnimator.addUpdateListener(AnimatorUpdateListener({ animation: ValueAnimator -> player.setVolume((animation.getAnimatedValue() as Float)) }))
        valueAnimator.start()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Processing
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onAudioSessionIdChanged(eventTime: EventTime,
                                                audioSessionId: Int) {
        notifyAudioSessionUpdate(true, audioSessionId)
    }

    private fun notifyAudioSessionUpdate(active: Boolean, audioSessionId: Int) {
        if (!PlayerHelper.isUsingDSP()) {
            return
        }
        val intent: Intent = Intent(if (active) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName())
        context.sendBroadcast(intent)
    }

    companion object {
        private val TAG: String = "AudioFocusReactor"
        private val DUCK_DURATION: Int = 1500
        private val DUCK_AUDIO_TO: Float = .2f
        private val FOCUS_GAIN_TYPE: Int = AudioManagerCompat.AUDIOFOCUS_GAIN
        private val STREAM_TYPE: Int = AudioManager.STREAM_MUSIC
    }
}
