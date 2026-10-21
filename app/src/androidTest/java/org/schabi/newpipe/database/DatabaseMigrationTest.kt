package org.schabi.newpipe.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
        file = File.createTempFile("test", ".db")
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

        val migratedConnection = testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_3,
            migrations = listOf(Migrations.MIGRATION_2_3)
        )

        // Only expect 2, the one with the null url will be ignored
        migratedConnection.prepare("SELECT (COUNT(*) = 2) FROM streams").use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        // Verify first stream exists
        migratedConnection.prepare(
            """
            SELECT (COUNT(*) = 1) FROM streams
            WHERE
                service_id = $DEFAULT_SERVICE_ID AND
                url = '$DEFAULT_URL' AND
                title = '$DEFAULT_TITLE' AND
                stream_type = '${DEFAULT_TYPE.name}' AND
                duration = $DEFAULT_DURATION AND
                uploader = '$DEFAULT_UPLOADER_NAME' AND
                thumbnail_url = '$DEFAULT_THUMBNAIL' AND
                view_count IS NULL AND
                textual_upload_date IS NULL AND
                upload_date IS NULL AND
                is_upload_date_approximation IS NULL
            """.trimIndent()
        ).use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        // Verify second stream exists
        migratedConnection.prepare(
            """
            SELECT (COUNT(*) = 1) FROM streams
            WHERE
                service_id = $DEFAULT_SECOND_SERVICE_ID AND
                url = '$DEFAULT_SECOND_URL' AND
                title = '' AND
                stream_type = '${StreamType.VIDEO_STREAM.name}' AND
                duration = 0 AND
                uploader = '' AND
                thumbnail_url = '' AND
                view_count IS NULL AND
                textual_upload_date IS NULL AND
                upload_date IS NULL AND
                is_upload_date_approximation IS NULL
            """.trimIndent()
        ).use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        migratedConnection.close()
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

        val migratedConnection = testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_8,
            migrations = listOf(Migrations.MIGRATION_7_8)
        )

        migratedConnection.prepare(
            """
            SELECT (COUNT(*) = 2) FROM search_history
            WHERE
                search = 'abc' AND
                service_id IN (
                    SELECT service_id FROM search_history
                    WHERE search = 'abc'
                    GROUP BY service_id
                    HAVING COUNT(*) = 1
                )
            """.trimIndent()
        ).use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        migratedConnection.close()
    }

    @Test
    fun migrateDatabaseFrom8to9() = runTest {
        val databaseInV8 = testHelper.createDatabase(version = Migrations.DB_VER_8)

        val lastPlaylistUIDSQL = "SELECT MAX(uid) FROM playlists"
        val playlistsSQL = """
            INSERT OR FAIL INTO playlists
            (name, is_thumbnail_permanent, thumbnail_stream_id)
            VALUES (?, ?, ?)
        """.trimIndent()

        val lastRemotePlaylistUIDSQL = "SELECT MAX(uid) FROM remote_playlists"
        val remotePlaylistsSQL = """
            INSERT OR FAIL INTO remote_playlists
            (service_id, url)
            VALUES (?, ?)
        """.trimIndent()

        val localUid1: Long
        val localUid2: Long
        val remoteUid1: Long
        val remoteUid2: Long

        databaseInV8.run {
            // Insert first local playlist
            prepare(playlistsSQL).use { statement ->
                statement.bindText(1, DEFAULT_NAME + "1")
                statement.bindBoolean(2, false)
                statement.bindLong(3, -1)
                statement.step()
            }

            localUid1 = prepare(lastPlaylistUIDSQL).use { statement ->
                statement.step()
                statement.getLong(0)
            }

            // Insert second local playlist
            prepare(playlistsSQL).use { statement ->
                statement.bindText(1, DEFAULT_NAME + "2")
                statement.bindBoolean(2, false)
                statement.bindLong(3, -1)
                statement.step()
            }

            localUid2 = prepare(lastPlaylistUIDSQL).use { statement ->
                statement.step()
                statement.getLong(0)
            }

            execSQL("DELETE FROM playlists WHERE uid = $localUid1")

            // Insert first remote playlist
            prepare(remotePlaylistsSQL).use { statement ->
                statement.bindInt(1, DEFAULT_SERVICE_ID)
                statement.bindText(2, DEFAULT_URL)
                statement.step()
            }

            remoteUid1 = prepare(lastRemotePlaylistUIDSQL).use { statement ->
                statement.step()
                statement.getLong(0)
            }

            // Insert second remote playlist
            prepare(remotePlaylistsSQL).use { statement ->
                statement.bindInt(1, DEFAULT_SECOND_SERVICE_ID)
                statement.bindText(2, DEFAULT_SECOND_URL)
                statement.step()
            }

            remoteUid2 = prepare(lastRemotePlaylistUIDSQL).use { statement ->
                statement.step()
                statement.getLong(0)
            }

            execSQL("DELETE FROM remote_playlists WHERE uid = $remoteUid2")

            close()
        }

        val migratedConnection = testHelper.runMigrationsAndValidate(
            version = Migrations.DB_VER_9,
            migrations = listOf(Migrations.MIGRATION_8_9)
        )

        // Verify we have only one local playlist
        migratedConnection.prepare(
            """
            SELECT (COUNT(*) = 1) FROM playlists
            WHERE uid = $localUid2 AND display_index = -1
            """.trimIndent()
        ).use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        // Verify we have only one remote playlist
        migratedConnection.prepare(
            """
            SELECT (COUNT(*) = 1) FROM remote_playlists
            WHERE uid = $remoteUid1 AND display_index = -1
            """.trimIndent()
        ).use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        // Insert third local playlist
        val newPlaylistsSQL = """
            INSERT OR FAIL INTO playlists
            (name, is_thumbnail_permanent, thumbnail_stream_id, display_index)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        migratedConnection.prepare(newPlaylistsSQL).use { statement ->
            statement.bindText(1, DEFAULT_NAME + "3")
            statement.bindBoolean(2, false)
            statement.bindLong(3, -1)
            statement.bindLong(4, -1)
            statement.step()
        }

        val localUid3 = migratedConnection.prepare(lastPlaylistUIDSQL).use { statement ->
            statement.step()
            statement.getLong(0)
        }

        // Insert third remote playlist
        val newRemotePlaylistsSQL = """
            INSERT OR FAIL INTO remote_playlists
            (service_id, name, url, thumbnail_url, uploader, display_index, stream_count)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        migratedConnection.prepare(newRemotePlaylistsSQL).use { statement ->
            statement.bindInt(1, DEFAULT_THIRD_SERVICE_ID)
            statement.bindText(2, DEFAULT_NAME)
            statement.bindText(3, DEFAULT_THIRD_URL)
            statement.bindText(4, DEFAULT_THUMBNAIL)
            statement.bindText(5, DEFAULT_UPLOADER_NAME)
            statement.bindLong(6, -1)
            statement.bindLong(7, 10)
            statement.step()
        }

        val remoteUid3 = migratedConnection.prepare(lastRemotePlaylistUIDSQL).use { statement ->
            statement.step()
            statement.getLong(0)
        }

        // Verify we have 2 local playlists now with third one present
        migratedConnection.prepare("SELECT (COUNT(*) = 2) FROM playlists").use { statement ->
            statement.step()
            require(statement.getBoolean(0))
        }

        migratedConnection.prepare(
            """
            SELECT (COUNT(*) = 1) FROM playlists
            WHERE uid = $localUid3 AND display_index = -1
            """.trimIndent()
        ).use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        // Verify we have 2 remote playlists now with third one present
        migratedConnection.prepare("SELECT (COUNT(*) = 2) FROM remote_playlists").use { statement ->
            statement.step()
            require(statement.getBoolean(0))
        }

        migratedConnection.prepare(
            """
            SELECT (COUNT(*) = 1) FROM remote_playlists
            WHERE uid = $remoteUid3 AND display_index = -1
            """.trimIndent()
        ).use { statement ->
            statement.step()
            assert(statement.getBoolean(0))
        }

        migratedConnection.close()
    }
}
