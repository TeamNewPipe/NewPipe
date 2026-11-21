package org.schabi.newpipe.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Reduces decoder pressure while a high-speed popup switches sources.
 *
 * This is a mitigation, not a handler for native MediaCodec allocation failures. The original
 * speed and pitch are not persisted or changed in preferences. Manual speed changes win.
 */
internal class PopupQualityTransition(private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: ExoPlayer? = null
    private var sourceUrl: String? = null
    private var originalParameters: PlaybackParameters? = null
    private var sawBuffering = false
    private val timeout = Runnable { finish() }

    fun beforeQualityReload() {
        // Complete a previous transition before evaluating a second quality selection.
        finish()
        val active = player.getExoPlayer() ?: return
        val parameters = active.playbackParameters
        if (!PopupQualitySwitchPolicy.shouldStabilize(
                player.playerType,
                active.playWhenReady,
                player.isLive,
                parameters.speed
            )
        ) {
            return
        }
        val url = player.playQueue?.item?.url ?: return
        engine = active
        sourceUrl = url
        originalParameters = parameters
        sawBuffering = false
        // Temporarily lower decoder throughput before recreating the media sources.
        active.playbackParameters = PlaybackParameters(1.0f, parameters.pitch)
        handler.postDelayed(timeout, MAX_TRANSITION_MILLIS)
    }

    fun onPlaybackStateChanged(state: Int) {
        if (engine == null) return
        if (state == Media3Player.STATE_BUFFERING) {
            sawBuffering = true
        } else if (PopupQualitySwitchPolicy.shouldFinishOnState(state, sawBuffering)) {
            finish()
        }
    }

    /** Drop pending restoration when the old engine is being released. */
    fun cancel() {
        handler.removeCallbacks(timeout)
        engine = null
        sourceUrl = null
        originalParameters = null
        sawBuffering = false
    }

    private fun finish() {
        handler.removeCallbacks(timeout)
        val previous = originalParameters
        val oldEngine = engine
        val oldUrl = sourceUrl
        cancel()
        if (previous != null && oldEngine != null &&
            PopupQualitySwitchPolicy.shouldRestore(
                player.getExoPlayer() === oldEngine,
                oldUrl == player.playQueue?.item?.url,
                oldEngine.playbackParameters.speed
            )
        ) {
            oldEngine.playbackParameters = previous
        }
    }

    companion object {
        // Avoid keeping users at the temporary speed if a network source never reaches READY.
        private const val MAX_TRANSITION_MILLIS = 8_000L
    }
}

internal object PopupQualitySwitchPolicy {
    fun shouldStabilize(
        type: PlayerType,
        playWhenReady: Boolean,
        live: Boolean,
        speed: Float
    ): Boolean {
        return type == PlayerType.POPUP && playWhenReady && !live &&
            speed.isFinite() && speed >= 3.0f
    }

    fun shouldRestore(sameEngine: Boolean, sameMedia: Boolean, currentSpeed: Float): Boolean {
        return sameEngine && sameMedia && currentSpeed == 1.0f
    }

    /** STATE_IDLE may be an intermediate source-replacement state, not a completed switch. */
    fun shouldFinishOnState(state: Int, sawBuffering: Boolean): Boolean {
        return state == Media3Player.STATE_ENDED ||
            state == Media3Player.STATE_READY && sawBuffering
    }
}
