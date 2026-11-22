/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.ListSeparators

@RunWith(AndroidJUnit4::class)
class PlaylistSelectionStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var store: PlaylistSelectionStore

    @Volatile private var activeProfileId = PROFILE_A

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        activeProfileId = PROFILE_A
        store = PlaylistSelectionStore(database) { activeProfileId }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun removingOneDuplicateKeepsTheOtherCopyAndRefreshesAutomaticThumbnailAfterCommit() {
        val fixture = playlist()
        assertEquals(fixture.ids[0], fixture.ids[2])
        val replacement = fixture.ids.drop(1)
        val callback = AtomicBoolean()

        assertTrue(
            store.replace(PROFILE_A, fixture.id, fixture.ids, replacement) {
                assertFalse("Presentation updates must run after the Room transaction commits", database.inTransaction())
                assertRows(fixture.id, replacement)
                callback.set(true)
            }.blockingGet()
        )

        assertTrue(callback.get())
        assertRows(fixture.id, replacement)
        assertEquals(replacement.first(), metadata(fixture.id).thumbnailStreamId)
        fixture.ids.distinct().forEach { assertNotNull(database.streamDAO().getStreamDirect(it)) }
    }

    @Test
    fun movingSeveralOccurrencesPreservesDuplicatesAndAnExplicitThumbnail() {
        val fixture = playlist()
        val original = metadata(fixture.id)
        original.isThumbnailPermanent = true
        original.thumbnailStreamId = fixture.ids[1]
        database.playlistDAO().updateForProfile(PROFILE_A, original)
        val replacement = listOf(fixture.ids[1], fixture.ids[3], fixture.ids[0], fixture.ids[2])

        assertTrue(store.replace(PROFILE_A, fixture.id, fixture.ids, replacement).blockingGet())

        assertRows(fixture.id, replacement)
        assertEquals(fixture.ids.groupingBy { it }.eachCount(), replacement.groupingBy { it }.eachCount())
        assertTrue(metadata(fixture.id).isThumbnailPermanent)
        assertEquals(original.thumbnailStreamId, metadata(fixture.id).thumbnailStreamId)
    }

    @Test
    fun aStaleFullOrderCannotOverwriteAReorderWithTheSameMembers() {
        val fixture = playlist()
        val callback = AtomicBoolean()
        val planned = store.replace(PROFILE_A, fixture.id, fixture.ids, fixture.ids.drop(1)) { callback.set(true) }
        val newerOrder = fixture.ids.reversed()
        LocalPlaylistManager(database, PROFILE_A).updateJoin(fixture.id, newerOrder).blockingAwait()
        val before = metadata(fixture.id)

        assertFalse(planned.blockingGet())

        assertFalse(callback.get())
        assertRows(fixture.id, newerOrder)
        assertEquals(before, metadata(fixture.id))
    }

    @Test
    fun profileChangesAndForeignOwnershipBothRejectTheCapturedEdit() {
        val fixture = playlist()
        val other = playlist(PROFILE_B)
        val planned = store.replace(PROFILE_A, fixture.id, fixture.ids, emptyList())
        activeProfileId = PROFILE_B

        assertFalse("The profile is checked when the transaction executes", planned.blockingGet())
        assertFalse("A matching active profile cannot edit somebody else's playlist", store.replace(PROFILE_B, fixture.id, fixture.ids, emptyList()).blockingGet())

        assertRows(fixture.id, fixture.ids)
        assertRows(other.id, other.ids)
        assertEquals(PROFILE_A, metadata(fixture.id).profileId)
        assertEquals(PROFILE_B, metadata(other.id).profileId)
    }

    @Test
    fun capturedListsCannotChangeBeforeSubscriptionAndRemovingAllResetsAutomaticThumbnail() {
        val fixture = playlist()
        val expected = fixture.ids.toMutableList()
        val replacement = fixture.ids.dropLast(1).toMutableList()
        val intended = replacement.toList()
        val planned = store.replace(PROFILE_A, fixture.id, expected, replacement)
        expected.clear()
        replacement.clear()

        assertTrue(planned.blockingGet())
        assertRows(fixture.id, intended)
        assertTrue(store.replace(PROFILE_A, fixture.id, intended, emptyList()).blockingGet())

        assertRows(fixture.id, emptyList())
        assertEquals(PlaylistEntity.DEFAULT_THUMBNAIL_ID, metadata(fixture.id).thumbnailStreamId)
        assertEquals("Saved playlist", metadata(fixture.id).name)
        fixture.ids.distinct().forEach { assertNotNull(database.streamDAO().getStreamDirect(it)) }
    }

    @Test
    fun aDeletedPlaylistCannotBeRecreatedByAnOldSelection() {
        val fixture = playlist()
        val planned = store.replace(PROFILE_A, fixture.id, fixture.ids, fixture.ids.dropLast(1))
        database.playlistDAO().deletePlaylistForProfile(PROFILE_A, fixture.id)

        assertFalse(planned.blockingGet())
        assertTrue(database.playlistDAO().getAllDirectForProfile(PROFILE_A).isEmpty())
        assertRows(fixture.id, emptyList())
    }

    @Test
    fun changedHeadingPositionsRejectTheEditWithoutChangingSavedRowsOrHeadings() {
        val fixture = playlist()
        val initial = mapOf(0 to "Introduction", 3 to "Review")
        withSeparators(fixture, initial) { saved, separators ->
            val planned = store.replaceWithHeadings(PROFILE_A, fixture.id, saved, saved.drop(1), separators, initial, mapOf(0 to "Introduction", 2 to "Review"))
            separators.move(saved, 3, 4)
            val external = mapOf(0 to "Introduction", 4 to "Review")
            assertEquals(external, separators.labels(saved))
            val before = metadata(fixture.id)

            assertFalse(planned.blockingGet())

            assertRows(fixture.id, fixture.ids)
            assertEquals(before, metadata(fixture.id))
            assertEquals(external, separators.labels(saved))
        }
    }

    @Test
    fun acceptedRemovalAndMoveCommitTheMatchingShiftedHeadingPositions() {
        val fixture = playlist()
        val initial = mapOf(0 to "Introduction", 3 to "Review")
        withSeparators(fixture, initial) { saved, separators ->
            val remaining = saved.drop(1)
            val removedHeadings = mapOf(0 to "Introduction", 2 to "Review")
            assertTrue(store.replaceWithHeadings(PROFILE_A, fixture.id, saved, remaining, separators, initial, removedHeadings).blockingGet())
            assertRows(fixture.id, remaining.map { it.streamId })
            assertEquals(removedHeadings, separators.labels(remaining))

            val refreshed = LocalPlaylistManager(database, PROFILE_A).getPlaylistStreams(fixture.id).blockingFirst()
            val moved = listOf(refreshed[2], refreshed[0], refreshed[1])
            val movedHeadings = mapOf(1 to "Introduction", 3 to "Review")
            assertTrue(store.replaceWithHeadings(PROFILE_A, fixture.id, refreshed, moved, separators, removedHeadings, movedHeadings).blockingGet())

            assertRows(fixture.id, listOf(fixture.ids[3], fixture.ids[1], fixture.ids[2]))
            assertEquals(movedHeadings, separators.labels(moved))
            assertEquals(fixture.ids[3], metadata(fixture.id).thumbnailStreamId)
        }
    }

    private fun playlist(profileId: String = PROFILE_A): Fixture {
        val streams = listOf("a", "b", "a", "c").map { suffix ->
            StreamEntity(
                serviceId = 0,
                url = "https://example.test/video/$suffix",
                title = "Video $suffix",
                streamType = StreamType.VIDEO_STREAM,
                duration = 60,
                uploader = "Example channel"
            )
        }
        LocalPlaylistManager(database, profileId).createPlaylist("Saved playlist", streams).blockingGet()
        val id = database.playlistDAO().getAllDirectForProfile(profileId).single().uid
        return Fixture(id, database.playlistStreamDAO().getQuickAddEntries(id).map { it.streamUid })
    }

    private fun metadata(playlistId: Long) = checkNotNull(database.playlistDAO().getPlaylistDirect(playlistId))

    private fun withSeparators(fixture: Fixture, headings: Map<Int, String>, action: (List<PlaylistStreamEntry>, ListSeparators) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val listId = "selection-store-test-${UUID.randomUUID()}"
        val key = "list_separators_${PROFILE_A}_$listId"
        val positions = "${key}_positions"
        val original = listOf(key, positions).associateWith { preferences.getString(it, null) }
        try {
            val data = JSONObject().apply { headings.forEach { (row, label) -> put(row.toString(), label) } }
            preferences.edit().putString(positions, data.toString()).commit()
            val saved = LocalPlaylistManager(database, PROFILE_A).getPlaylistStreams(fixture.id).blockingFirst()
            action(saved, ListSeparators(context, PROFILE_A, listId))
        } finally {
            preferences.edit().apply { original.forEach { (savedKey, value) -> putString(savedKey, value) } }.commit()
        }
    }

    private fun assertRows(playlistId: Long, ids: List<Long>) {
        val rows = database.playlistStreamDAO().getQuickAddEntries(playlistId)
        assertEquals(ids, rows.map { it.streamUid })
        assertEquals(ids.indices.toList(), rows.map { it.index })
    }

    private data class Fixture(val id: Long, val ids: List<Long>)

    companion object {
        private const val PROFILE_A = "playlist-selection-a"
        private const val PROFILE_B = "playlist-selection-b"
    }
}
