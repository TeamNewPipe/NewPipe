package org.schabi.newpipe.local.feed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.StreamFilterSelection
import org.schabi.newpipe.util.StreamListFilter

@RunWith(AndroidJUnit4::class)
class FeedPartialRefreshTest {
    @Test
    fun undatedPremiereSurvivesFeedStorageAndHonorsUpcomingVisibilityAfterReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = TestDatabase.createReplacingNewPipeDatabase()
        try {
            val profile = SubscriptionEntity.DEFAULT_PROFILE_ID
            val subscription = database.subscriptionDAO().insert(
                SubscriptionEntity(serviceId = 0, url = "https://example.com/channel", name = "Channel", profileId = profile)
            )
            val manager = FeedDatabaseManager(context, profile)
            val scheduled = stream("scheduled", StreamType.VIDEO_STREAM).apply {
                uploadDate = null
                upcomingStatus = true
            }
            manager.upsertAll(subscription, listOf(scheduled))
            val membership = database.feedDAO().getMemberships(subscription).single()
            val restored = requireNotNull(database.streamDAO().getStreamDirect(membership.streamId)).toStreamInfoItem()
            assertEquals(true, restored.upcomingStatus)
            val defaults = setOf(StreamListFilter.VIDEOS, StreamListFilter.LIVE, StreamListFilter.SHORTS)
            assertFalse(StreamFilterSelection.parse("").matchesVisible(restored, null, defaults, true, true))
            assertTrue(StreamFilterSelection.parse("UPCOMING").matchesVisible(restored, null, defaults, true, true))

            // A subsequent RSS-like response has a publication date but no schedule metadata.
            manager.upsertAll(subscription, listOf(stream("scheduled", StreamType.VIDEO_STREAM)), isComplete = false)
            assertEquals(true, requireNotNull(database.streamDAO().getStreamDirect(membership.streamId)).upcomingStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun partialRefreshAddsUploadsWithoutRemovingCachedLiveStreams() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = TestDatabase.createReplacingNewPipeDatabase()
        try {
            val profile = SubscriptionEntity.DEFAULT_PROFILE_ID
            val subscription = database.subscriptionDAO().insert(
                SubscriptionEntity(serviceId = 0, url = "https://example.com/channel", name = "Channel", profileId = profile)
            )
            val manager = FeedDatabaseManager(context, profile)
            val live = stream("live", StreamType.LIVE_STREAM)
            val upload = stream("upload", StreamType.VIDEO_STREAM)
            manager.upsertAll(subscription, listOf(live))
            val original = database.feedDAO().getMemberships(subscription).single()
            manager.upsertAll(subscription, listOf(upload), isComplete = false)
            assertEquals(2, database.feedDAO().getMemberships(subscription).size)
            assertTrue(database.feedDAO().getMemberships(subscription).any { it.streamId == original.streamId })
            manager.upsertAll(subscription, emptyList(), isComplete = false)
            assertEquals(2, database.feedDAO().getMemberships(subscription).size)
            manager.upsertAll(subscription, listOf(upload))
            assertEquals(1, database.feedDAO().getMemberships(subscription).size)
        } finally {
            database.close()
        }
    }

    private fun stream(id: String, type: StreamType) = StreamInfoItem(0, "https://example.com/$id", id, type).apply {
        uploadDate = DateWrapper(OffsetDateTime.now())
        uploaderName = "Channel"
        uploaderUrl = "https://example.com/channel"
    }
}
