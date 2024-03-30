package org.schabi.newpipe.player.helper

import com.google.android.exoplayer2.DefaultLoadControl

class LoadController() : DefaultLoadControl() {
    private var preloadingEnabled: Boolean = true
    public override fun onPrepared() {
        preloadingEnabled = true
        super.onPrepared()
    }

    public override fun onStopped() {
        preloadingEnabled = true
        super.onStopped()
    }

    public override fun onReleased() {
        preloadingEnabled = true
        super.onReleased()
    }

    public override fun shouldContinueLoading(playbackPositionUs: Long,
                                              bufferedDurationUs: Long,
                                              playbackSpeed: Float): Boolean {
        if (!preloadingEnabled) {
            return false
        }
        return super.shouldContinueLoading(
                playbackPositionUs, bufferedDurationUs, playbackSpeed)
    }

    fun disablePreloadingOfCurrentTrack() {
        preloadingEnabled = false
    }

    companion object {
        val TAG: String = "LoadController"
    }
}
