package org.schabi.newpipe.local.playlist

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

@RunWith(AndroidJUnit4::class)
class LikedVideosStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("liked-videos-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val owner = PlaylistEntity.DEFAULT_PROFILE_ID
    private val other = "00000000-0000-0000-0000-000000000001"
    private val video = StreamEntity(serviceId = 0, url = "https://example.com/video", title = "Video", streamType = StreamType.VIDEO_STREAM, duration = 60, uploader = "Channel")

    private fun store(profile: String = owner) = LikedVideosStore(database, preferences, profile, "Liked videos")

    @After
    fun close() {
        database.close()
        preferences.edit().clear().commit()
    }

    @Test
    fun concurrentLikesCreateOnePlaylistAndOneOccurrence() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((1..12).map { Callable { store().setLiked(video, true) } })
            assertTrue(results.all { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1, database.playlistDAO().getAllDirectForProfile(owner).size)
            assertEquals(1, database.playlistStreamDAO().getQuickAddEntries(store().getOrCreatePlaylist()).size)
            assertTrue(store().observeLiked(video.serviceId, video.url).blockingFirst())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun unlikeRemovesAllOccurrencesAndRetainsOtherVideosAndThumbnail() {
        val liked = store()
        assertFalse(liked.setLiked(video, false))
        assertTrue(database.playlistDAO().getAllDirect().isEmpty())
        liked.setLiked(video, true)
        val next = video.copy(url = "https://example.com/next")
        liked.setLiked(next, true)
        val id = liked.getOrCreatePlaylist()
        val entries = database.playlistStreamDAO().getQuickAddEntries(id)
        database.playlistStreamDAO().insert(PlaylistStreamEntity(id, entries.first().streamUid, 2))
        assertFalse(liked.setLiked(video, false))
        assertFalse(liked.observeLiked(video.serviceId, video.url).blockingFirst())
        assertEquals(listOf(next.url), database.playlistStreamDAO().getOrderedStreamsDirect(id).map { it.url })
        assertEquals(entries[1].streamUid, database.playlistDAO().getPlaylistDirect(id)!!.thumbnailStreamId)
        assertFalse(liked.setLiked(next, false))
        assertEquals(PlaylistEntity.DEFAULT_THUMBNAIL_ID, database.playlistDAO().getPlaylistDirect(id)!!.thumbnailStreamId)
    }

    @Test
    fun profileOwnershipIsCheckedEvenWhenSavedPointerIsWrong() {
        store(other).setLiked(video, true)
        val foreignId = store(other).getOrCreatePlaylist()
        preferences.edit().putLong(LikedVideosStore.preferenceKey(owner), foreignId).commit()
        assertFalse(store().observeLiked(video.serviceId, video.url).blockingFirst())
        store().setLiked(video, false)
        assertTrue(store(other).observeLiked(video.serviceId, video.url).blockingFirst())
        store().setLiked(video, true)
        assertNotEquals(foreignId, store().getOrCreatePlaylist())
        store().setLiked(video, false)
        assertTrue(store(other).observeLiked(video.serviceId, video.url).blockingFirst())
    }

    @Test
    fun renameKeepsIdentityAndDeletedPlaylistIsRecreatedWithoutAdoptingNamesakes() {
        val ordinary = database.playlistDAO().insert(PlaylistEntity(name = "Liked videos", isThumbnailPermanent = false, thumbnailStreamId = -1, displayIndex = 0))
        store().setLiked(video, true)
        val id = store().getOrCreatePlaylist()
        assertNotEquals(ordinary, id)
        val playlist = database.playlistDAO().getPlaylistDirect(id)!!
        playlist.name = "My favorites"
        database.playlistDAO().update(playlist)
        assertEquals(id, store().getOrCreatePlaylist())
        assertTrue(store().observeLiked(video.serviceId, video.url).blockingFirst())
        database.playlistDAO().deletePlaylist(id)
        assertFalse(store().observeLiked(video.serviceId, video.url).blockingFirst())
        store().setLiked(video, true)
        assertNotEquals(id, store().getOrCreatePlaylist())
        assertTrue(database.playlistStreamDAO().getQuickAddEntries(ordinary).isEmpty())
    }

    @Test
    fun sameUrlOnDifferentServicesIsIndependentAndLibraryRemovalUpdatesState() {
        store().setLiked(video, true)
        store().setLiked(video.copy(serviceId = 1), true)
        store().setLiked(video, false)
        assertTrue(store().observeLiked(1, video.url).blockingFirst())
        assertFalse(store().observeLiked(0, video.url).blockingFirst())
        database.playlistStreamDAO().deleteBatch(store().getOrCreatePlaylist())
        assertFalse(store().observeLiked(1, video.url).blockingFirst())
    }

    @Test
    fun fullBackupRestoresPlaylistContentsAndItsProfileLink() {
        val sourceName = "liked-videos-backup-source.db"
        val restoredName = "liked-videos-backup-restored.db"
        context.deleteDatabase(sourceName)
        context.deleteDatabase(restoredName)
        val zip = File.createTempFile("liked-videos", ".zip", context.cacheDir)
        val backup = StoredFileHelper(context, null, Uri.fromFile(zip), "test")
        val manager = ImportExportManager(BackupFileLocator(context))
        var source: AppDatabase? = Room.databaseBuilder(context, AppDatabase::class.java, sourceName).build()
        var restored: AppDatabase? = null
        try {
            val first = LikedVideosStore(requireNotNull(source), preferences, owner, "Liked videos")
            first.setLiked(video, true)
            val originalId = first.getOrCreatePlaylist()
            requireNotNull(source).close()
            source = null
            manager.exportDatabase(preferences, backup, context.getDatabasePath(sourceName).toPath())
            preferences.edit().clear().commit()
            manager.loadJsonPrefs(backup, preferences)
            assertTrue(ZipHelper.extractFileFromZip(backup, BackupFileLocator.FILE_NAME_DB, context.getDatabasePath(restoredName).toPath()))
            restored = Room.databaseBuilder(context, AppDatabase::class.java, restoredName).build()
            val imported = LikedVideosStore(requireNotNull(restored), preferences, owner, "Liked videos")
            assertEquals(originalId, imported.getOrCreatePlaylist())
            assertTrue(imported.observeLiked(video.serviceId, video.url).blockingFirst())
            imported.setLiked(video, false)
            assertEquals(originalId, imported.getOrCreatePlaylist())
            assertFalse(imported.observeLiked(video.serviceId, video.url).blockingFirst())
        } finally {
            source?.close()
            restored?.close()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(restoredName)
            zip.delete()
        }
    }
}
