package org.schabi.newpipe.player.ui

/** Only the phone landscape live-chat panel may cover the video viewport. */
internal object LiveChatLandscapeOverlay {
    fun shouldOverlay(
        isLiveChat: Boolean,
        landscape: Boolean,
        tv: Boolean,
        enabled: Boolean
    ): Boolean = isLiveChat && landscape && !tv && enabled
}
