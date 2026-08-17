@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.helper

import androidx.media3.exoplayer.DefaultLoadControl

class LoadController : DefaultLoadControl() {

    private var preloadingEnabled = true

    override fun onPrepared() {
        preloadingEnabled = true
        super.onPrepared()
    }

    override fun onStopped() {
        preloadingEnabled = true
        super.onStopped()
    }

    override fun onReleased() {
        preloadingEnabled = true
        super.onReleased()
    }

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float
    ): Boolean {
        if (!preloadingEnabled) {
            return false
        }
        return super.shouldContinueLoading(
            playbackPositionUs,
            bufferedDurationUs,
            playbackSpeed
        )
    }

    fun disablePreloadingOfCurrentTrack() {
        preloadingEnabled = false
    }

    companion object {
        const val TAG = "LoadController"
    }
}
