package org.schabi.newpipe.database

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamType

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
        private const val DEFAULT_THUMBNAIL = "https://example.com/example.jpg"

        private const val DEFAULT_SECOND_SERVICE_ID = 1
        private const val DEFAULT_SECOND_URL = "https://www.youtube.com/watch?v=ncQU6iBn5Fc"

        private const val DEFAULT_THIRD_SERVICE_ID = 2
        private const val DEFAULT_THIRD_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val testHelper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = AppDatabase::class,
        driver = BundledSQLiteDriver(),
        file = instrumentation.targetContext.getDatabasePath(AppDatabase.DATABASE_NAME)
    )

    @Test
    fun migrateDatabaseFrom2to3() = runTest {
        val databaseInV2 = testHelper.createDatabase(version = Migrations.DB_VER_2)

        databaseInV2.run {
            val sql = """
                INSERT OR FAIL INTO streams
                (service_id, url, title, stream_type, duration, uploader, thumbnail_url)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            prepare(sql).use { statement ->
                statement.bindInt(1, DEFAULT_SERVICE_ID)
                statement.bindText(2, DEFAULT_URL)
                statement.bindText(3, DEFAULT_TITLE)
                statement.bindText(4, DEFAULT_TYPE.name)
                statement.bindLong(5, DEFAULT_DURATION)
                statement.bindText(6, DEFAULT_UPLOADER_NAME)
                statement.bindText(7, DEFAULT_THUMBNAIL)
                statement.step()
            }

            prepare(sql).use { statement ->
                statement.bindInt(1, DEFAULT_SECOND_SERVICE_ID)
                statement.bindText(2, DEFAULT_SECOND_URL)
                statement.step()
            }

            prepare(sql).use { statement ->
                statement.bindInt(1, DEFAULT_SERVICE_ID)
                statement.step()
            }
            close()
        }

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_3,
            migrations = listOf(Migrations.MIGRATION_2_3)
        )

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_4,
            migrations = listOf(Migrations.MIGRATION_3_4)
        )

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_5,
            migrations = listOf(Migrations.MIGRATION_4_5)
        )

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_6,
            migrations = listOf(Migrations.MIGRATION_5_6)
        )

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_7,
            migrations = listOf(Migrations.MIGRATION_6_7)
        )

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_8,
            migrations = listOf(Migrations.MIGRATION_7_8)
        )

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_9,
            migrations = listOf(Migrations.MIGRATION_8_9)
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
    fun migrateDatabaseFrom7to8() = runTest {
        val databaseInV7 = testHelper.createDatabase(version = Migrations.DB_VER_7)

        val defaultSearch1 = " abc "
        val defaultSearch2 = " abc"

        val serviceId = DEFAULT_SERVICE_ID // YouTube
        // Use id different to YouTube because two searches with the same query
        // but different service are considered not equal.
        val otherServiceId = ServiceList.SoundCloud.serviceId

        databaseInV7.run {
            val sql = "INSERT OR FAIL INTO search_history (service_id, search) VALUES (?, ?)"

            prepare(sql).use { statement ->
                statement.bindInt(1, serviceId)
                statement.bindText(2, defaultSearch1)
                statement.step()
            }

            prepare(sql).use { statement ->
                statement.bindInt(1, serviceId)
                statement.bindText(2, defaultSearch2)
                statement.step()
            }

            prepare(sql).use { statement ->
                statement.bindInt(1, otherServiceId)
                statement.bindText(2, defaultSearch1)
                statement.step()
            }

            prepare(sql).use { statement ->
                statement.bindInt(1, otherServiceId)
                statement.bindText(2, defaultSearch2)
                statement.step()
            }

            close()
        }

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_8,
            migrations = listOf(Migrations.MIGRATION_7_8)
        )

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_9,
            migrations = listOf(Migrations.MIGRATION_8_9)
        )

        val migratedDatabaseV8 = getMigratedDatabase()
        val listFromDB = migratedDatabaseV8.searchHistoryDAO().getAll().blockingFirst()

        assertEquals(2, listFromDB.size)
        assertEquals("abc", listFromDB[0].search)
        assertEquals("abc", listFromDB[1].search)
        assertNotEquals(listFromDB[0].serviceId, listFromDB[1].serviceId)
    }

    @Test
    fun migrateDatabaseFrom8to9() = runTest {
        val databaseInV8 = testHelper.createDatabase(version = Migrations.DB_VER_8)

        val localUid1: Long
        val localUid2: Long
        val remoteUid1: Long
        val remoteUid2: Long

        databaseInV8.run {
            val remotePlaylistsSQL = """
                INSERT OR FAIL INTO remote_playlists
                (service_id, url)
                VALUES (?, ?)
            """.trimIndent()
            val playlistsSQL = """
                INSERT OR FAIL INTO playlists
                (name, is_thumbnail_permanent, thumbnail_stream_id)
                VALUES (?, ?, ?)
            """.trimIndent()

            prepare(playlistsSQL).use { statement ->
                statement.bindText(1, DEFAULT_NAME + "1")
                statement.bindBoolean(2, false)
                statement.bindInt(3, -1)
                statement.step()
            }

            prepare("SELECT MAX(uid) FROM playlists").use { statement ->
                statement.step()
                localUid1 = statement.getLong(0)
            }

            prepare(playlistsSQL).use { statement ->
                statement.bindText(1, DEFAULT_NAME + "2")
                statement.bindBoolean(2, false)
                statement.bindInt(3, -1)
                statement.step()
            }

            prepare("SELECT MAX(uid) FROM playlists").use { statement ->
                statement.step()
                localUid2 = statement.getLong(0)
            }

            execSQL("DELETE FROM playlists WHERE uid = $localUid1")

            prepare(remotePlaylistsSQL).use { statement ->
                statement.bindInt(1, DEFAULT_SERVICE_ID)
                statement.bindText(2, DEFAULT_URL)
                statement.step()
            }

            prepare("SELECT MAX(uid) FROM remote_playlists").use { statement ->
                statement.step()
                remoteUid1 = statement.getLong(0)
            }

            prepare(remotePlaylistsSQL).use { statement ->
                statement.bindInt(1, DEFAULT_SECOND_SERVICE_ID)
                statement.bindText(2, DEFAULT_SECOND_URL)
                statement.step()
            }

            prepare("SELECT MAX(uid) FROM remote_playlists").use { statement ->
                statement.step()
                remoteUid2 = statement.getLong(0)
            }

            execSQL("DELETE FROM remote_playlists WHERE uid = $remoteUid2")

            close()
        }

        testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_9,
            migrations = listOf(Migrations.MIGRATION_8_9)
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

    private fun getMigratedDatabase(): AppDatabase {
        val database: AppDatabase = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .build()
        testHelper.closeWhenFinished(database)
        return database
    }
}
