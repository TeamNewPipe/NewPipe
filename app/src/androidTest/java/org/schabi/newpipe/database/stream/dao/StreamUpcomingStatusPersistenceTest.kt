/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.database.stream.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.StreamListFilter

@RunWith(AndroidJUnit4::class)
class StreamUpcomingStatusPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var streams: StreamDAO
    private val futureDate = OffsetDateTime.parse("2100-01-01T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        streams = database.streamDAO()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sparseRefreshPreservesProviderStatusAndExplicitAvailabilityClearsItDespiteStaleDate() {
        val id = streams.upsert(stream("scheduled", true).copy(uploadDate = futureDate))
        assertEquals(id, streams.upsert(stream("scheduled").copy(title = "Refreshed title")))
        assertEquals(true, stored(id).upcomingStatus)
        assertEquals("Refreshed title", stored(id).title)
        assertEquals(StreamListFilter.UPCOMING, StreamListFilter.categoryOf(stored(id).toStreamInfoItem()))

        assertEquals(id, streams.upsert(stream("scheduled", false)))
        assertEquals(false, stored(id).upcomingStatus)
        assertEquals(futureDate.toInstant(), stored(id).uploadDate!!.toInstant())
        assertEquals(StreamListFilter.VIDEOS, StreamListFilter.categoryOf(stored(id).toStreamInfoItem()))
        streams.upsert(stream("scheduled"))
        assertEquals(false, stored(id).upcomingStatus)
        assertEquals(1, streams.getAll().blockingFirst().size)
    }

    @Test
    fun batchRefreshPreservesUnknownAndTransitionsScheduledStreamsToLiveOrCompleted() {
        val ids = streams.upsertAll(
            listOf(stream("live", true), stream("post-live", true), stream("post-audio", true), stream("sparse", true), stream("unknown"))
        )
        val refreshed = streams.upsertAll(
            listOf(
                stream("live", false, StreamType.LIVE_STREAM),
                stream("post-live", type = StreamType.POST_LIVE_STREAM),
                stream("post-audio", type = StreamType.POST_LIVE_AUDIO_STREAM),
                stream("sparse"),
                stream("unknown"),
                stream("new-upcoming", true)
            )
        )
        assertEquals(ids, refreshed.take(ids.size))
        assertEquals(listOf(false, false, false, true, null, true), refreshed.map { stored(it).upcomingStatus })
        assertEquals(StreamListFilter.LIVE, StreamListFilter.categoryOf(stored(ids.first()).toStreamInfoItem()))
        assertNull(stored(ids.last()).upcomingStatus)
    }

    @Test
    fun legacyLiveTypeWithFutureDateRetainsFallbackAcrossRepeatedUpdates() {
        val item = stream("legacy", type = StreamType.LIVE_STREAM).copy(uploadDate = futureDate)
        val id = streams.upsert(item.copy())
        repeat(2) {
            assertEquals(id, streams.upsert(item.copy()))
            assertNull(stored(id).upcomingStatus)
            assertEquals(StreamListFilter.UPCOMING, StreamListFilter.categoryOf(stored(id).toStreamInfoItem()))
        }
    }

    private fun stored(id: Long): StreamEntity = requireNotNull(streams.getStreamDirect(id))

    private fun stream(id: String, status: Boolean? = null, type: StreamType = StreamType.VIDEO_STREAM) = StreamEntity(
        serviceId = 0,
        url = "https://example.com/$id",
        title = "Title for $id",
        streamType = type,
        duration = -1,
        uploader = "Channel",
        upcomingStatus = status
    )
}
