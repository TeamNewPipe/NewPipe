/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.activity

import android.app.Application
import androidx.room.Room
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChannelActivityDAOTest {
    private lateinit var database: AppDatabase
    private val profile = SubscriptionEntity.DEFAULT_PROFILE_ID
    private val channel = "https://example.com/channel"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readsOnlyTheMatchingProfileServiceAndChannelAndDeduplicatesModes() {
        val local = subscription(profile, 0, 3)
        val foreign = subscription("other", 0, 3)
        val anotherService = subscription(profile, 1, 1)
        val shared = stream(0, "shared")
        val foreignOnly = stream(0, "foreign")
        val otherService = stream(1, "other service")
        database.feedDAO().insertAll(
            listOf(FeedEntity(shared, local, 1), FeedEntity(shared, local, 2), FeedEntity(shared, foreign, 1), FeedEntity(foreignOnly, foreign, 1), FeedEntity(otherService, anotherService, 1))
        )
        val snapshot = database.channelActivityDAO().snapshot(profile, 0, channel)
        assertEquals(listOf(shared), snapshot.records.map { it.streamId })
        assertEquals(setOf(shared, foreignOnly), database.channelActivityDAO().snapshot("other", 0, channel).records.map { it.streamId }.toSet())
        assertEquals(listOf(otherService), database.channelActivityDAO().snapshot(profile, 1, channel).records.map { it.streamId })
        assertTrue(database.channelActivityDAO().snapshot(profile, 0, channel + "/different").records.isEmpty())
    }

    @Test
    fun excludesUnsubscribedYouTubeModesAndUsesOnlyTheirRefreshTimestamps() {
        val local = subscription(profile, 0, 1)
        val regular = stream(0, "regular")
        val music = stream(0, "music")
        database.feedDAO().insertAll(listOf(FeedEntity(regular, local, 1), FeedEntity(music, local, 2)))
        val regularRefresh = OffsetDateTime.parse("2026-11-24T10:00:00Z")
        database.feedDAO().setLastUpdatedForSubscription(FeedLastUpdatedEntity(local, 1, regularRefresh))
        database.feedDAO().setLastUpdatedForSubscription(FeedLastUpdatedEntity(local, 2, regularRefresh.plusDays(1)))

        val result = database.channelActivityDAO().snapshot(profile, 0, channel)
        assertEquals(listOf(regular), result.records.map { it.streamId })
        assertEquals(regularRefresh, result.lastUpdated)
        assertNull(database.channelActivityDAO().snapshot("unknown profile", 0, channel).lastUpdated)
    }

    @Test
    fun unrelatedSavedStreamsDoNotImplyObservedChannelHistoryAndDeletionClearsActivity() {
        stream(0, "saved without feed membership")
        assertTrue(database.channelActivityDAO().snapshot(profile, 0, channel).records.isEmpty())
        val local = subscription(profile, 0, 1)
        val observed = stream(0, "observed")
        database.feedDAO().insertAll(listOf(FeedEntity(observed, local, 1)))
        assertEquals(1, database.channelActivityDAO().snapshot(profile, 0, channel).records.size)
        database.subscriptionDAO().delete(requireNotNull(database.subscriptionDAO().getSubscriptionForProfile(profile, local)))
        assertTrue(database.channelActivityDAO().snapshot(profile, 0, channel).records.isEmpty())
    }

    private fun subscription(profileId: String, service: Int, modes: Int): Long = database.subscriptionDAO().insert(
        SubscriptionEntity(serviceId = service, url = channel, name = "Shared display name", profileId = profileId, youtubeModeMask = modes)
    )

    private fun stream(service: Int, id: String): Long = database.streamDAO().insert(
        StreamEntity(
            serviceId = service,
            url = "https://example.com/video/$id",
            title = "Video",
            streamType = StreamType.VIDEO_STREAM,
            duration = 60,
            uploader = "Shared display name",
            uploadDate = OffsetDateTime.parse("2026-11-20T12:00:00Z")
        )
    )
}
