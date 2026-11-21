package org.schabi.newpipe.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatLandscapeOverlayTest {
    @Test
    fun liveChatOverlaysVideoInPhoneLandscapeWhenOptedIn() {
        assertTrue(LiveChatLandscapeOverlay.shouldOverlay(true, true, false, true))
        assertFalse(LiveChatLandscapeOverlay.shouldOverlay(true, true, false, false))
    }

    @Test
    fun regularCommentsPortraitAndTvPreserveResizedSidecar() {
        assertFalse(LiveChatLandscapeOverlay.shouldOverlay(false, true, false, true))
        assertFalse(LiveChatLandscapeOverlay.shouldOverlay(true, false, false, true))
        assertFalse(LiveChatLandscapeOverlay.shouldOverlay(true, true, true, true))
    }
}
