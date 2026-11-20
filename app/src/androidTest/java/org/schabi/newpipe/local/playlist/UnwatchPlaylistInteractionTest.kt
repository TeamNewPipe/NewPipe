package org.schabi.newpipe.local.playlist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.quickadd.QuickAddStore
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase

/** History removal and playlist Quick Add must not race or invalidate the stream metadata. */
class UnwatchPlaylistInteractionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = TestDatabase.createReplacingNewPipeDatabase()

    @Test
    fun quickAddStillWorksAfterMarkingTheSameStreamUnwatched() {
        val profileId = ProfileManager.getActiveProfileId(context)
        val url = "https://example.com/watch?v=unwatch-quick-add"
        val stream = StreamInfoItem(0, url, "Test", StreamType.VIDEO_STREAM).apply {
            duration = 180
            uploaderName = "Uploader"
        }
        val playlist = PlaylistEntity(
            name = "Test",
            isThumbnailPermanent = false,
            thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
            displayIndex = -1
        ).apply {
            this.profileId = profileId
        }
        val playlistId = database.playlistDAO().insert(playlist)

        HistoryRecordManager(context, profileId).markAsUnwatched(stream).blockingAwait()
        val addition = QuickAddStore(database, profileId).add(playlistId, StreamEntity(stream))
        assertNotNull(addition)
        assertNotNull(database.streamDAO().getStreamDirect(0, url))
    }

    @After
    fun close() {
        NewPipeDatabase.close()
    }
}
