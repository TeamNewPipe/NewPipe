/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.tv.ui.TvPlayerActivity
import org.schabi.newpipe.tv.util.TvNavigationHelper
import org.schabi.newpipe.util.DeviceUtils

class TvNavigationHelperTest {

    @Test
    fun testTvNavigationHelperConstants() {
        assertEquals("tv_service_id", TvNavigationHelper.KEY_SERVICE_ID)
        assertEquals("tv_url", TvNavigationHelper.KEY_URL)
        assertEquals("tv_title", TvNavigationHelper.KEY_TITLE)
        assertEquals("tv_search_query", TvNavigationHelper.KEY_SEARCH_QUERY)
    }

    @Test
    fun testTvPlayerActivityPendingQueue() {
        val streamItem = StreamInfoItem(
            0,
            "https://example.com/video",
            "Title",
            StreamType.VIDEO_STREAM
        )
        val queue = SinglePlayQueue(streamItem)

        TvPlayerActivity.setPendingPlayQueue(queue)
        // Clear pending queue
        TvPlayerActivity.setPendingPlayQueue(null)
    }

    @Test
    fun testDeviceUtilsConfirmKeys() {
        assertTrue(DeviceUtils.isConfirmKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(DeviceUtils.isConfirmKey(KeyEvent.KEYCODE_ENTER))
        assertTrue(DeviceUtils.isConfirmKey(KeyEvent.KEYCODE_SPACE))
        assertTrue(DeviceUtils.isConfirmKey(KeyEvent.KEYCODE_NUMPAD_ENTER))

        assertFalse(DeviceUtils.isConfirmKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(DeviceUtils.isConfirmKey(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(DeviceUtils.isConfirmKey(KeyEvent.KEYCODE_BACK))
    }
}
