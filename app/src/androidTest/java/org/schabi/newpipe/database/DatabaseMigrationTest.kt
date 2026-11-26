package org.schabi.newpipe.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.sync.HistoryRecordId
import org.schabi.newpipe.sync.HistoryStreamIdentity

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    companion object {
        private const val DEFAULT_SERVICE_ID = 0
        private const val DEFAULT_URL = "https://www.youtube.com/watch?v=cDphUib5iG4"
        private const val DEFAULT_TITLE = "Test Title"
        private const val DEFAULT_NAME = "Test Name"
        private val DEFAULT_TYPE = StreamType.VIDEO_STREAM
        private const val DEFAULT_DURATION = 480L
        private const val DEFAULT_UPLOADER_NAME = "Uploader Test"
        private const val DEFAULT_UPLOADER_AVATAR = "https://example.com/avatar.jpg"
        private const val DEFAULT_THUMBNAIL = "https://example.com/example.jpg"

        private const val DEFAULT_SECOND_SERVICE_ID = 1
        private const val DEFAULT_SECOND_URL = "https://www.youtube.com/watch?v=ncQU6iBn5Fc"

        private const val DEFAULT_THIRD_SERVICE_ID = 2
        private const val DEFAULT_THIRD_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate34to35PreservesSubscriptionsAndFeedWithUnmutedDefaults() {
        testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_34).use { database ->
            database.execSQL(
                "INSERT INTO subscriptions (uid, service_id, url, name, notification_mode, " +
                    "notification_keywords, youtube_mode_mask, profile_id, handle) VALUES " +
                    "(41, 0, 'https://example.com/channel', 'Channel', 2, 'science', 3, 'profile-a', '@channel'), " +
                    "(42, 0, 'https://example.com/channel', 'Other profile', 1, '', 1, 'profile-b', NULL)"
            )
            database.execSQL("INSERT INTO streams (uid, service_id, url, title, stream_type, duration, uploader) VALUES (7, 0, 'https://example.com/video', 'Cached', 'VIDEO_STREAM', 60, 'Channel')")
            database.execSQL("INSERT INTO feed (stream_id, subscription_id, youtube_mode_mask, first_discovered_at) VALUES (7, 41, 2, 1000)")
            database.execSQL("INSERT INTO feed_last_updated (subscription_id, youtube_mode_mask, last_updated) VALUES (41, 2, 2000)")
            database.execSQL("INSERT INTO feed_group (uid, name, icon_id, sort_order, profile_id) VALUES (9, 'Group', 0, 0, 'profile-a')")
            database.execSQL("INSERT INTO feed_group_subscription_join (group_id, subscription_id) VALUES (9, 41)")
        }
        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_35,
            true,
            Migrations.MIGRATION_34_35
        )
        migrated.query("SELECT uid, profile_id, notification_mode, notification_keywords, youtube_mode_mask, handle, feed_muted FROM subscriptions ORDER BY uid").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(41L, cursor.getLong(0))
            assertEquals("profile-a", cursor.getString(1))
            assertEquals(2, cursor.getInt(2))
            assertEquals("science", cursor.getString(3))
            assertEquals(3, cursor.getInt(4))
            assertEquals("@channel", cursor.getString(5))
            assertEquals(0, cursor.getInt(6))
            assertTrue(cursor.moveToNext())
            assertEquals(42L, cursor.getLong(0))
            assertEquals("profile-b", cursor.getString(1))
            assertEquals(0, cursor.getInt(6))
            assertFalse(cursor.moveToNext())
        }
        migrated.query("SELECT stream_id, subscription_id, youtube_mode_mask, first_discovered_at FROM feed").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(listOf(7L, 41L, 2L, 1000L), (0..3).map(cursor::getLong))
            assertFalse(cursor.moveToNext())
        }
        migrated.query("SELECT last_updated FROM feed_last_updated WHERE subscription_id = 41").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2000L, cursor.getLong(0))
        }
        migrated.query("SELECT group_id, subscription_id FROM feed_group_subscription_join").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9L, cursor.getLong(0))
            assertEquals(41L, cursor.getLong(1))
        }
        migrated.execSQL("UPDATE subscriptions SET feed_muted = 1 WHERE uid = 41")
        migrated.query("SELECT feed_muted FROM subscriptions ORDER BY uid").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertTrue(cursor.moveToNext())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun migrate33to34PreservesStreamsAndHistoryWithoutGuessingUpcomingStatus() {
        testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_33).use { database ->
            for ((index, date) in listOf<Long?>(null, 0L, 4_102_444_800_000L).withIndex()) {
                database.insert(
                    "streams",
                    SQLiteDatabase.CONFLICT_FAIL,
                    ContentValues().apply {
                        put("uid", index + 1L)
                        put("service_id", DEFAULT_SERVICE_ID)
                        put("url", "https://example.com/stream-$index")
                        put("title", DEFAULT_TITLE)
                        put("stream_type", DEFAULT_TYPE.name)
                        put("duration", DEFAULT_DURATION)
                        put("uploader", DEFAULT_UPLOADER_NAME)
                        put("upload_date", date)
                        put("content_category", "COURSES")
                    }
                )
            }
            database.execSQL("INSERT INTO stream_history (stream_id, access_date, repeat_count, profile_id) VALUES (1, 123456789, 3, 'profile-a')")
        }
        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_34,
            true,
            Migrations.MIGRATION_33_34
        )
        migrated.query("SELECT uid, title, duration, content_category, upcoming_status FROM streams ORDER BY uid").use { cursor ->
            for (id in 1L..3L) {
                assertTrue(cursor.moveToNext())
                assertEquals(id, cursor.getLong(0))
                assertEquals(DEFAULT_TITLE, cursor.getString(1))
                assertEquals(DEFAULT_DURATION, cursor.getLong(2))
                assertEquals("COURSES", cursor.getString(3))
                assertTrue(cursor.isNull(4))
            }
            assertFalse(cursor.moveToNext())
        }
        migrated.query("SELECT stream_id, access_date, repeat_count, profile_id FROM stream_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(123456789L, cursor.getLong(1))
            assertEquals(3L, cursor.getLong(2))
            assertEquals("profile-a", cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
        migrated.execSQL("UPDATE streams SET upcoming_status = 1 WHERE uid = 1")
        migrated.execSQL("UPDATE streams SET upcoming_status = 0 WHERE uid = 2")
        migrated.query("SELECT upcoming_status FROM streams ORDER BY uid").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertTrue(cursor.moveToNext())
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.moveToNext())
            assertTrue(cursor.isNull(0))
        }
    }

    @Test
    fun migrate32to33IndexesWatchStreamsAndPreservesLegacyPayloads() {
        val url = "https://example.com/watch?q=quoted\"&title=日本語"
        val profile = "11111111-1111-4111-8111-111111111111"
        val fields = listOf("WATCH_EVENT" to "watchEvent", "PLAYBACK_PROGRESS" to "playbackProgress", "WATCH_STREAM_TOMBSTONE" to "watchStreamTombstone")
        val payloads = fields.map { (_, field) ->
            JSONObject().put(field, JSONObject().put("stream", JSONObject().put("serviceId", 0).put("url", url))).toString()
        } + listOf("{damaged", null, "{\"searchEvent\":{\"query\":\"unchanged\"}}")
        testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_32).use { database ->
            payloads.forEachIndexed { index, payload ->
                database.insert(
                    "history_sync_records",
                    SQLiteDatabase.CONFLICT_FAIL,
                    ContentValues().apply {
                        put("category", if (index == 5) "SEARCH" else "WATCH")
                        put("record_id", "record-$index")
                        put("profile_id", if (index == 0) ProfileManager.DEFAULT_PROFILE_ID else profile)
                        put(
                            "record_type",
                            fields.getOrNull(index)?.first ?: when (index) {
                                4 -> "WATCH_ALL_TOMBSTONE"
                                5 -> "SEARCH_EVENT"
                                else -> "WATCH_EVENT"
                            }
                        )
                        put("lamport_version", 7L)
                        put("origin_peer_id", "legacy-peer")
                        put("origin_revision", index + 1L)
                        put("is_deleted", if (index == 1) 1 else 0)
                        put("record_json", payload)
                    }
                )
            }
        }
        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_33,
            true,
            Migrations.MIGRATION_32_33
        )
        migrated.query("SELECT profile_id, record_json, stream_key, lamport_version, origin_revision, is_deleted FROM history_sync_records ORDER BY record_id").use { rows ->
            payloads.forEachIndexed { index, payload ->
                assertTrue(rows.moveToNext())
                assertEquals(payload, rows.getString(1))
                if (index < 3) {
                    assertEquals(HistoryRecordId.progress(rows.getString(0), HistoryStreamIdentity(0, url)), rows.getString(2))
                } else {
                    assertTrue(rows.isNull(2))
                }
                assertEquals(7L, rows.getLong(3))
                assertEquals(index + 1L, rows.getLong(4))
                assertEquals(if (index == 1) 1 else 0, rows.getInt(5))
            }
            assertFalse(rows.moveToNext())
        }
    }

    @Test
    fun migrate30to31PreservesSubscriptionMetadata() {
        val old = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_30)
        old.execSQL("INSERT INTO subscriptions (uid, service_id, url, name, notification_mode) VALUES (42, 0, 'https://www.youtube.com/channel/UCtest', 'Travel', 0)")
        old.close()
        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_31,
            true,
            Migrations.MIGRATION_30_31
        )
        migrated.query("SELECT uid, name, handle FROM subscriptions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals("Travel", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun migrate31to32PreservesHistoryAndLeavesCategoriesUnknown() {
        testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_31).use { database ->
            database.insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("uid", 42L)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                    put("thumbnail_url", DEFAULT_THUMBNAIL)
                    put("is_short_form_content", 1)
                }
            )
            database.execSQL(
                "INSERT INTO stream_history (stream_id, access_date, repeat_count, profile_id) " +
                    "VALUES (42, 123456789, 3, 'profile-a')"
            )
            database.execSQL(
                "INSERT INTO stream_state (stream_id, progress_time, profile_id) " +
                    "VALUES (42, 45000, 'profile-a')"
            )
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_32,
            true,
            Migrations.MIGRATION_31_32
        )
        migrated.query(
            "SELECT uid, service_id, url, title, stream_type, duration, uploader, " +
                "thumbnail_url, is_short_form_content, content_category FROM streams"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals(DEFAULT_SERVICE_ID, cursor.getInt(1))
            assertEquals(DEFAULT_URL, cursor.getString(2))
            assertEquals(DEFAULT_TITLE, cursor.getString(3))
            assertEquals(DEFAULT_TYPE.name, cursor.getString(4))
            assertEquals(DEFAULT_DURATION, cursor.getLong(5))
            assertEquals(DEFAULT_UPLOADER_NAME, cursor.getString(6))
            assertEquals(DEFAULT_THUMBNAIL, cursor.getString(7))
            assertEquals(1, cursor.getInt(8))
            assertTrue(cursor.isNull(9))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            "SELECT stream_id, access_date, repeat_count, profile_id FROM stream_history"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals(123456789L, cursor.getLong(1))
            assertEquals(3L, cursor.getLong(2))
            assertEquals("profile-a", cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
        migrated.query("SELECT stream_id, progress_time, profile_id FROM stream_state").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals(45000L, cursor.getLong(1))
            assertEquals("profile-a", cursor.getString(2))
            assertFalse(cursor.moveToNext())
        }

        migrated.insert(
            "streams",
            SQLiteDatabase.CONFLICT_FAIL,
            ContentValues().apply {
                put("uid", 43L)
                put("service_id", DEFAULT_SERVICE_ID)
                put("url", DEFAULT_SECOND_URL)
                put("title", "Unknown category")
                put("stream_type", DEFAULT_TYPE.name)
                put("duration", DEFAULT_DURATION)
                put("uploader", DEFAULT_UPLOADER_NAME)
            }
        )
        migrated.query("SELECT content_category FROM streams WHERE uid = 43").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun migrateDatabaseFrom2to3() {
        val databaseInV2 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_2)

        databaseInV2.run {
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                    put("thumbnail_url", DEFAULT_THUMBNAIL)
                }
            )
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SECOND_SERVICE_ID)
                    put("url", DEFAULT_SECOND_URL)
                }
            )
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_3,
            true,
            Migrations.MIGRATION_2_3
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_4,
            true,
            Migrations.MIGRATION_3_4
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_5,
            true,
            Migrations.MIGRATION_4_5
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_6,
            true,
            Migrations.MIGRATION_5_6
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_7,
            true,
            Migrations.MIGRATION_6_7
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_8,
            true,
            Migrations.MIGRATION_7_8
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9,
            true,
            Migrations.MIGRATION_8_9
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )

        val migratedDatabaseV3 = getMigratedDatabase()
        val listFromDB = migratedDatabaseV3.streamDAO().getAll().blockingFirst()

        // Only expect 2, the one with the null url will be ignored
        assertEquals(2, listFromDB.size)

        val streamFromMigratedDatabase = listFromDB[0]
        assertEquals(DEFAULT_SERVICE_ID, streamFromMigratedDatabase.serviceId)
        assertEquals(DEFAULT_URL, streamFromMigratedDatabase.url)
        assertEquals(DEFAULT_TITLE, streamFromMigratedDatabase.title)
        assertEquals(DEFAULT_TYPE, streamFromMigratedDatabase.streamType)
        assertEquals(DEFAULT_DURATION, streamFromMigratedDatabase.duration)
        assertEquals(DEFAULT_UPLOADER_NAME, streamFromMigratedDatabase.uploader)
        assertEquals(DEFAULT_THUMBNAIL, streamFromMigratedDatabase.thumbnailUrl)
        assertNull(streamFromMigratedDatabase.viewCount)
        assertNull(streamFromMigratedDatabase.textualUploadDate)
        assertNull(streamFromMigratedDatabase.uploadDate)
        assertNull(streamFromMigratedDatabase.isUploadDateApproximation)

        val secondStreamFromMigratedDatabase = listFromDB[1]
        assertEquals(DEFAULT_SECOND_SERVICE_ID, secondStreamFromMigratedDatabase.serviceId)
        assertEquals(DEFAULT_SECOND_URL, secondStreamFromMigratedDatabase.url)
        assertEquals("", secondStreamFromMigratedDatabase.title)
        // Should fallback to VIDEO_STREAM
        assertEquals(StreamType.VIDEO_STREAM, secondStreamFromMigratedDatabase.streamType)
        assertEquals(0, secondStreamFromMigratedDatabase.duration)
        assertEquals("", secondStreamFromMigratedDatabase.uploader)
        assertEquals("", secondStreamFromMigratedDatabase.thumbnailUrl)
        assertNull(secondStreamFromMigratedDatabase.viewCount)
        assertNull(secondStreamFromMigratedDatabase.textualUploadDate)
        assertNull(secondStreamFromMigratedDatabase.uploadDate)
        assertNull(secondStreamFromMigratedDatabase.isUploadDateApproximation)
    }

    @Test
    fun migrateDatabaseFrom7to8() {
        val databaseInV7 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_7)

        val defaultSearch1 = " abc "
        val defaultSearch2 = " abc"

        val serviceId = DEFAULT_SERVICE_ID // YouTube
        // Use id different to YouTube because two searches with the same query
        // but different service are considered not equal.
        val otherServiceId = ServiceList.SoundCloud.serviceId

        databaseInV7.run {
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", serviceId)
                    put("search", defaultSearch1)
                }
            )
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", serviceId)
                    put("search", defaultSearch2)
                }
            )
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", otherServiceId)
                    put("search", defaultSearch1)
                }
            )
            insert(
                "search_history",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", otherServiceId)
                    put("search", defaultSearch2)
                }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_8,
            true,
            Migrations.MIGRATION_7_8
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9,
            true,
            Migrations.MIGRATION_8_9
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )

        val migratedDatabaseV8 = getMigratedDatabase()
        val listFromDB = migratedDatabaseV8.searchHistoryDAO().getAll().blockingFirst()

        assertEquals(2, listFromDB.size)
        assertEquals("abc", listFromDB[0].search)
        assertEquals("abc", listFromDB[1].search)
        assertNotEquals(listFromDB[0].serviceId, listFromDB[1].serviceId)
    }

    @Test
    fun migrateDatabaseFrom8to9() {
        val databaseInV8 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_8)

        val localUid1: Long
        val localUid2: Long
        val remoteUid1: Long
        val remoteUid2: Long
        databaseInV8.run {
            localUid1 = insert(
                "playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("name", DEFAULT_NAME + "1")
                    put("is_thumbnail_permanent", false)
                    put("thumbnail_stream_id", -1)
                }
            )
            localUid2 = insert(
                "playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("name", DEFAULT_NAME + "2")
                    put("is_thumbnail_permanent", false)
                    put("thumbnail_stream_id", -1)
                }
            )
            delete(
                "playlists",
                "uid = ?",
                Array(1) { localUid1 }
            )
            remoteUid1 = insert(
                "remote_playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                }
            )
            remoteUid2 = insert(
                "remote_playlists",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SECOND_SERVICE_ID)
                    put("url", DEFAULT_SECOND_URL)
                }
            )
            delete(
                "remote_playlists",
                "uid = ?",
                Array(1) { remoteUid2 }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9,
            true,
            Migrations.MIGRATION_8_9
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )

        val migratedDatabaseV9 = getMigratedDatabase()
        var localListFromDB = migratedDatabaseV9.playlistDAO().getAll().blockingFirst()
        var remoteListFromDB = migratedDatabaseV9.playlistRemoteDAO().getAll().blockingFirst()

        assertEquals(1, localListFromDB.size)
        assertEquals(localUid2, localListFromDB[0].uid)
        assertEquals(-1, localListFromDB[0].displayIndex)
        assertEquals(1, remoteListFromDB.size)
        assertEquals(remoteUid1, remoteListFromDB[0].uid)
        assertEquals(-1, remoteListFromDB[0].displayIndex)

        val localUid3 = migratedDatabaseV9.playlistDAO().insert(
            PlaylistEntity(
                name = "${DEFAULT_NAME}3",
                isThumbnailPermanent = false,
                thumbnailStreamId = -1,
                displayIndex = -1
            )
        )
        val remoteUid3 = migratedDatabaseV9.playlistRemoteDAO().insert(
            PlaylistRemoteEntity(
                serviceId = DEFAULT_THIRD_SERVICE_ID,
                orderingName = DEFAULT_NAME,
                url = DEFAULT_THIRD_URL,
                thumbnailUrl = DEFAULT_THUMBNAIL,
                uploader = DEFAULT_UPLOADER_NAME,
                displayIndex = -1,
                streamCount = 10
            )
        )

        localListFromDB = migratedDatabaseV9.playlistDAO().getAll().blockingFirst()
        remoteListFromDB = migratedDatabaseV9.playlistRemoteDAO().getAll().blockingFirst()
        assertEquals(2, localListFromDB.size)
        assertEquals(localUid3, localListFromDB[1].uid)
        assertEquals(-1, localListFromDB[1].displayIndex)
        assertEquals(2, remoteListFromDB.size)
        assertEquals(remoteUid3, remoteListFromDB[1].uid)
        assertEquals(-1, remoteListFromDB[1].displayIndex)
    }

    @Test
    fun migrateDatabaseFrom9to10() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_9
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10,
            true,
            Migrations.MIGRATION_9_10
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom10to11() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_10
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11,
            true,
            Migrations.MIGRATION_10_11
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom11to12() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_11
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12,
            true,
            Migrations.MIGRATION_11_12
        )
        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom12to13() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_12
        ).close()

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13,
            true,
            Migrations.MIGRATION_12_13
        )
    }

    @Test
    fun migrateDatabaseFrom13to14DefaultsSubscriptionsToRegularYoutube() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_13
        ).apply {
            insert(
                "subscriptions",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("name", DEFAULT_NAME)
                    put("notification_mode", 0)
                }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_14,
            true,
            Migrations.MIGRATION_13_14
        )

        val subscription = getMigratedDatabase()
            .subscriptionDAO()
            .getSubscriptionDirect(DEFAULT_SERVICE_ID, DEFAULT_URL)!!
        assertEquals(1, subscription.youtubeModeMask)
    }

    @Test
    fun migrateDatabaseFrom14to15SeparatesYoutubeFeedModes() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_14
        ).apply {
            insert(
                "subscriptions",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("uid", 1)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("name", DEFAULT_NAME)
                    put("notification_mode", 0)
                    put("youtube_mode_mask", 3)
                }
            )
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("uid", 1)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                }
            )
            insert(
                "feed",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("stream_id", 1)
                    put("subscription_id", 1)
                }
            )
            insert(
                "feed_last_updated",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("subscription_id", 1)
                    put("last_updated", 1234)
                }
            )
            close()
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_15,
            true,
            Migrations.MIGRATION_14_15
        )
        migrated.query(
            "SELECT youtube_mode_mask FROM feed ORDER BY youtube_mode_mask"
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            cursor.moveToNext()
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query(
            "SELECT youtube_mode_mask FROM feed_last_updated ORDER BY youtube_mode_mask"
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            cursor.moveToNext()
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrateDatabaseFrom15to16AddsUploaderAvatar() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_15
        ).apply {
            insert(
                "streams",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("uid", 1)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                }
            )
            close()
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_16,
            true,
            Migrations.MIGRATION_15_16
        )
        migrated.query(
            "SELECT uploader_avatar_url FROM streams WHERE uid = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }

        migrated.execSQL(
            "UPDATE streams SET uploader_avatar_url = ? WHERE uid = 1",
            arrayOf(DEFAULT_UPLOADER_AVATAR)
        )
        migrated.query(
            "SELECT uploader_avatar_url FROM streams WHERE uid = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(DEFAULT_UPLOADER_AVATAR, cursor.getString(0))
        }
    }

    @Test
    fun migrateDatabaseFrom16to17AddsLearningNotes() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_16
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_17,
            true,
            Migrations.MIGRATION_16_17
        )
        migrated.query("PRAGMA table_info(`learning_notes`)").use { cursor ->
            assertEquals(6, cursor.count)
        }
    }

    @Test
    fun migrateDatabaseFrom17to18AddsLearningSessions() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_17
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_18,
            true,
            Migrations.MIGRATION_17_18
        )
        migrated.query("PRAGMA table_info(`learning_sessions`)").use { cursor ->
            assertEquals(7, cursor.count)
        }
    }

    @Test
    fun migrateDatabaseFrom18to19AddsMembershipRestriction() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_18
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_19,
            true,
            Migrations.MIGRATION_18_19
        )
        migrated.query("PRAGMA table_info(streams)").use { cursor ->
            val columnNameIndex = cursor.getColumnIndex("name")
            var foundMembershipColumn = false
            while (cursor.moveToNext()) {
                foundMembershipColumn = foundMembershipColumn ||
                    cursor.getString(columnNameIndex) == "requires_membership"
            }
            assertTrue(foundMembershipColumn)
        }
    }

    @Test
    fun migrateDatabaseFrom19to20AddsLocalMediaSourceColumns() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_19
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_20,
            true,
            Migrations.MIGRATION_19_20
        )
        migrated.query("PRAGMA table_info(streams)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            val columns = mutableMapOf<String, String?>()
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] = cursor.getString(defaultIndex)
            }
            assertEquals("'REMOTE'", columns["source_type"])
            assertTrue(columns.containsKey("mime_type"))
            assertTrue(columns.containsKey("local_media_id"))
            assertTrue(columns.containsKey("local_album"))
            assertTrue(columns.containsKey("local_folder"))
        }
    }

    @Test
    fun migrateDatabaseFrom20to21AddsLearningContentSelection() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_20
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_21,
            true,
            Migrations.MIGRATION_20_21
        )
        migrated.query("PRAGMA table_info(learning_content_sources)").use { cursor ->
            assertEquals(8, cursor.count)
        }
        migrated.query("PRAGMA table_info(learning_content_streams)").use { cursor ->
            assertEquals(2, cursor.count)
        }
        migrated.query("PRAGMA table_info(learning_sessions)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var foundDesignatedColumn = false
            while (cursor.moveToNext()) {
                foundDesignatedColumn = foundDesignatedColumn ||
                    cursor.getString(nameIndex) == "is_designated"
            }
            assertTrue(foundDesignatedColumn)
        }
    }

    @Test
    fun migrateDatabaseFrom21to22AddsNotificationKeywordFilters() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_21
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_22,
            true,
            Migrations.MIGRATION_21_22
        )
        migrated.query("PRAGMA table_info(subscriptions)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            var defaultValue: String? = null
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "notification_keywords") {
                    defaultValue = cursor.getString(defaultIndex)
                }
            }
            assertEquals("''", defaultValue)
        }
        migrated.query("PRAGMA table_info(subscription_sync_changes)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            assertTrue(columns.contains("notification_mode"))
            assertTrue(columns.contains("notification_keywords"))
        }
        migrated.query("PRAGMA table_info(subscription_sync_records)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            assertTrue(columns.contains("notification_mode"))
            assertTrue(columns.contains("notification_keywords"))
        }
    }

    @Test
    fun migrateDatabaseFrom22to23AddsSavedSearchFeeds() {
        val database = testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_22
        )
        database.close()

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_23,
            true,
            Migrations.MIGRATION_22_23
        )
        migrated.query("PRAGMA table_info(saved_search_feed)").use { cursor ->
            assertEquals(8, cursor.count)
        }
        migrated.query("PRAGMA table_info(saved_search_feed_stream)").use { cursor ->
            assertEquals(3, cursor.count)
        }
    }

    @Test
    fun migrateDatabaseFrom23to24PreservesFeedMembershipsWithUnknownDiscoveryTime() {
        testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_23).use { database ->
            database.execSQL("INSERT INTO subscriptions (uid, service_id, url, notification_mode) VALUES (7, 0, 'https://example.com/channel', 0)")
            database.execSQL("INSERT INTO streams (uid, service_id, url, title, stream_type, duration, uploader) VALUES (42, 0, 'https://example.com/video', 'Video', 'VIDEO_STREAM', 60, 'Channel')")
            database.execSQL("INSERT INTO feed (stream_id, subscription_id, youtube_mode_mask) VALUES (42, 7, 1)")
        }
        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_24,
            true,
            Migrations.MIGRATION_23_24
        )
        migrated.query("SELECT stream_id, subscription_id, first_discovered_at FROM feed").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals(7L, cursor.getLong(1))
            assertEquals(0L, cursor.getLong(2))
        }
    }

    @Test
    fun migrateDatabaseFrom24to25ScopesSubscriptionsAndGroupsToDefaultProfile() {
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_24
        ).use { database ->
            database.execSQL(
                "INSERT INTO subscriptions " +
                    "(uid, service_id, url, notification_mode) " +
                    "VALUES (7, 0, 'https://example.com/profile-channel', 0)"
            )
            database.execSQL(
                "INSERT INTO feed_group (uid, name, icon_id, sort_order) " +
                    "VALUES (3, 'Existing group', 0, 0)"
            )
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_25,
            true,
            Migrations.MIGRATION_24_25
        )

        migrated.query(
            "SELECT profile_id FROM subscriptions WHERE uid = 7"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(defaultProfileId, cursor.getString(0))
        }
        migrated.query(
            "SELECT profile_id FROM feed_group WHERE uid = 3"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(defaultProfileId, cursor.getString(0))
        }

        migrated.execSQL(
            "INSERT INTO subscriptions " +
                "(service_id, url, notification_mode, profile_id) " +
                "VALUES (0, 'https://example.com/profile-channel', 0, 'other-profile')"
        )
        migrated.query(
            "SELECT COUNT(*) FROM subscriptions " +
                "WHERE url = 'https://example.com/profile-channel'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrateDatabaseFrom25to26ScopesWatchHistoryAndProgressToDefaultProfile() {
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_25
        ).use { database ->
            database.execSQL(
                "INSERT INTO streams " +
                    "(uid, service_id, url, title, stream_type, duration, uploader) " +
                    "VALUES (42, 0, 'https://example.com/history-video', " +
                    "'History video', 'VIDEO_STREAM', 60, 'Channel')"
            )
            database.execSQL(
                "INSERT INTO stream_history (stream_id, access_date, repeat_count) " +
                    "VALUES (42, 1234, 2)"
            )
            database.execSQL(
                "INSERT INTO stream_state (stream_id, progress_time) VALUES (42, 30000)"
            )
            database.execSQL(
                "INSERT INTO learning_sessions " +
                    "(session_id, stream_id, started_at, ended_at, watched_duration_ms, " +
                    "local_date, background_playback, is_designated) " +
                    "VALUES ('session-1', 42, 1000, 2000, 1000, '2026-09-20', 0, 1)"
            )
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_26,
            true,
            Migrations.MIGRATION_25_26
        )

        migrated.query(
            "SELECT profile_id, repeat_count FROM stream_history WHERE stream_id = 42"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(defaultProfileId, cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
        }
        migrated.query(
            "SELECT profile_id, progress_time FROM stream_state WHERE stream_id = 42"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(defaultProfileId, cursor.getString(0))
            assertEquals(30_000L, cursor.getLong(1))
        }

        migrated.query(
            "SELECT profile_id FROM learning_sessions WHERE session_id = 'session-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(defaultProfileId, cursor.getString(0))
        }

        migrated.execSQL(
            "INSERT INTO stream_state (stream_id, progress_time, profile_id) " +
                "VALUES (42, 10000, 'other-profile')"
        )
        migrated.query(
            "SELECT COUNT(*) FROM stream_state WHERE stream_id = 42"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrateDatabaseFrom26to27ScopesPlaylistsAndSavedFeedsToDefaultProfile() {
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_26
        ).use { database ->
            database.execSQL(
                "INSERT INTO playlists " +
                    "(uid, name, is_thumbnail_permanent, thumbnail_stream_id, display_index) " +
                    "VALUES (5, 'Existing local', 0, -1, 0)"
            )
            database.execSQL(
                "INSERT INTO remote_playlists " +
                    "(uid, service_id, name, url, display_index) " +
                    "VALUES (6, 0, 'Existing remote', 'https://example.com/playlist', 0)"
            )
            database.execSQL(
                "INSERT INTO saved_search_feed " +
                    "(uid, name, service_id, query, content_filter, sort_filter, sort_order) " +
                    "VALUES (7, 'Existing search', 0, 'query', '', '', 0)"
            )
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_27,
            true,
            Migrations.MIGRATION_26_27
        )

        listOf("playlists" to 5L, "remote_playlists" to 6L, "saved_search_feed" to 7L)
            .forEach { (table, id) ->
                migrated.query(
                    "SELECT profile_id FROM $table WHERE uid = $id"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(defaultProfileId, cursor.getString(0))
                }
            }

        migrated.execSQL(
            "INSERT INTO remote_playlists " +
                "(service_id, name, url, display_index, profile_id) " +
                "VALUES (0, 'Other remote', 'https://example.com/playlist', 0, 'other-profile')"
        )
        migrated.query(
            "SELECT COUNT(*) FROM remote_playlists " +
                "WHERE url = 'https://example.com/playlist'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        migrated.execSQL(
            "INSERT INTO saved_search_feed " +
                "(name, service_id, query, content_filter, sort_filter, sort_order, profile_id) " +
                "VALUES ('Other search', 0, 'query', '', '', 0, 'other-profile')"
        )
        migrated.query(
            "SELECT COUNT(*) FROM saved_search_feed WHERE query = 'query'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrateDatabaseFrom27to28ScopesSyncJournalsToDefaultProfile() {
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_27
        ).use { database ->
            database.execSQL(
                "INSERT INTO subscription_sync_records " +
                    "(record_id, service_id, url, lamport_version, origin_peer_id, " +
                    "origin_revision, is_deleted) VALUES " +
                    "('subscription-record', 0, 'https://example.com/channel', 1, " +
                    "'peer-a', 1, 0)"
            )
            database.execSQL(
                "INSERT INTO playlist_sync_records " +
                    "(record_id, record_type, lamport_version, origin_peer_id, " +
                    "origin_revision, is_deleted) VALUES " +
                    "('playlist-record', 'REMOTE_PLAYLIST', 1, 'peer-a', 1, 0)"
            )
            database.execSQL(
                "INSERT INTO history_sync_records " +
                    "(category, record_id, record_type, lamport_version, origin_peer_id, " +
                    "origin_revision, is_deleted) VALUES " +
                    "('WATCH', 'history-record', 'WATCH_EVENT', 1, 'peer-a', 1, 0)"
            )
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_28,
            true,
            Migrations.MIGRATION_27_28
        )

        listOf(
            "subscription_sync_records" to "subscription-record",
            "playlist_sync_records" to "playlist-record",
            "history_sync_records" to "history-record"
        ).forEach { (table, recordId) ->
            migrated.query(
                "SELECT profile_id FROM $table WHERE record_id = '$recordId'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(defaultProfileId, cursor.getString(0))
            }
        }

        listOf(
            "subscription_sync_changes",
            "playlist_sync_changes",
            "history_sync_changes"
        ).forEach { table ->
            migrated.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var hasProfileId = false
                while (cursor.moveToNext()) {
                    hasProfileId = hasProfileId || cursor.getString(nameIndex) == "profile_id"
                }
                assertTrue(hasProfileId)
            }
        }

        migrated.execSQL(
            "INSERT INTO subscription_sync_records " +
                "(record_id, profile_id, service_id, url, lamport_version, origin_peer_id, " +
                "origin_revision, is_deleted) VALUES " +
                "('subscription-record-work', '11111111-1111-1111-1111-111111111111', " +
                "0, 'https://example.com/channel', 2, 'peer-b', 1, 0)"
        )
        migrated.query(
            "SELECT COUNT(*) FROM subscription_sync_records " +
                "WHERE url = 'https://example.com/channel'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun migrateDatabaseFrom28to29PersistsShortFormClassification() {
        testHelper.createDatabase(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_28
        ).use { database ->
            database.execSQL(
                "INSERT INTO streams " +
                    "(uid, service_id, url, title, stream_type, duration, uploader) VALUES " +
                    "(42, 0, 'https://www.youtube.com/shorts/abcdefghijk', " +
                    "'Short', 'VIDEO_STREAM', 60, 'Channel')"
            )
            database.execSQL(
                "INSERT INTO streams " +
                    "(uid, service_id, url, title, stream_type, duration, uploader) VALUES " +
                    "(43, 0, 'https://www.youtube.com/watch?v=abcdefghijk', " +
                    "'Regular short video', 'VIDEO_STREAM', 60, 'Channel')"
            )
        }

        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_29,
            true,
            Migrations.MIGRATION_28_29
        )

        migrated.query(
            "SELECT uid, is_short_form_content FROM streams WHERE uid IN (42, 43) ORDER BY uid"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertTrue(cursor.moveToNext())
            assertEquals(43L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
        }
    }

    @Test
    fun migrateDatabaseFrom29to30KeepsSearchHistoryInDefaultProfile() {
        testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_29).use { database ->
            database.execSQL(
                "INSERT INTO search_history (id, service_id, search, creation_date) " +
                    "VALUES (42, 0, 'kept query', 1000)"
            )
        }
        val migrated = testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME,
            Migrations.DB_VER_30,
            true,
            Migrations.MIGRATION_29_30
        )
        migrated.query("SELECT id, search, profile_id FROM search_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals("kept query", cursor.getString(1))
            assertEquals("00000000-0000-0000-0000-000000000000", cursor.getString(2))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun getMigratedDatabase(): AppDatabase {
        val database: AppDatabase = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                Migrations.MIGRATION_13_14,
                Migrations.MIGRATION_14_15,
                Migrations.MIGRATION_15_16,
                Migrations.MIGRATION_16_17,
                Migrations.MIGRATION_17_18,
                Migrations.MIGRATION_18_19,
                Migrations.MIGRATION_19_20,
                Migrations.MIGRATION_20_21,
                Migrations.MIGRATION_21_22,
                Migrations.MIGRATION_22_23,
                Migrations.MIGRATION_23_24,
                Migrations.MIGRATION_24_25,
                Migrations.MIGRATION_25_26,
                Migrations.MIGRATION_26_27,
                Migrations.MIGRATION_27_28,
                Migrations.MIGRATION_28_29,
                Migrations.MIGRATION_29_30,
                Migrations.MIGRATION_30_31,
                Migrations.MIGRATION_31_32,
                Migrations.MIGRATION_32_33,
                Migrations.MIGRATION_33_34,
                Migrations.MIGRATION_34_35
            )
            .build()
        testHelper.closeWhenFinished(database)
        return database
    }
}
