package org.schabi.newpipe.player.event

import com.google.android.exoplayer2.PlaybackException

open interface PlayerServiceEventListener : PlayerEventListener {
    fun onViewCreated()
    fun onFullscreenStateChanged(fullscreen: Boolean)
    fun onScreenRotationButtonClicked()
    fun onMoreOptionsLongClicked()
    fun onPlayerError(error: PlaybackException?, isCatchableException: Boolean)
    fun hideSystemUiIfNeeded()
}
