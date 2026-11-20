package org.schabi.newpipe.local.feed.archive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class FeedArchiveTest {
    @Test
    fun archiveIsOptInPagedProfileSpecificAndReversible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = "archive-test-${System.nanoTime()}"
        val other = "$profile-other"
        val items = (0 until 55).map { index ->
            StreamInfoItem(
                0,
                "https://example.org/watch?v=$index",
                "Video $index",
                StreamType.VIDEO_STREAM
            ).apply { uploaderName = "Creator" }
        }
        withFeedArchive(context) { archive ->
            try {
                archive.clear(profile)
                assertTrue(archive.page(profile, 0).isEmpty())
                archive.record(profile, items)
                assertTrue(archive.page(profile, 0).isEmpty())

                FeedArchiveSettings.set(context, profile, 1)
                archive.record(profile, items)
                archive.record(profile, items) // Duplicate fetches must not duplicate rows.
                assertEquals(50, archive.page(profile, 0).size)
                assertEquals(5, archive.page(profile, 1).size)
                assertTrue(archive.page(other, 0).isEmpty())

                FeedArchiveSettings.set(context, profile, 0)
                archive.prune(profile)
                assertTrue(archive.page(profile, 0).isEmpty())
            } finally {
                FeedArchiveSettings.set(context, profile, 0)
                archive.clear(profile)
            }
        }
    }
}
