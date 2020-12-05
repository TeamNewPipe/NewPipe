package org.schabi.newpipe.local.playlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.TestApp
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class LocalPlaylistManagerTest {
    @Inject
    lateinit var appDatabase: AppDatabase
    @Inject
    lateinit var localPlaylistManager: LocalPlaylistManager

    @Before
    fun setUp() {
        TestApp.app.appComponent.inject(this)
    }

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun testCreatePlaylist() {
        localPlaylistManager.createPlaylist("Playlist 1", listOf()).test().await()
            .assertNoValues()

        val streamEntity1 = StreamEntity(0, 0, "", "", StreamType.NONE, 0, "")
        val streamEntity2 = StreamEntity(1, 0, "", "", StreamType.NONE, 0, "")
        localPlaylistManager.createPlaylist("Playlist 2", listOf(streamEntity1, streamEntity2)).test().await()
            .assertValueCount(1)
    }
}
