package org.schabi.newpipe.local.feed

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FeedAllExclusionsTest {
    @Test
    fun manualExclusionsAreReversibleAndIsolatedByProfile() {
        val context = RuntimeEnvironment.getApplication()
        val firstProfile = "11111111-1111-1111-1111-111111111111"
        val otherProfile = "22222222-2222-2222-2222-222222222222"
        val url = "https://example.com/channel"
        if (FeedAllExclusions.isExcluded(context, firstProfile, 0, url)) {
            FeedAllExclusions.toggle(context, firstProfile, 0, url)
        }
        assertTrue(FeedAllExclusions.toggle(context, firstProfile, 0, url))
        assertTrue(FeedAllExclusions.isExcluded(context, firstProfile, 0, url))
        assertFalse(FeedAllExclusions.isExcluded(context, otherProfile, 0, url))
        assertFalse(FeedAllExclusions.isExcluded(context, firstProfile, 1, url))
        assertFalse(FeedAllExclusions.toggle(context, firstProfile, 0, url))
        assertFalse(FeedAllExclusions.isExcluded(context, firstProfile, 0, url))
    }
}
