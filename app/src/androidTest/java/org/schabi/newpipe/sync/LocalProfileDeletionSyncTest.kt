/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.profiles.ProfileDeletionManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class LocalProfileDeletionSyncTest {
    private lateinit var phone: Peer
    private lateinit var tablet: Peer
    private lateinit var removedProfile: String
    private lateinit var retainedProfile: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        phone = Peer(
            DeviceContext(context, "profile-delete-phone-$suffix"),
            TestDatabase.createReplacingNewPipeDatabase()
        )
        tablet = Peer(
            DeviceContext(context, "profile-delete-tablet-$suffix"),
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries().build()
        )
        removedProfile = requireNotNull(
            ProfileManager.createProfile(phone.context, "Remove locally")
        ).id
        retainedProfile = requireNotNull(
            ProfileManager.createProfile(phone.context, "Keep locally")
        ).id
        seedProfile(phone.database, removedProfile)
        seedProfile(phone.database, retainedProfile)
        exchange(phone, tablet)
        exchange(tablet, phone)
        assertEquals(
            profileData(phone.database, removedProfile),
            profileData(tablet.database, removedProfile)
        )
        assertEquals(
            profileData(phone.database, retainedProfile),
            profileData(tablet.database, retainedProfile)
        )
    }

    @After
    fun tearDown() {
        NewPipeDatabase.close()
        tablet.database.close()
        phone.context.clear()
        tablet.context.clear()
    }

    @Test
    fun deletingLocalProfilePreservesPeerDataAcrossRepeatedExchanges() {
        val remoteData = profileData(tablet.database, removedProfile)
        val keptData = profileData(phone.database, retainedProfile)
        val journals = journalSizes(phone.database)

        ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()

        assertEquals(
            "Removing a profile must not create or discard sync revisions",
            journals,
            journalSizes(phone.database)
        )
        repeat(3) {
            exchange(phone, tablet)
            exchange(tablet, phone)
            assertLocallyRemoved()
            assertNotNull(ProfileManager.getProfile(tablet.context, removedProfile))
            assertEquals(remoteData, profileData(tablet.database, removedProfile))
            assertEquals(keptData, profileData(phone.database, retainedProfile))
            assertEquals(keptData, profileData(tablet.database, retainedProfile))
        }
        assertContiguousJournals(phone.database)
        assertContiguousJournals(tablet.database)
    }

    @Test
    fun laterPeerChangesNeitherRecreateLocalProfileNorTurnIntoRemoteDeletes() {
        ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()
        val database = tablet.database
        val stream = requireNotNull(database.streamDAO().getStreamDirect(0, VIDEO_URL))
        val changedAt = DATE.plusSeconds(1).toInstant().toEpochMilli()
        tablet.historyStore.recordProgress(removedProfile, stream.uid, 72_000, changedAt)
        database.streamStateDAO().upsert(StreamStateEntity(stream.uid, 72_000, removedProfile))
        tablet.historyStore.recordSearch(removedProfile, 0, "later search", changedAt)
        database.searchHistoryDAO().insert(
            SearchHistoryEntry(DATE.plusSeconds(1), 0, "later search", profileId = removedProfile)
        )
        val subscription = database.subscriptionDAO().getAllDirectForProfile(removedProfile).single()
        subscription.notificationKeywords = "later peer change"
        database.subscriptionDAO().update(subscription)
        val playlist = database.playlistDAO().getAllDirectForProfile(removedProfile).single()
        playlist.name = "Updated on the other device"
        database.playlistDAO().updateForProfile(removedProfile, playlist)
        val bookmark = database.playlistRemoteDAO().getAllDirectForProfile(removedProfile).single()
        database.playlistRemoteDAO().upsertForProfile(
            removedProfile,
            bookmark.copy(orderingName = "Updated bookmark")
        )
        val group = database.feedGroupDAO().getAllDirectForProfile(removedProfile).single()
        group.name = "Updated group"
        database.feedGroupDAO().update(group)
        val updated = profileData(database, removedProfile)

        repeat(3) {
            exchange(tablet, phone)
            exchange(phone, tablet)
            assertLocallyRemoved()
            assertEquals(updated, profileData(database, removedProfile))
        }
        assertContiguousJournals(phone.database)
        assertContiguousJournals(tablet.database)
    }

    @Test
    fun pendingItemDeletionStillReachesPeerAfterLocalProfileRemoval() {
        val database = phone.database
        val remoteData = profileData(tablet.database, removedProfile).copy(searches = emptyList())
        database.runInTransaction {
            phone.historyStore.recordSearchDelete(removedProfile, QUERY)
            database.searchHistoryDAO().deleteAllWhereQueryForProfile(removedProfile, QUERY)
        }
        val journals = journalSizes(database)

        ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()

        assertEquals(journals, journalSizes(database))
        // A later revision from another profile must follow the still-pending item deletion.
        database.runInTransaction {
            phone.historyStore.recordSearch(
                retainedProfile,
                0,
                "follow-up query",
                DATE.plusSeconds(1).toInstant().toEpochMilli()
            )
            database.searchHistoryDAO().insert(
                SearchHistoryEntry(
                    DATE.plusSeconds(1),
                    0,
                    "follow-up query",
                    profileId = retainedProfile
                )
            )
        }
        val keptData = profileData(database, retainedProfile)
        repeat(3) {
            exchange(phone, tablet)
            exchange(tablet, phone)
            assertLocallyRemoved()
            assertEquals(remoteData, profileData(tablet.database, removedProfile))
            assertEquals(keptData, profileData(tablet.database, retainedProfile))
        }
        assertContiguousJournals(phone.database)
        assertContiguousJournals(tablet.database)
    }

    @Test
    fun individualDeletesInRemainingProfileStillSynchronize() {
        val remoteData = profileData(tablet.database, removedProfile)
        ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()
        val database = phone.database
        val stream = requireNotNull(database.streamDAO().getStreamDirect(0, VIDEO_URL))
        database.runInTransaction {
            phone.historyStore.recordWatchStreamDelete(retainedProfile, stream.uid)
            database.streamHistoryDAO().deleteStreamHistoryForProfile(retainedProfile, stream.uid)
            database.streamStateDAO().deleteStateForProfile(retainedProfile, stream.uid)
            phone.historyStore.recordSearchDelete(retainedProfile, QUERY)
            database.searchHistoryDAO().deleteAllWhereQueryForProfile(retainedProfile, QUERY)
            database.subscriptionDAO().deleteSubscriptionForProfile(retainedProfile, 0, CHANNEL_URL)
            val playlist = database.playlistDAO().getAllDirectForProfile(retainedProfile).single()
            database.playlistDAO().deletePlaylistForProfile(retainedProfile, playlist.uid)
            val bookmark = database.playlistRemoteDAO().getAllDirectForProfile(retainedProfile).single()
            database.playlistRemoteDAO().deletePlaylistForProfile(retainedProfile, bookmark.uid)
            val group = database.feedGroupDAO().getAllDirectForProfile(retainedProfile).single()
            database.feedGroupDAO().deleteForProfile(retainedProfile, group.uid)
        }

        repeat(2) {
            exchange(phone, tablet)
            exchange(tablet, phone)
        }
        assertNotNull(ProfileManager.getProfile(phone.context, retainedProfile))
        assertNotNull(ProfileManager.getProfile(tablet.context, retainedProfile))
        assertTrue(profileData(phone.database, retainedProfile).isEmpty())
        assertTrue(profileData(tablet.database, retainedProfile).isEmpty())
        assertEquals(remoteData, profileData(tablet.database, removedProfile))
        assertLocallyRemoved()
        assertContiguousJournals(phone.database)
        assertContiguousJournals(tablet.database)
    }

    @Test
    fun failedLocalCleanupDoesNotPublishDeletesAndCanBeRetried() {
        val localData = profileData(phone.database, removedProfile)
        val remoteData = profileData(tablet.database, removedProfile)
        val journals = journalSizes(phone.database)
        phone.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_profile_cleanup BEFORE DELETE ON subscriptions " +
                "BEGIN SELECT RAISE(ABORT, 'injected cleanup failure'); END"
        )

        assertThrows(SQLiteConstraintException::class.java) {
            ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()
        }
        assertNotNull(ProfileManager.getProfile(phone.context, removedProfile))
        assertFalse(ProfileManager.isProfileSyncTombstoned(phone.context, removedProfile))
        assertEquals(localData, profileData(phone.database, removedProfile))
        assertEquals(journals, journalSizes(phone.database))
        exchange(phone, tablet)
        assertEquals(remoteData, profileData(tablet.database, removedProfile))

        phone.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_profile_cleanup")
        ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()
        assertThrows(IllegalArgumentException::class.java) {
            ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()
        }
        exchange(phone, tablet)
        exchange(tablet, phone)
        assertLocallyRemoved()
        assertEquals(remoteData, profileData(tablet.database, removedProfile))
    }

    @Test
    fun failedArchiveCleanupRollsBackRoomRowsBeforeSuppressingTheProfile() {
        val localData = profileData(phone.database, removedProfile)
        val remoteData = profileData(tablet.database, removedProfile)
        val journals = journalSizes(phone.database)
        phone.context.failNextFilesAccess = true

        assertThrows(IllegalStateException::class.java) {
            ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()
        }
        assertNotNull(ProfileManager.getProfile(phone.context, removedProfile))
        assertFalse(ProfileManager.isProfileSyncTombstoned(phone.context, removedProfile))
        assertEquals(localData, profileData(phone.database, removedProfile))
        assertEquals(journals, journalSizes(phone.database))
        exchange(phone, tablet)
        assertEquals(remoteData, profileData(tablet.database, removedProfile))

        ProfileDeletionManager.deleteProfile(phone.context, removedProfile).blockingAwait()
        exchange(phone, tablet)
        exchange(tablet, phone)
        assertLocallyRemoved()
        assertEquals(remoteData, profileData(tablet.database, removedProfile))
    }

    private fun assertLocallyRemoved() {
        assertNull(ProfileManager.getProfile(phone.context, removedProfile))
        assertTrue(ProfileManager.isProfileSyncTombstoned(phone.context, removedProfile))
        assertTrue(profileData(phone.database, removedProfile).isEmpty())
    }

    private fun seedProfile(database: AppDatabase, profileId: String) {
        val streamId = database.streamDAO().upsert(
            StreamEntity(
                serviceId = 0,
                url = VIDEO_URL,
                title = "Shared video",
                streamType = StreamType.VIDEO_STREAM,
                duration = 120,
                uploader = "Channel"
            )
        )
        database.streamHistoryDAO().insert(StreamHistoryEntity(streamId, DATE, 2, profileId))
        database.streamStateDAO().insert(StreamStateEntity(streamId, 42_000, profileId))
        database.searchHistoryDAO().insert(SearchHistoryEntry(DATE, 0, QUERY, profileId = profileId))
        val subscriptionId = database.subscriptionDAO().insert(
            SubscriptionEntity(serviceId = 0, url = CHANNEL_URL, name = "Channel", profileId = profileId)
        )
        val playlistId = database.playlistDAO().insert(
            PlaylistEntity(
                name = "Saved videos",
                isThumbnailPermanent = false,
                thumbnailStreamId = streamId,
                displayIndex = 0,
                profileId = profileId
            )
        )
        database.playlistStreamDAO().insertAll(
            listOf(
                PlaylistStreamEntity(playlistId, streamId, 0),
                PlaylistStreamEntity(playlistId, streamId, 1)
            )
        )
        database.playlistRemoteDAO().insert(
            PlaylistRemoteEntity(
                serviceId = 0,
                orderingName = "Bookmark",
                url = PLAYLIST_URL,
                thumbnailUrl = null,
                uploader = "Channel",
                displayIndex = 0,
                streamCount = 2,
                profileId = profileId
            )
        )
        val groupId = database.feedGroupDAO().insert(
            FeedGroupEntity(
                uid = 0,
                name = "Group",
                icon = FeedGroupIcon.STAR,
                sortOrder = 0,
                profileId = profileId
            )
        )
        database.feedGroupDAO().updateSubscriptionsForGroupForProfile(
            profileId,
            groupId,
            listOf(subscriptionId)
        )
    }

    private fun profileData(database: AppDatabase, profileId: String): ProfileData {
        val subscriptions = database.subscriptionDAO().getAllDirectForProfile(profileId)
        return ProfileData(
            history = database.streamHistoryDAO().getAllDirectForProfile(profileId).map {
                Triple(
                    requireNotNull(database.streamDAO().getStreamDirect(it.streamUid)).url,
                    it.accessDate,
                    it.repeatCount
                )
            },
            progress = database.streamStateDAO().getAllDirectForProfile(profileId).map {
                requireNotNull(database.streamDAO().getStreamDirect(it.streamUid)).url to it.progressMillis
            },
            searches = database.searchHistoryDAO().getAllDirectForProfile(profileId).map { it.serviceId to it.search },
            subscriptions = subscriptions.map { Triple(it.url, it.notificationMode, it.notificationKeywords) },
            playlists = database.playlistDAO().getAllDirectForProfile(profileId).map { playlist ->
                playlist.name to database.playlistStreamDAO()
                    .getOrderedStreamsDirectForProfile(profileId, playlist.uid).map { it.url }
            },
            bookmarks = database.playlistRemoteDAO().getAllDirectForProfile(profileId).map {
                Triple(it.serviceId, it.url, it.orderingName)
            },
            groups = database.feedGroupDAO().getAllDirectForProfile(profileId).map { group ->
                group.name to database.feedGroupDAO().getSubscriptionIdsForDirectForProfile(profileId, group.uid)
                    .map { id -> subscriptions.single { it.uid == id }.url }
            }
        )
    }

    private fun exchange(initiator: Peer, responder: Peer) {
        val profileRequest = initiator.profiles.createRequest()
        initiator.profiles.handleResponse(responder.profiles.handleRequest(profileRequest))
        repeatUntilSettled {
            val request = initiator.subscriptions.createRequest(responder.id)
            val response = responder.subscriptions.handleRequest(initiator.id, request)
            initiator.subscriptions.handleResponse(responder.id, response)
            !request.hasMore && !response.hasMore
        }
        repeatUntilSettled {
            val request = initiator.playlists.createRequest(responder.id)
            val response = responder.playlists.handleRequest(initiator.id, request)
            initiator.playlists.handleResponse(responder.id, response)
            !request.hasMore && !response.hasMore
        }
        for (category in listOf(HistorySyncCategory.WATCH, HistorySyncCategory.SEARCH)) {
            repeatUntilSettled {
                val request = initiator.history.createRequest(responder.id, category)
                val response = responder.history.handleRequest(initiator.id, request)
                initiator.history.handleResponse(responder.id, category, response)
                !request.hasMore && !response.hasMore
            }
        }
        repeatUntilSettled {
            val category = StructuredPreferenceCategory.FEED_GROUPS
            val request = initiator.structured.createRequest(responder.id, category)
            val response = responder.structured.handleRequest(initiator.id, request)
            initiator.structured.handleResponse(responder.id, category, response)
            !request.hasMore && !response.hasMore
        }
    }

    private fun repeatUntilSettled(exchange: () -> Boolean) {
        repeat(20) { if (exchange()) return }
        throw AssertionError("Synchronization did not converge within the fixture batch limit")
    }

    private fun journalSizes(database: AppDatabase): Map<String, Long> = JOURNALS.associateWith { table ->
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private fun assertContiguousJournals(database: AppDatabase) {
        for (table in JOURNALS) {
            val grouping = if (
                table == "history_sync_changes" || table == "structured_preference_sync_changes"
            ) {
                "category, origin_peer_id"
            } else {
                "origin_peer_id"
            }
            database.openHelper.readableDatabase.query(
                "SELECT MIN(origin_revision), MAX(origin_revision), COUNT(*) FROM $table GROUP BY $grouping"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    assertEquals("$table must retain its first revision", 1L, cursor.getLong(0))
                    assertEquals(
                        "$table must not skip deleted-profile revisions",
                        cursor.getLong(1),
                        cursor.getLong(2)
                    )
                }
            }
        }
    }

    private data class ProfileData(
        val history: List<Triple<String, OffsetDateTime, Long>>,
        val progress: List<Pair<String, Long>>,
        val searches: List<Pair<Int, String?>>,
        val subscriptions: List<Triple<String?, Int, String>>,
        val playlists: List<Pair<String?, List<String>>>,
        val bookmarks: List<Triple<Int, String?, String?>>,
        val groups: List<Pair<String, List<String?>>>
    ) {
        fun isEmpty(): Boolean = history.isEmpty() && progress.isEmpty() && searches.isEmpty() &&
            subscriptions.isEmpty() && playlists.isEmpty() && bookmarks.isEmpty() && groups.isEmpty()
    }

    private class Peer(val context: DeviceContext, val database: AppDatabase) {
        val id = DeviceIdentity(generateKeyPair(KeyType.ED25519).first).peerId.toBase58()
        private val available: (String) -> Boolean = { ProfileManager.getProfile(context, it) != null }
        val historyStore = RoomHistorySyncStore(database, id, available)
        val history = HistorySyncEngine(historyStore)
        val subscriptions = SubscriptionSyncEngine(RoomSubscriptionSyncStore(database, id, available))
        val playlists = PlaylistSyncEngine(RoomPlaylistSyncStore(database, id, available))
        val structured = StructuredPreferenceSyncEngine(
            RoomStructuredPreferenceSyncStore(
                context,
                database,
                id,
                canMaterializeProfile = available
            )
        )
        val profiles = ProfileSyncEngine(AndroidProfileSyncStore(context))
    }

    private class DeviceContext(base: Context, private val prefix: String) : ContextWrapper(base) {
        private val preferenceNames = ConcurrentHashMap<String, Boolean>()

        @Volatile
        var failNextFilesAccess = false

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File {
            if (failNextFilesAccess) {
                failNextFilesAccess = false
                throw IllegalStateException("Injected archive cleanup failure")
            }
            return File(baseContext.cacheDir, prefix).apply { mkdirs() }
        }

        override fun getNoBackupFilesDir(): File = File(filesDir, "no-backup").apply { mkdirs() }

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = "$prefix-$name"
            preferenceNames[isolatedName] = true
            return baseContext.getSharedPreferences(isolatedName, mode)
        }

        fun clear() {
            failNextFilesAccess = false
            preferenceNames.keys.forEach {
                baseContext.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
            filesDir.deleteRecursively()
        }
    }

    companion object {
        private const val VIDEO_URL = "https://example.com/watch/profile-deletion"
        private const val CHANNEL_URL = "https://example.com/channel/profile-deletion"
        private const val PLAYLIST_URL = "https://example.com/playlist/profile-deletion"
        private const val QUERY = "private query"
        private val DATE = OffsetDateTime.parse("2026-11-22T10:00:00Z")
        private val JOURNALS = listOf(
            "subscription_sync_changes",
            "playlist_sync_changes",
            "history_sync_changes",
            "structured_preference_sync_changes"
        )
    }
}
