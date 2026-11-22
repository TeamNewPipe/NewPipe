package org.schabi.newpipe.local.history

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class HistoryContentCategoryPersistenceTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var previousPreferences: Map<String, Boolean?>
    private lateinit var watchHistoryKey: String
    private lateinit var database: AppDatabase
    private lateinit var records: HistoryRecordManager
    private val profileId = "history-content-category-profile"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        watchHistoryKey = context.getString(R.string.enable_watch_history_key)
        previousPreferences = listOf(watchHistoryKey, IncognitoMode.KEY)
            .associateWith { preferences.all[it] as? Boolean }
        preferences.edit().putBoolean(watchHistoryKey, true).putBoolean(IncognitoMode.KEY, false).commit()
        database = TestDatabase.createReplacingNewPipeDatabase()
        records = HistoryRecordManager(context, profileId)
    }

    @After
    fun tearDown() {
        val editor = preferences.edit()
        previousPreferences.forEach { (key, value) ->
            if (value == null) editor.remove(key) else editor.putBoolean(key, value)
        }
        editor.commit()
        database.close()
    }

    @Test
    fun firstPlaybackRetainsAlbumCategoryAndFullStreamMetadata() {
        val info = info("album-track")
        records.onViewed(info, queueItem(info, "https://example.com/album")).blockingGet()

        val stored = requireNotNull(database.streamDAO().getStreamDirect(info.serviceId, info.url))
        assertEquals("TRACKS", stored.contentCategory)
        assertEquals("Full stream title", stored.title)
        assertEquals("Full stream uploader", stored.uploader)
        assertEquals(240L, stored.duration)
        assertEquals(1L, database.streamHistoryDAO().getLatestEntryForProfile(profileId, stored.uid)?.repeatCount)

        // Existing callers without queue metadata must keep the already verified category.
        records.onViewed(info).blockingGet()
        assertEquals("TRACKS", database.streamDAO().getStreamDirect(stored.uid)?.contentCategory)
        assertEquals(2L, database.streamHistoryDAO().getLatestEntryForProfile(profileId, stored.uid)?.repeatCount)
    }

    @Test
    fun canonicalizedPlaybackRetainsAlbumMetadataFromTheOriginalQueueUrl() {
        val canonicalUrl = "https://www.youtube.com/watch?v=abcdefghijk"
        val originalUrl = "https://music.youtube.com/watch?v=abcdefghijk"
        val info = StreamInfo(
            0,
            canonicalUrl,
            originalUrl,
            StreamType.AUDIO_STREAM,
            "abcdefghijk",
            "Canonical track",
            0
        ).apply {
            uploaderName = "Artist"
            duration = 240
            category = "Music"
        }
        val item = queueItem(info, "https://music.youtube.com/playlist?list=album", url = originalUrl)
        records.onViewed(info, item).blockingGet()

        val stored = requireNotNull(database.streamDAO().getStreamDirect(info.serviceId, canonicalUrl))
        assertEquals("TRACKS", stored.contentCategory)
        assertEquals("Canonical track", stored.title)
        assertNull(database.streamDAO().getStreamDirect(info.serviceId, originalUrl))
        assertEquals(1L, database.streamHistoryDAO().getLatestEntryForProfile(profileId, stored.uid)?.repeatCount)
    }

    @Test
    fun blankOriginalUrlCannotMatchAnEmptyQueuedUrl() {
        val info = info("blank-original").apply { originalUrl = "" }
        records.onViewed(info, queueItem(info, "https://example.com/album", url = "")).blockingGet()

        val stored = requireNotNull(database.streamDAO().getStreamDirect(info.serviceId, info.url))
        assertNull(stored.contentCategory)
        assertEquals(1L, database.streamHistoryDAO().getLatestEntryForProfile(profileId, stored.uid)?.repeatCount)
    }

    @Test
    fun absentOrBlankAlbumMetadataLeavesCategoryUnknown() {
        val withoutQueue = info("without-queue")
        records.onViewed(withoutQueue).blockingGet()
        assertNull(database.streamDAO().getStreamDirect(withoutQueue.serviceId, withoutQueue.url)?.contentCategory)

        listOf(null, " ").forEachIndexed { index, albumUrl ->
            val info = info("unknown-$index")
            records.onViewed(info, queueItem(info, albumUrl)).blockingGet()
            assertNull(requireNotNull(database.streamDAO().getStreamDirect(info.serviceId, info.url)).contentCategory)
        }
        assertEquals(3, database.streamHistoryDAO().getAllDirectForProfile(profileId).size)
    }

    @Test
    fun queueMetadataFromAnotherUrlCannotClassifyTheViewedStream() {
        val info = info("viewed-stream")
        val stale = queueItem(info("previous-stream"), "https://example.com/album")
        records.onViewed(info, stale).blockingGet()

        assertNull(requireNotNull(database.streamDAO().getStreamDirect(info.serviceId, info.url)).contentCategory)
        assertNull(database.streamDAO().getStreamDirect(stale.serviceId, stale.url))
        assertEquals(1, database.streamHistoryDAO().getAllDirectForProfile(profileId).size)
    }

    @Test
    fun queueMetadataFromAnotherServiceCannotClassifyTheViewedStream() {
        val info = info("shared-url")
        val stale = queueItem(info, "https://example.com/album", serviceId = 1)
        records.onViewed(info, stale).blockingGet()

        assertNull(requireNotNull(database.streamDAO().getStreamDirect(info.serviceId, info.url)).contentCategory)
        assertNull(database.streamDAO().getStreamDirect(stale.serviceId, stale.url))
        assertEquals(1, database.streamHistoryDAO().getAllDirectForProfile(profileId).size)
    }

    @Test
    fun explicitProviderCategoryTakesPriorityOverAlbumMetadata() {
        val info = info("podcast").apply { category = "Podcast Episode" }
        records.onViewed(info, queueItem(info, "https://example.com/album")).blockingGet()

        assertEquals(
            "PODCASTS",
            requireNotNull(database.streamDAO().getStreamDirect(info.serviceId, info.url)).contentCategory
        )
        assertEquals(1, database.streamHistoryDAO().getAllDirectForProfile(profileId).size)
    }

    @Test
    fun disabledHistoryAndIncognitoPreventAlbumMetadataPersistence() {
        val info = info("private-track")
        val item = queueItem(info, "https://example.com/album")
        preferences.edit().putBoolean(watchHistoryKey, false).commit()
        assertNull(records.onViewed(info, item).blockingGet())
        assertTrue(database.streamDAO().getAll().blockingFirst().isEmpty())

        preferences.edit().putBoolean(watchHistoryKey, true).commit()
        val pending = records.onViewed(info, item)
        preferences.edit().putBoolean(IncognitoMode.KEY, true).commit()
        assertNull(pending.blockingGet())
        assertNull(records.onViewed(info, item).blockingGet())
        assertTrue(database.streamDAO().getAll().blockingFirst().isEmpty())
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile(profileId).isEmpty())
    }

    private fun info(id: String) = StreamInfo(0, id, "https://example.com/$id", "Full stream title").apply {
        streamType = StreamType.AUDIO_STREAM
        duration = 240
        uploaderName = "Full stream uploader"
        category = "Music"
    }

    private fun queueItem(
        info: StreamInfo,
        albumUrl: String?,
        serviceId: Int = info.serviceId,
        url: String = info.url
    ): PlayQueueItem {
        val item = StreamInfoItem(serviceId, url, "List title", info.streamType).apply {
            duration = 120
            uploaderName = "List uploader"
            setAlbumUrl(albumUrl)
        }
        return PlayQueueItem(item)
    }
}
