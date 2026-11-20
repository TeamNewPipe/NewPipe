package org.schabi.newpipe.fragments.list.chat

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LiveChatSourceTest {
    @Test
    fun chatIsNotReusedWhenNavigatingToAnotherVideoOrService() {
        val chat = LiveChatFragment.newInstance(0, "https://example.com/live/one")

        assertTrue(chat.matchesSource(0, "https://example.com/live/one"))
        assertFalse(chat.matchesSource(0, "https://example.com/live/two"))
        assertFalse(chat.matchesSource(1, "https://example.com/live/one"))
    }
}
