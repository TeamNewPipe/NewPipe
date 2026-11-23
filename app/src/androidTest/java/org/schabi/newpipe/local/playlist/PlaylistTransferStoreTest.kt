/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.local.playlist

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
class PlaylistTransferStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var database: AppDatabase
    private lateinit var store: PlaylistSelectionStore
    private val preferenceKeys = mutableListOf<String>()
    private var activeProfile = PROFILE_A

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        activeProfile = PROFILE_A
        store = PlaylistSelectionStore(database) { activeProfile }
    }

    @After fun cleanup() {
        database.close()
        preferences.edit().apply { preferenceKeys.forEach(::remove) }.commit()
    }

    @Test fun movesOnlySelectedOccurrencesAndKeepsDestinationDuplicatesAndSourceHeadings() {
        val source = playlist("Source", listOf("a", "b", "a", "c"))
        val destination = playlist("Destination", listOf("a", "d"))
        val destinationBefore = ids(destination)
        val initialHeadings = mapOf(0 to "First", 3 to "Second")
        val shiftedHeadings = mapOf(0 to "First", 2 to "Second")
        val headings = separators(initialHeadings)

        assertTrue(store.moveToPlaylist(PROFILE_A, source.id, source.rows, listOf(3, 0), PlaylistTransferDestination.Existing(destination.id), headings, initialHeadings, shiftedHeadings).blockingGet())

        val remaining = listOf(source.ids[1], source.ids[2])
        assertEquals(remaining, ids(source))
        assertEquals(destinationBefore + listOf(source.ids[3], source.ids[0]), ids(destination))
        assertEquals(shiftedHeadings, headings.labels(rows(source.id)))
        assertEquals(1, ids(source).count { it == source.ids[0] })
        assertEquals(2, ids(destination).count { it == source.ids[0] })
        assertEquals(source.ids[1], metadata(source.id).thumbnailStreamId)
        assertEquals(destinationBefore.first(), metadata(destination.id).thumbnailStreamId)
        assertEquals(listOf(0, 1), database.playlistStreamDAO().getQuickAddEntries(source.id).map { it.index })
    }

    @Test fun createsTheDestinationInTheSameProfileAndPreservesAnExplicitSourceCover() {
        val source = playlist("Source", listOf("a", "b", "a"))
        val original = metadata(source.id).apply { isThumbnailPermanent = true }
        database.playlistDAO().updateForProfile(PROFILE_A, original)

        assertTrue(store.moveToPlaylist(PROFILE_A, source.id, source.rows, listOf(2, 1, 0), PlaylistTransferDestination.Create("  Level two  "), separators(), emptyMap(), emptyMap()).blockingGet())

        val created = database.playlistDAO().getAllDirectForProfile(PROFILE_A).single { it.uid != source.id }
        assertEquals("Level two", created.name)
        assertEquals(listOf(source.ids[2], source.ids[1], source.ids[0]), rows(created.uid).map { it.streamId })
        assertEquals(source.ids[2], created.thumbnailStreamId)
        assertTrue(ids(source).isEmpty())
        assertTrue(metadata(source.id).isThumbnailPermanent)
        assertEquals(original.thumbnailStreamId, metadata(source.id).thumbnailStreamId)
    }

    @Test fun staleSourceProfileChangeDeletedTargetAndForeignTargetLeaveBothPlaylistsUntouched() {
        val source = playlist("Source", listOf("a", "b", "a"))
        val destination = playlist("Destination", listOf("c"))
        val foreign = playlist("Other profile", listOf("d"), PROFILE_B)
        val headings = separators()
        fun planned(target: Long = destination.id) = store.moveToPlaylist(PROFILE_A, source.id, source.rows, listOf(1), PlaylistTransferDestination.Existing(target), headings, emptyMap(), emptyMap())
        val pending = planned()
        activeProfile = PROFILE_B
        assertFalse(pending.blockingGet())
        activeProfile = PROFILE_A
        assertFalse(planned(foreign.id).blockingGet())
        assertFalse(planned(source.id).blockingGet())
        val reordered = listOf(source.ids[1], source.ids[0], source.ids[2])
        LocalPlaylistManager(database, PROFILE_A).updateJoin(source.id, reordered).blockingAwait()
        assertFalse(planned().blockingGet())
        assertEquals(reordered, ids(source))
        assertEquals(destination.ids, ids(destination))
        LocalPlaylistManager(database, PROFILE_A).updateJoin(source.id, source.ids).blockingAwait()
        database.playlistDAO().deletePlaylistForProfile(PROFILE_A, destination.id)
        assertFalse(planned().blockingGet())
        assertEquals(source.ids, ids(source))
        assertEquals(foreign.ids, ids(foreign))
        val create = store.moveToPlaylist(PROFILE_A, source.id, source.rows, listOf(0), PlaylistTransferDestination.Create("Must not exist"), headings, emptyMap(), emptyMap())
        database.playlistDAO().deletePlaylistForProfile(PROFILE_A, source.id)
        assertFalse(create.blockingGet())
        assertTrue(database.playlistDAO().getAllDirectForProfile(PROFILE_A).isEmpty())
    }

    @Test fun movingAllItemsResetsAutomaticSourceCoverAndKeepsPermanentDestinationCover() {
        val source = playlist("Source", listOf("a", "b"))
        val destination = playlist("Destination", listOf("c", "d"))
        val permanent = metadata(destination.id).apply {
            isThumbnailPermanent = true
            thumbnailStreamId = destination.ids.last()
        }
        database.playlistDAO().updateForProfile(PROFILE_A, permanent)
        assertTrue(store.moveToPlaylist(PROFILE_A, source.id, source.rows, listOf(0, 1), PlaylistTransferDestination.Existing(destination.id), separators(), emptyMap(), emptyMap()).blockingGet())
        assertTrue(ids(source).isEmpty())
        assertEquals(PlaylistEntity.DEFAULT_THUMBNAIL_ID, metadata(source.id).thumbnailStreamId)
        assertEquals(destination.ids + source.ids, ids(destination))
        assertTrue(metadata(destination.id).isThumbnailPermanent)
        assertEquals(destination.ids.last(), metadata(destination.id).thumbnailStreamId)
    }

    @Test fun sourceWriteFailureRollsBackDestinationInsertionAndNewPlaylistCreation() {
        val source = playlist("Source", listOf("a", "b"))
        val destination = playlist("Destination", listOf("c"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_source_delete BEFORE DELETE ON playlist_stream_join WHEN OLD.playlist_id = ${source.id} BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val before = database.playlistDAO().getAllDirectForProfile(PROFILE_A).map { it.uid }.toSet()
        val headings = separators(mapOf(0 to "Retained"))
        for (target in listOf(PlaylistTransferDestination.Existing(destination.id), PlaylistTransferDestination.Create("Rolled back"))) {
            assertThrows(RuntimeException::class.java) {
                store.moveToPlaylist(PROFILE_A, source.id, source.rows, listOf(0), target, headings, mapOf(0 to "Retained"), emptyMap()).blockingGet()
            }
            assertEquals(source.ids, ids(source))
            assertEquals(destination.ids, ids(destination))
            assertEquals(before, database.playlistDAO().getAllDirectForProfile(PROFILE_A).map { it.uid }.toSet())
            assertEquals(mapOf(0 to "Retained"), headings.labels(source.rows))
        }
    }

    @Test fun destinationEditsArePreservedAndChangedSourceHeadingsRejectTheMove() {
        val source = playlist("Source", listOf("a", "b"))
        val destination = playlist("Destination", listOf("c", "d"))
        val headings = separators(mapOf(0 to "First"))
        val planned = store.moveToPlaylist(PROFILE_A, source.id, source.rows, listOf(0), PlaylistTransferDestination.Existing(destination.id), headings, mapOf(0 to "First"), mapOf(0 to "First"))
        LocalPlaylistManager(database, PROFILE_A).updateJoin(destination.id, destination.ids.reversed()).blockingAwait()
        assertTrue(planned.blockingGet())
        assertEquals(destination.ids.reversed() + source.ids[0], ids(destination))
        assertEquals(destination.ids.last(), metadata(destination.id).thumbnailStreamId)
        val current = rows(source.id)
        val next = store.moveToPlaylist(PROFILE_A, source.id, current, listOf(0), PlaylistTransferDestination.Existing(destination.id), headings, mapOf(0 to "First"), emptyMap())
        headings.move(current, 0, 1)
        assertFalse(next.blockingGet())
        assertEquals(listOf(source.ids[1]), ids(source))
    }

    private fun playlist(name: String, values: List<String>, profile: String = PROFILE_A): Fixture {
        LocalPlaylistManager(database, profile).createPlaylist(
            name,
            values.map { value ->
                StreamEntity(serviceId = 0, url = "https://example.test/$value", title = value, streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel")
            }
        ).blockingGet()
        val id = database.playlistDAO().getAllDirectForProfile(profile).single { it.name == name }.uid
        return Fixture(id, rows(id, profile))
    }

    private fun separators(labels: Map<Int, String> = emptyMap()): ListSeparators {
        val listId = "transfer-test-${UUID.randomUUID()}"
        val key = "list_separators_${PROFILE_A}_${listId}_positions"
        preferenceKeys.add(key)
        val json = JSONObject().apply { labels.forEach { (row, label) -> put(row.toString(), label) } }
        preferences.edit().putString(key, json.toString()).commit()
        return ListSeparators(context, PROFILE_A, listId)
    }

    private fun rows(id: Long, profile: String = PROFILE_A) = LocalPlaylistManager(database, profile).getPlaylistStreams(id).blockingFirst()
    private fun ids(fixture: Fixture) = database.playlistStreamDAO().getQuickAddEntries(fixture.id).map { it.streamUid }
    private fun metadata(id: Long) = checkNotNull(database.playlistDAO().getPlaylistDirect(id))
    private data class Fixture(val id: Long, val rows: List<PlaylistStreamEntry>) {
        val ids = rows.map { it.streamId }
    }

    companion object {
        private const val PROFILE_A = "playlist-transfer-a"
        private const val PROFILE_B = "playlist-transfer-b"
    }
}
