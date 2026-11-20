package org.schabi.newpipe.player.ui

/** Avoid retaining a landscape lock after a video is minimized outside Android's native PiP. */
object MiniPlayerCollapseOrientationPolicy {
    @JvmStatic
    fun shouldExitFullscreen(
        isVideoPlayer: Boolean,
        isFullscreen: Boolean,
        enteringFullscreen: Boolean,
        nativePipTransition: Boolean,
        nonPhoneWindow: Boolean
    ): Boolean = isVideoPlayer &&
        (isFullscreen || enteringFullscreen) &&
        !nativePipTransition &&
        !nonPhoneWindow
}
