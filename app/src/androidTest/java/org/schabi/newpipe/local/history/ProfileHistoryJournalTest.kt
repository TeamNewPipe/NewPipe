/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.history

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileDeletionManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.sync.DeviceIdentity
import org.schabi.newpipe.sync.HistorySyncCategory
import org.schabi.newpipe.sync.HistorySyncEngine
import org.schabi.newpipe.sync.RoomHistorySyncStore
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class ProfileHistoryJournalTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val watchKey = context.getString(R.string.enable_watch_history_key)
    private lateinit var phone: AppDatabase
    private lateinit var tablet: AppDatabase
    private lateinit var phoneStore: RoomHistorySyncStore
    private lateinit var tabletStore: RoomHistorySyncStore
    private lateinit var profileId: String
    private lateinit var previousProfile: String
    private var previousWatch: Boolean? = null
    private var previousIncognito: Boolean? = null

    @Before
    fun setUp() {
        previousProfile = ProfileManager.getActiveProfileId(context)
        previousWatch = prefs.all[watchKey] as? Boolean
        previousIncognito = prefs.all[IncognitoMode.KEY] as? Boolean
        prefs.edit().putBoolean(watchKey, true).putBoolean(IncognitoMode.KEY, false).commit()
        assertTrue(ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
        profileId = requireNotNull(
            ProfileManager.createProfile(context, "Journal ${UUID.randomUUID().toString().take(8)}")
        ).id
        assertTrue(ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
        phone = TestDatabase.createReplacingNewPipeDatabase()
        tablet = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        phoneStore = RoomHistorySyncStore.get(context)
        tabletStore = RoomHistorySyncStore(
            tablet,
            DeviceIdentity(generateKeyPair(KeyType.ED25519).first).peerId.toBase58()
        )
    }

    @After
    fun tearDown() {
        ProfileManager.setActiveProfile(context, previousProfile)
        if (::profileId.isInitialized) ProfileManager.deleteProfile(context, profileId)
        restoreBoolean(watchKey, previousWatch)
        restoreBoolean(IncognitoMode.KEY, previousIncognito)
        if (::phone.isInitialized) NewPipeDatabase.close()
        if (::tablet.isInitialized) tablet.close()
    }

    @Test
    fun laterProfileViewsRewindsAndDeletesReachTheOtherPeer() {
        val manager = HistoryRecordManager(context, profileId)
        val default = HistoryRecordManager(context, ProfileManager.DEFAULT_PROFILE_ID)
        val first = stream("first")
        val second = stream("second")
        default.onViewed(first).blockingGet()
        default.saveStreamState(first, 60_000).blockingAwait()
        manager.onViewed(first).blockingGet()
        manager.saveStreamState(first, 90_000).blockingAwait()
        synchronize()
        assertHistory(tablet, profileId, first, 1, 90_000)

        // The initial snapshot has already been sent. These writes must enter the journal
        // themselves; keeping the same stores also avoids relying on legacy recovery.
        manager.onViewed(first).blockingGet()
        manager.saveStreamState(first, 12_000).blockingAwait()
        manager.onViewed(second).blockingGet()
        manager.saveStreamState(second, 30_000).blockingAwait()
        repeat(3) { synchronize() }
        assertHistory(tablet, profileId, first, 2, 12_000)
        assertHistory(tablet, profileId, second, 1, 30_000)
        assertHistory(tablet, ProfileManager.DEFAULT_PROFILE_ID, first, 1, 60_000)

        val firstId = requireNotNull(phone.streamDAO().getStreamDirect(0, first.url)).uid
        manager.deleteStreamHistoryAndState(firstId).blockingAwait()
        repeat(2) { synchronize() }
        assertAbsent(tablet, profileId, first)
        assertHistory(tablet, profileId, second, 1, 30_000)

        manager.deleteWholeStreamHistory().blockingGet()
        manager.deleteCompleteStreamStateHistory().blockingGet()
        repeat(2) { synchronize() }
        assertTrue(tablet.streamHistoryDAO().getAllDirectForProfile(profileId).isEmpty())
        assertTrue(tablet.streamStateDAO().getAllDirectForProfile(profileId).isEmpty())
        assertHistory(tablet, ProfileManager.DEFAULT_PROFILE_ID, first, 1, 60_000)
    }

    @Test
    fun queuedDefaultWritesKeepTheirProfileWhenTheActiveProfileChanges() {
        val manager = HistoryRecordManager(context)
        val info = stream("queued")
        val viewed = manager.onViewed(info)
        val progress = manager.saveStreamState(info, 45_000)
        assertTrue(ProfileManager.setActiveProfile(context, profileId))
        viewed.blockingGet()
        progress.blockingAwait()
        synchronize()

        for (database in listOf(phone, tablet)) {
            assertHistory(database, ProfileManager.DEFAULT_PROFILE_ID, info, 1, 45_000)
            assertAbsent(database, profileId, info)
        }

        // Deletion is bound to the same manager even though another profile is active.
        manager.deleteWholeStreamHistory().blockingGet()
        manager.deleteCompleteStreamStateHistory().blockingGet()
        repeat(2) { synchronize() }
        for (database in listOf(phone, tablet)) {
            assertAbsent(database, ProfileManager.DEFAULT_PROFILE_ID, info)
            assertAbsent(database, profileId, info)
        }
    }

    @Test
    fun pendingWritesAndDeletesDoNotRecreateRemovedProfileOrDeleteItsPeerHistory() {
        val searchKey = context.getString(R.string.enable_search_history_key)
        val previousSearch = prefs.all[searchKey] as? Boolean
        prefs.edit().putBoolean(searchKey, true).commit()
        try {
            val manager = HistoryRecordManager(context, profileId)
            val info = stream("removed-profile")
            val item = StreamInfoItem(0, info.url, "Removed profile", StreamType.VIDEO_STREAM).apply {
                duration = 600
                uploaderName = "Journal channel"
            }
            manager.onViewed(info).blockingGet()
            manager.saveStreamState(info, 45_000).blockingAwait()
            manager.onSearched(0, "kept search").blockingGet()
            synchronize()
            synchronize(HistorySyncCategory.SEARCH)
            val streamId = requireNotNull(phone.streamDAO().getStreamDirect(0, info.url)).uid
            val watchRevisions = phoneStore.getKnownRevisions(HistorySyncCategory.WATCH)
            val searchRevisions = phoneStore.getKnownRevisions(HistorySyncCategory.SEARCH)
            val pending = listOf(
                manager.onViewed(info).ignoreElement(),
                manager.saveStreamState(info, 90_000),
                manager.onSearched(0, "late search").ignoreElement(),
                manager.markAsWatched(item).ignoreElement(),
                manager.markAsUnwatched(item),
                manager.deleteStreamHistoryAndState(streamId),
                manager.deleteWholeStreamHistory().ignoreElement(),
                manager.deleteCompleteStreamStateHistory().ignoreElement(),
                manager.deleteSearchHistory("kept search").ignoreElement(),
                manager.deleteCompleteSearchHistory().ignoreElement()
            )

            ProfileDeletionManager.deleteProfile(context, profileId).blockingAwait()
            pending.forEach { it.blockingAwait() }

            assertNull(ProfileManager.getProfile(context, profileId))
            assertTrue(phone.streamHistoryDAO().getAllDirectForProfile(profileId).isEmpty())
            assertTrue(phone.streamStateDAO().getAllDirectForProfile(profileId).isEmpty())
            assertTrue(phone.searchHistoryDAO().getAllDirectForProfile(profileId).isEmpty())
            assertEquals(watchRevisions, phoneStore.getKnownRevisions(HistorySyncCategory.WATCH))
            assertEquals(searchRevisions, phoneStore.getKnownRevisions(HistorySyncCategory.SEARCH))
            synchronize()
            synchronize(HistorySyncCategory.SEARCH)
            assertHistory(tablet, profileId, info, 1, 45_000)
            assertEquals(listOf("kept search"), tablet.searchHistoryDAO().getAllDirectForProfile(profileId).map { it.search })
        } finally {
            restoreBoolean(searchKey, previousSearch)
        }
    }

    private fun synchronize(category: HistorySyncCategory = HistorySyncCategory.WATCH) {
        val source = HistorySyncEngine(phoneStore)
        val target = HistorySyncEngine(tabletStore)
        repeat(100) {
            val request = source.createRequest(tabletStore.localPeerId, category)
            val response = target.handleRequest(phoneStore.localPeerId, request)
            assertTrue(response.error, response.accepted)
            source.handleResponse(tabletStore.localPeerId, category, response)
            if (!request.hasMore && !response.hasMore) return
        }
        throw AssertionError("History synchronization did not converge")
    }

    private fun assertHistory(
        database: AppDatabase,
        owner: String,
        info: StreamInfo,
        count: Long,
        progress: Long
    ) {
        val id = requireNotNull(database.streamDAO().getStreamDirect(0, info.url)).uid
        assertEquals(count, database.streamHistoryDAO().getLatestEntryForProfile(owner, id)?.repeatCount)
        assertEquals(progress, database.streamStateDAO().getStateDirectForProfile(owner, id)?.progressMillis)
    }

    private fun assertAbsent(database: AppDatabase, owner: String, info: StreamInfo) {
        val id = database.streamDAO().getStreamDirect(0, info.url)?.uid ?: return
        assertNull(database.streamHistoryDAO().getLatestEntryForProfile(owner, id))
        assertNull(database.streamStateDAO().getStateDirectForProfile(owner, id))
    }

    private fun stream(name: String) = StreamInfo(0, "fixture", "https://example.test/video/$name", name).apply {
        streamType = StreamType.VIDEO_STREAM
        duration = 180
        uploaderName = "Journal channel"
    }

    private fun restoreBoolean(key: String, previous: Boolean?) {
        val editor = prefs.edit()
        if (previous == null) editor.remove(key) else editor.putBoolean(key, previous)
        editor.commit()
    }
}
