package org.schabi.newpipe.cast.session

import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastPlayerState

interface CastSession {
    val device: CastDevice

    fun connect()
    fun disconnect()
    fun isConnected(): Boolean

    fun loadMedia(
        streamUrl: String,
        title: String,
        artist: String? = null,
        thumbnailUrl: String? = null,
        startPositionMs: Long = 0L
    )

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)

    fun getPositionMs(): Long
    fun getDurationMs(): Long
    fun getPlayerState(): CastPlayerState
}
