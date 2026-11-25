package org.schabi.newpipe.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.Renderer
import java.util.concurrent.CopyOnWriteArraySet

/** Shift only the text renderer's clock. Positive offsets display captions later. */
class SubtitleTiming : Player.Listener {
    @Volatile var offsetMs: Long = 0
        private set
    private val offsetListeners = CopyOnWriteArraySet<() -> Unit>()

    fun setOffset(value: Long) {
        val bounded = value.coerceIn(-30_000, 30_000)
        if (offsetMs == bounded) return
        offsetMs = bounded
        offsetListeners.forEach { it() }
    }

    internal fun addOffsetListener(listener: () -> Unit) {
        offsetListeners += listener
    }

    internal fun removeOffsetListener(listener: () -> Unit) {
        offsetListeners -= listener
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = setOffset(0)

    fun wrap(renderer: Renderer): Renderer = object : Renderer by renderer {
        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            renderer.render(shift(positionUs), elapsedRealtimeUs)
        }

        override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) {
            renderer.resetPosition(shift(positionUs), sampleStreamIsResetToKeyFrame)
        }
    }

    internal fun shift(positionUs: Long): Long = positionUs - offsetMs * 1000
}
