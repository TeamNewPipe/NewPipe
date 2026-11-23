/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(AndroidJUnit4::class)
class DeletedProfileSyncIsolationTest {
    private lateinit var phoneDatabase: AppDatabase
    private lateinit var tabletDatabase: AppDatabase

    @Before
    fun setUp() {
        phoneDatabase = newDatabase()
        tabletDatabase = newDatabase()
    }

    @After
    fun tearDown() {
        phoneDatabase.close()
        tabletDatabase.close()
    }

    @Test
    fun staleSubscriptionCannotRecreateDeletedProfileData() {
        phoneDatabase.subscriptionDAO().insert(
            SubscriptionEntity(
                serviceId = SERVICE_ID,
                url = CHANNEL_URL,
                name = "Deleted profile channel",
                profileId = DELETED_PROFILE_ID
            )
        )
        val phoneStore = RoomSubscriptionSyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomSubscriptionSyncStore(
            tabletDatabase,
            newPeerId(),
            canMaterializeProfile = { false }
        )
        val phone = SubscriptionSyncEngine(phoneStore)
        val tablet = SubscriptionSyncEngine(tabletStore)

        val request = phone.createRequest(tabletStore.localPeerId)
        val response = tablet.handleRequest(phoneStore.localPeerId, request)

        assertTrue(response.accepted)
        assertTrue(
            tabletDatabase.subscriptionDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
    }

    @Test
    fun stalePlaylistBookmarkCannotRecreateDeletedProfileData() {
        phoneDatabase.playlistRemoteDAO().upsertForProfile(
            DELETED_PROFILE_ID,
            PlaylistRemoteEntity(
                serviceId = SERVICE_ID,
                orderingName = "Deleted profile playlist",
                url = PLAYLIST_URL,
                thumbnailUrl = null,
                uploader = "Uploader",
                displayIndex = 0,
                streamCount = 1,
                profileId = DELETED_PROFILE_ID
            )
        )
        val phoneStore = RoomPlaylistSyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomPlaylistSyncStore(
            tabletDatabase,
            newPeerId(),
            canMaterializeProfile = { false }
        )
        val phone = PlaylistSyncEngine(phoneStore)
        val tablet = PlaylistSyncEngine(tabletStore)

        val request = phone.createRequest(tabletStore.localPeerId)
        val response = tablet.handleRequest(phoneStore.localPeerId, request)

        assertTrue(response.accepted)
        assertTrue(
            tabletDatabase.playlistRemoteDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
    }

    @Test
    fun staleWatchHistoryAndProgressCannotRecreateDeletedProfileData() {
        val streamId = phoneDatabase.streamDAO().upsert(
            StreamEntity(
                serviceId = SERVICE_ID,
                url = VIDEO_URL,
                title = "Deleted profile video",
                streamType = StreamType.VIDEO_STREAM,
                duration = 120,
                uploader = "Uploader"
            )
        )
        phoneDatabase.streamHistoryDAO().insert(
            StreamHistoryEntity(
                streamUid = streamId,
                accessDate = OffsetDateTime.parse("2026-09-21T10:00:00Z"),
                repeatCount = 2,
                profileId = DELETED_PROFILE_ID
            )
        )
        phoneDatabase.streamStateDAO().upsert(
            StreamStateEntity(
                streamUid = streamId,
                progressMillis = 42_000,
                profileId = DELETED_PROFILE_ID
            )
        )
        val phoneStore = RoomHistorySyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomHistorySyncStore(
            tabletDatabase,
            newPeerId(),
            canMaterializeProfile = { false }
        )
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)

        val request = phone.createRequest(
            tabletStore.localPeerId,
            HistorySyncCategory.WATCH
        )
        val response = tablet.handleRequest(phoneStore.localPeerId, request)

        assertTrue(response.accepted)
        assertTrue(
            tabletDatabase.streamHistoryDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
        assertTrue(
            tabletDatabase.streamStateDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
    }

    @Test
    fun suppressedProfileRowsAreNotSeededIntoTheOutgoingHistoryJournal() {
        val streamId = seedWatchHistory(phoneDatabase, DELETED_PROFILE_ID)
        seedWatchHistory(phoneDatabase, ProfileManager.DEFAULT_PROFILE_ID)
        val phonePeer = newPeerId()
        val tabletPeer = newPeerId()

        repeat(3) {
            val phoneStore = RoomHistorySyncStore(
                phoneDatabase,
                phonePeer,
                canMaterializeProfile = { it != DELETED_PROFILE_ID }
            )
            val tabletStore = RoomHistorySyncStore(tabletDatabase, tabletPeer)
            synchronizeWatch(phoneStore, tabletStore)

            assertTrue(
                phoneDatabase.historySyncDAO()
                    .getRecordsForProfile(HistorySyncCategory.WATCH.name, DELETED_PROFILE_ID)
                    .isEmpty()
            )
            assertTrue(tabletDatabase.streamHistoryDAO().getAllDirectForProfile(DELETED_PROFILE_ID).isEmpty())
            assertTrue(tabletDatabase.streamStateDAO().getAllDirectForProfile(DELETED_PROFILE_ID).isEmpty())
            assertEquals(
                2L,
                tabletDatabase.streamHistoryDAO()
                    .getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID).single().repeatCount
            )
            assertEquals(2L, phoneStore.getKnownRevisions(HistorySyncCategory.WATCH)[phonePeer])
        }
        // Suppressing export is not a request to erase these leftover local rows.
        assertEquals(
            2L,
            phoneDatabase.streamHistoryDAO()
                .getLatestEntryForProfile(DELETED_PROFILE_ID, streamId)?.repeatCount
        )
        assertEquals(
            42_000L,
            phoneDatabase.streamStateDAO()
                .getStateDirectForProfile(DELETED_PROFILE_ID, streamId)?.progressMillis
        )
    }

    @Test
    fun pendingPeerDeletionWinsOverUnjournaledLegacyRowsAfterRestart() {
        val streamId = seedWatchHistory(phoneDatabase, DELETED_PROFILE_ID)
        val phonePeer = newPeerId()
        val tabletPeer = newPeerId()
        synchronizeWatch(
            RoomHistorySyncStore(phoneDatabase, phonePeer),
            RoomHistorySyncStore(tabletDatabase, tabletPeer)
        )

        // The old non-Default manager changed these rows without writing journal events.
        phoneDatabase.streamHistoryDAO().deleteStreamHistoryForProfile(DELETED_PROFILE_ID, streamId)
        phoneDatabase.streamHistoryDAO().insert(
            StreamHistoryEntity(streamId, WATCHED_AT.plusSeconds(1), 5, DELETED_PROFILE_ID)
        )
        phoneDatabase.streamStateDAO().upsert(StreamStateEntity(streamId, 12_000, DELETED_PROFILE_ID))

        val tabletStreamId = requireNotNull(tabletDatabase.streamDAO().getStreamDirect(SERVICE_ID, VIDEO_URL)).uid
        val tabletRecorder = RoomHistorySyncStore(tabletDatabase, tabletPeer)
        tabletDatabase.runInTransaction {
            tabletRecorder.recordWatchStreamDelete(DELETED_PROFILE_ID, tabletStreamId)
            tabletDatabase.streamHistoryDAO().deleteStreamHistoryForProfile(DELETED_PROFILE_ID, tabletStreamId)
            tabletDatabase.streamStateDAO().deleteStateForProfile(DELETED_PROFILE_ID, tabletStreamId)
        }

        // A different profile advances the phone's clock before it receives the deletion.
        // Giving the old rows fresh repair stamps would now resurrect the deleted video.
        val unrelatedUrl = "$VIDEO_URL-unrelated"
        val unrelatedId = phoneDatabase.streamDAO().upsert(historyStream(unrelatedUrl))
        val phoneRecorder = RoomHistorySyncStore(phoneDatabase, phonePeer)
        phoneDatabase.runInTransaction {
            phoneRecorder.recordWatchEvent(
                ProfileManager.DEFAULT_PROFILE_ID,
                unrelatedId,
                WATCHED_AT.plusSeconds(2).toInstant().toEpochMilli(),
                1
            )
            phoneDatabase.streamHistoryDAO().insert(
                StreamHistoryEntity(unrelatedId, WATCHED_AT.plusSeconds(2), 1, ProfileManager.DEFAULT_PROFILE_ID)
            )
        }
        val phoneRevision = requireNotNull(phoneRecorder.getKnownRevisions(HistorySyncCategory.WATCH)[phonePeer])
        val tabletRevision = requireNotNull(tabletRecorder.getKnownRevisions(HistorySyncCategory.WATCH)[tabletPeer])

        repeat(3) {
            // New stores exercise every initialization/reconciliation pass after a restart.
            val phoneStore = RoomHistorySyncStore(phoneDatabase, phonePeer)
            val tabletStore = RoomHistorySyncStore(tabletDatabase, tabletPeer)
            synchronizeWatch(phoneStore, tabletStore)
            for (database in listOf(phoneDatabase, tabletDatabase)) {
                val id = requireNotNull(database.streamDAO().getStreamDirect(SERVICE_ID, VIDEO_URL)).uid
                assertNull(database.streamHistoryDAO().getLatestEntryForProfile(DELETED_PROFILE_ID, id))
                assertNull(database.streamStateDAO().getStateDirectForProfile(DELETED_PROFILE_ID, id))
                assertEquals(
                    1L,
                    database.streamHistoryDAO()
                        .getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID).single().repeatCount
                )
                assertEquals(
                    phoneRevision,
                    database.historySyncDAO().countChangesAfter(HistorySyncCategory.WATCH.name, phonePeer, 0)
                )
                assertEquals(
                    tabletRevision,
                    database.historySyncDAO().countChangesAfter(HistorySyncCategory.WATCH.name, tabletPeer, 0)
                )
            }
            assertEquals(phoneRevision, phoneStore.getKnownRevisions(HistorySyncCategory.WATCH)[phonePeer])
            assertEquals(tabletRevision, tabletStore.getKnownRevisions(HistorySyncCategory.WATCH)[tabletPeer])
        }

        // A real view recorded after the deletion must still synchronize normally.
        val viewedAt = WATCHED_AT.plusSeconds(3)
        phoneDatabase.runInTransaction {
            phoneRecorder.recordWatchEvent(DELETED_PROFILE_ID, streamId, viewedAt.toInstant().toEpochMilli(), 1)
            phoneRecorder.recordProgress(DELETED_PROFILE_ID, streamId, 15_000, viewedAt.toInstant().toEpochMilli())
            phoneDatabase.streamHistoryDAO().insert(StreamHistoryEntity(streamId, viewedAt, 1, DELETED_PROFILE_ID))
            phoneDatabase.streamStateDAO().upsert(StreamStateEntity(streamId, 15_000, DELETED_PROFILE_ID))
        }
        repeat(2) {
            synchronizeWatch(
                RoomHistorySyncStore(phoneDatabase, phonePeer),
                RoomHistorySyncStore(tabletDatabase, tabletPeer)
            )
        }
        for (database in listOf(phoneDatabase, tabletDatabase)) {
            val id = requireNotNull(database.streamDAO().getStreamDirect(SERVICE_ID, VIDEO_URL)).uid
            assertEquals(1L, database.streamHistoryDAO().getLatestEntryForProfile(DELETED_PROFILE_ID, id)?.repeatCount)
            assertEquals(15_000L, database.streamStateDAO().getStateDirectForProfile(DELETED_PROFILE_ID, id)?.progressMillis)
        }
    }

    private fun seedWatchHistory(database: AppDatabase, profileId: String): Long {
        val streamId = database.streamDAO().upsert(historyStream(VIDEO_URL))
        database.streamHistoryDAO().insert(StreamHistoryEntity(streamId, WATCHED_AT, 2, profileId))
        database.streamStateDAO().upsert(StreamStateEntity(streamId, 42_000, profileId))
        return streamId
    }

    private fun historyStream(url: String) = StreamEntity(
        serviceId = SERVICE_ID,
        url = url,
        title = "History video",
        streamType = StreamType.VIDEO_STREAM,
        duration = 120,
        uploader = "Uploader"
    )

    private fun synchronizeWatch(phoneStore: RoomHistorySyncStore, tabletStore: RoomHistorySyncStore) {
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)
        repeat(20) {
            val request = phone.createRequest(tabletStore.localPeerId, HistorySyncCategory.WATCH)
            val response = tablet.handleRequest(phoneStore.localPeerId, request)
            assertTrue(response.error, response.accepted)
            phone.handleResponse(tabletStore.localPeerId, HistorySyncCategory.WATCH, response)
            if (!request.hasMore && !response.hasMore) return
        }
        throw AssertionError("History synchronization did not converge")
    }

    private fun newDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    companion object {
        private const val SERVICE_ID = 0
        private const val DELETED_PROFILE_ID = "11111111-1111-1111-1111-111111111111"
        private const val CHANNEL_URL = "https://example.com/channel/deleted"
        private const val PLAYLIST_URL = "https://example.com/playlist/deleted"
        private const val VIDEO_URL = "https://example.com/video/deleted"
        private val WATCHED_AT = OffsetDateTime.parse("2026-09-21T10:00:00Z")
    }
}
