package org.schabi.newpipe.database.stream.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class StreamContentCategoryPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var streams: StreamDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        streams = database.streamDAO()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun singleRefreshWithoutCategoryPreservesVerifiedCategoryAndUpdatesMetadata() {
        val originalId = streams.upsert(stream("track", "TRACKS"))
        val refreshedId = streams.upsert(stream("track").copy(title = "Updated title"))

        assertEquals(originalId, refreshedId)
        val stored = requireNotNull(streams.getStreamDirect(originalId))
        assertEquals("TRACKS", stored.contentCategory)
        assertEquals("Updated title", stored.title)
        assertEquals(1, streams.getAll().blockingFirst().size)
    }

    @Test
    fun singleUpsertKeepsUnknownUntilVerifiedMetadataArrivesAndAcceptsCategoryUpdates() {
        val originalId = streams.upsert(stream("unknown"))
        assertNull(requireNotNull(streams.getStreamDirect(originalId)).contentCategory)

        assertEquals(originalId, streams.upsert(stream("unknown")))
        assertNull(requireNotNull(streams.getStreamDirect(originalId)).contentCategory)

        assertEquals(originalId, streams.upsert(stream("unknown", "PODCASTS")))
        assertEquals("PODCASTS", requireNotNull(streams.getStreamDirect(originalId)).contentCategory)

        assertEquals(originalId, streams.upsert(stream("unknown", "COURSES")))
        assertEquals("COURSES", requireNotNull(streams.getStreamDirect(originalId)).contentCategory)
        assertEquals(1, streams.getAll().blockingFirst().size)
    }

    @Test
    fun batchUpsertPreservesKnownCategoriesAndDoesNotInventUnknownCategories() {
        val originalIds = streams.upsertAll(
            listOf(stream("track", "TRACKS"), stream("unknown"), stream("course", "COURSES"))
        )
        val refreshedIds = streams.upsertAll(
            listOf(
                stream("track").copy(title = "Refreshed track"),
                stream("unknown", "COURSES"),
                stream("course", "PODCASTS"),
                stream("new-unknown"),
                stream("new-track", "TRACKS")
            )
        )

        assertEquals(originalIds, refreshedIds.take(3))
        val stored = refreshedIds.map { requireNotNull(streams.getStreamDirect(it)) }
        assertEquals(listOf("TRACKS", "COURSES", "PODCASTS", null, "TRACKS"), stored.map { it.contentCategory })
        assertEquals("Refreshed track", stored[0].title)
        assertEquals(5, streams.getAll().blockingFirst().size)

        val noCategoryIds = streams.upsertAll(
            listOf(stream("new-unknown"), stream("new-track"))
        )
        assertEquals(refreshedIds.takeLast(2), noCategoryIds)
        assertNull(requireNotNull(streams.getStreamDirect(noCategoryIds[0])).contentCategory)
        assertEquals("TRACKS", requireNotNull(streams.getStreamDirect(noCategoryIds[1])).contentCategory)
    }

    private fun stream(id: String, category: String? = null) = StreamEntity(
        serviceId = 0,
        url = "https://example.com/$id",
        title = "Title for $id",
        streamType = StreamType.AUDIO_STREAM,
        duration = 120,
        uploader = "Artist",
        contentCategory = category
    )
}
