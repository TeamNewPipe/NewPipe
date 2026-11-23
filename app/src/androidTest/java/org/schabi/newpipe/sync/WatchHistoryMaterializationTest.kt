package org.schabi.newpipe.sync

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(AndroidJUnit4::class)
class WatchHistoryMaterializationTest {
    private lateinit var database: AppDatabase
    private lateinit var store: RoomHistorySyncStore
    private lateinit var peerId: String
    private var revision = 0L
    private val scans = CopyOnWriteArrayList<Pair<String, List<Any?>>>()
    private val whitespace = Regex("\\s+")
    private val profiles = listOf(ProfileManager.DEFAULT_PROFILE_ID, "11111111-1111-4111-8111-111111111111")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries()
            .setQueryCallback(
                object : RoomDatabase.QueryCallback {
                    override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                        val query = sqlQuery.trim().replace(whitespace, " ")
                        if (query.startsWith("SELECT * FROM history_sync_records WHERE category =") && !query.contains("record_id")) {
                            scans.add(query to bindArgs.toList())
                        }
                    }
                },
                Executor { it.run() }
            )
            .build()
        peerId = DeviceIdentity(generateKeyPair(KeyType.ED25519).first).peerId.toBase58()
        store = RoomHistorySyncStore(database, peerId)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun multiStreamBatchReadsOnlyAffectedStreamsAndDuplicateBatchDoesNotScan() {
        apply(profiles.flatMap { profile -> (0 until 64).map { watch(profile, it) } })
        val batch = profiles.flatMap { profile -> (0 until 4).map { watch(profile, it) } }
        scans.clear()

        assertEquals(8, store.applyChanges(HistorySyncCategory.WATCH, batch).affectedRecords)

        assertEquals(profiles, scans.map { it.second.first() })
        assertTrue(scans.all { it.first.contains("stream_key IN") })
        profiles.zip(scans).forEach { (profile, scan) ->
            assertEquals((0 until 4).map { HistoryRecordId.progress(profile, stream(it).identity) }.toSet(), scan.second.drop(1).toSet())
        }
        profiles.forEach { profile ->
            val history = database.streamHistoryDAO().getAllDirectForProfile(profile)
            assertEquals(64, history.size)
            assertEquals(4, history.count { it.repeatCount == 2L })
            assertEquals(60, history.count { it.repeatCount == 1L })
        }
        scans.clear()
        assertEquals(0, store.applyChanges(HistorySyncCategory.WATCH, batch).acceptedChanges)
        assertTrue(scans.isEmpty())
        store.applyChanges(HistorySyncCategory.WATCH, emptyList())
        assertTrue(scans.isEmpty())
    }

    @Test
    fun globalClearsShareOneScanPreserveOtherProfilesAndRejectLateOldEvents() {
        apply(profiles.flatMap { profile -> (0 until 16).flatMap { listOf(watch(profile, it), progress(profile, it)) } })
        val first = profiles.first()
        val other = profiles.last()
        scans.clear()
        apply(
            listOf(
                change(first, HistoryRecordType.WATCH_ALL_TOMBSTONE, HistoryRecordId.watchAllTombstone(first)),
                change(first, HistoryRecordType.PLAYBACK_ALL_TOMBSTONE, HistoryRecordId.playbackAllTombstone(first))
            )
        )

        assertEquals(1, scans.size)
        assertTrue(scans.single().first.contains("stream_key IN"))
        assertEquals(first, scans.single().second.first())
        assertEquals(16, scans.single().second.size - 1)
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile(first).isEmpty())
        assertTrue(database.streamStateDAO().getAllDirectForProfile(first).isEmpty())
        assertEquals(16, database.streamHistoryDAO().getAllDirectForProfile(other).size)
        assertEquals(16, database.streamStateDAO().getAllDirectForProfile(other).size)

        apply(listOf(watch(first, 0).copy(lamportVersion = 1), progress(first, 0).copy(lamportVersion = 1)))
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile(first).isEmpty())
        assertTrue(database.streamStateDAO().getAllDirectForProfile(first).isEmpty())

        apply(
            listOf(
                change(first, HistoryRecordType.WATCH_ALL_TOMBSTONE, HistoryRecordId.watchAllTombstone(first)),
                watch(first, 0),
                change(first, HistoryRecordType.PLAYBACK_ALL_TOMBSTONE, HistoryRecordId.playbackAllTombstone(first)),
                progress(first, 0, 12_000)
            )
        )
        assertEquals(1L, database.streamHistoryDAO().getAllDirectForProfile(first).single().repeatCount)
        assertEquals(12_000L, database.streamStateDAO().getAllDirectForProfile(first).single().progressMillis)
        assertEquals(16, database.streamHistoryDAO().getAllDirectForProfile(other).size)
    }

    @Test
    fun globalClearsReceivedBeforeAnyStreamKeepTheirCutoffsAndRevisionOrder() {
        val profile = profiles.first()
        val delayed = listOf(watch(profile, 0), progress(profile, 0))
        val clears = listOf(
            change(profile, HistoryRecordType.WATCH_ALL_TOMBSTONE, HistoryRecordId.watchAllTombstone(profile)),
            change(profile, HistoryRecordType.PLAYBACK_ALL_TOMBSTONE, HistoryRecordId.playbackAllTombstone(profile))
        )

        apply(clears)

        assertTrue(scans.isEmpty())
        val records = database.historySyncDAO().getRecordsForProfile(HistorySyncCategory.WATCH.name, profile)
        assertEquals(clears.map { it.recordId }.toSet(), records.map { it.recordId }.toSet())
        assertTrue(records.all { it.recordJson == null && it.streamKey == null })

        scans.clear()
        apply(delayed)

        assertEquals(1, scans.size)
        assertEquals(listOf(profile, HistoryRecordId.progress(profile, stream(0).identity)), scans.single().second)
        assertEquals(mapOf(peerId to 4L), store.getKnownRevisions(HistorySyncCategory.WATCH))
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile(profile).isEmpty())
        assertTrue(database.streamStateDAO().getAllDirectForProfile(profile).isEmpty())

        apply(listOf(watch(profile, 0), progress(profile, 0, 12_000)))

        assertEquals(1L, database.streamHistoryDAO().getAllDirectForProfile(profile).single().repeatCount)
        assertEquals(12_000L, database.streamStateDAO().getAllDirectForProfile(profile).single().progressMillis)
    }

    @Test
    fun smallBatchDoesNotLoadUnrelatedLargeHistoryAndGlobalClearUsesBoundedPages() {
        val profile = profiles.first()
        database.runInTransaction {
            apply((0 until 4_096).map { watch(profile, it) })
        }
        scans.clear()
        val batch = (0 until 4).flatMap { listOf(watch(profile, it), progress(profile, it)) }

        assertEquals(8, store.applyChanges(HistorySyncCategory.WATCH, batch).affectedRecords)

        assertEquals(1, scans.size)
        assertTrue(scans.single().first.contains("stream_key IN"))
        assertEquals((0 until 4).map { HistoryRecordId.progress(profile, stream(it).identity) }.toSet(), scans.single().second.drop(1).toSet())
        val history = database.streamHistoryDAO().getAllDirectForProfile(profile)
        assertEquals(4_096, history.size)
        assertEquals(4, history.count { it.repeatCount == 2L })
        assertEquals(4_092, history.count { it.repeatCount == 1L })
        assertEquals(4, database.streamStateDAO().getAllDirectForProfile(profile).size)

        scans.clear()
        apply(listOf(change(profile, HistoryRecordType.WATCH_ALL_TOMBSTONE, HistoryRecordId.watchAllTombstone(profile))))

        assertEquals(32, scans.size)
        assertTrue(scans.all { it.first.contains("stream_key IN") && it.second.size == 129 })
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile(profile).isEmpty())
        assertEquals(4, database.streamStateDAO().getAllDirectForProfile(profile).size)
    }

    @Test
    fun malformedLegacyStreamRecordRejectsSyncWithoutAdvancingTheJournal() {
        val profile = profiles.first()
        val initial = watch(profile, 0)
        apply(listOf(initial))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE history_sync_records SET stream_key = NULL, record_json = ? WHERE category = 'WATCH' AND record_id = ?",
            arrayOf("{damaged", initial.recordId)
        )
        val knownBefore = store.getKnownRevisions(HistorySyncCategory.WATCH)

        assertThrows(HistorySyncException::class.java) {
            apply(listOf(watch(profile, 1)))
        }

        assertEquals(knownBefore, store.getKnownRevisions(HistorySyncCategory.WATCH))
        assertEquals(1, database.streamHistoryDAO().getAllDirectForProfile(profile).size)
    }

    @Test
    fun streamDeleteKeepsProgressAndUnavailableProfileDoesNotReappear() {
        val profile = profiles.first()
        apply(listOf(watch(profile, 0), watch(profile, 1), progress(profile, 0)))
        val stream = stream(0)
        apply(
            listOf(
                change(
                    profile,
                    HistoryRecordType.WATCH_STREAM_TOMBSTONE,
                    HistoryRecordId.watchStreamTombstone(profile, stream.identity),
                    SyncedHistoryRecord(watchStreamTombstone = SyncedWatchStreamTombstone(stream))
                )
            )
        )
        assertEquals(1, database.streamHistoryDAO().getAllDirectForProfile(profile).size)
        assertEquals(90_000L, database.streamStateDAO().getAllDirectForProfile(profile).single().progressMillis)

        store = RoomHistorySyncStore(database, peerId) { false }
        apply(
            listOf(
                change(profile, HistoryRecordType.WATCH_ALL_TOMBSTONE, HistoryRecordId.watchAllTombstone(profile)),
                watch(profile, 0)
            )
        )
        assertTrue(database.streamHistoryDAO().getAllDirectForProfile(profile).isEmpty())
        assertTrue(database.streamStateDAO().getAllDirectForProfile(profile).isEmpty())
    }

    private fun apply(changes: List<HistoryChange>) {
        changes.chunked(MAX_HISTORY_CHANGES_PER_BATCH).forEach {
            store.applyChanges(HistorySyncCategory.WATCH, it)
        }
    }

    private fun watch(profile: String, index: Int) = change(
        profile,
        HistoryRecordType.WATCH_EVENT,
        HistoryRecordId.watchEvent(),
        SyncedHistoryRecord(watchEvent = SyncedWatchEvent(stream(index), 1000 + revision, 1))
    )

    private fun progress(profile: String, index: Int, position: Long = 90_000): HistoryChange {
        val stream = stream(index)
        return change(
            profile,
            HistoryRecordType.PLAYBACK_PROGRESS,
            HistoryRecordId.progress(profile, stream.identity),
            SyncedHistoryRecord(playbackProgress = SyncedPlaybackProgress(stream, position, 1000 + revision))
        )
    }

    private fun change(
        profile: String,
        type: HistoryRecordType,
        id: String,
        record: SyncedHistoryRecord? = null
    ): HistoryChange {
        revision += 1
        return HistoryChange(
            category = HistorySyncCategory.WATCH,
            originPeerId = peerId,
            originRevision = revision,
            lamportVersion = revision,
            recordId = id,
            profileId = profile,
            recordType = type,
            type = HistoryChangeType.UPSERT,
            record = record
        )
    }

    private fun stream(index: Int) = SyncedHistoryStream(
        serviceId = 0,
        url = "https://example.com/watch/$index",
        title = "History $index",
        streamType = "VIDEO_STREAM",
        duration = 180,
        uploader = "Uploader"
    )
}
