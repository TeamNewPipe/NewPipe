package org.schabi.newpipe.cast.session

import org.schabi.newpipe.cast.model.CastPlayerState

interface CastStateListener {
    fun onSessionStarted(session: CastSession)
    fun onSessionEnded(session: CastSession, error: Throwable? = null)
    fun onPlaybackStateChanged(state: CastPlayerState)
    fun onProgressUpdated(positionMs: Long, durationMs: Long)
    fun onMediaCompleted()
    fun onVolumeChanged(volume: Float)
}
