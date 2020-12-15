package org.schabi.newpipe.local.playlist

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.di.RoomModule
import org.schabi.newpipe.extractor.stream.StreamType
import javax.inject.Inject

@UninstallModules(RoomModule::class)
@HiltAndroidTest
class LocalPlaylistManagerTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var appDatabase: AppDatabase
    @Inject
    lateinit var localPlaylistManager: LocalPlaylistManager

    @Before
    fun setUp() {
        hiltRule.inject()
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
