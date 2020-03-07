package org.schabi.newpipe.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    companion object {
        private const val DEFAULT_SERVICE_ID = 0
        private const val DEFAULT_URL = "https://www.youtube.com/watch?v=cDphUib5iG4"
        private const val DEFAULT_TITLE = "Test Title"
        private val DEFAULT_TYPE = StreamType.VIDEO_STREAM
        private const val DEFAULT_DURATION = 480L
        private const val DEFAULT_UPLOADER_NAME = "Uploader Test"
        private const val DEFAULT_THUMBNAIL = "https://example.com/example.jpg"
    }

    @get:Rule val testHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName, FrameworkSQLiteOpenHelperFactory());

    @Test
    fun migrateDatabaseFrom2to3() {
        val databaseInV2 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_2)

        databaseInV2.run {
            insert("streams", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
                // put("uid", null)
                put("service_id", DEFAULT_SERVICE_ID)
                put("url", DEFAULT_URL)
                put("title", DEFAULT_TITLE)
                put("stream_type", DEFAULT_TYPE.name)
                put("duration", DEFAULT_DURATION)
                put("uploader", DEFAULT_UPLOADER_NAME)
                put("thumbnail_url", DEFAULT_THUMBNAIL)
            })
            close()
        }

        testHelper.runMigrationsAndValidate(AppDatabase.DATABASE_NAME, Migrations.DB_VER_3,
                true, Migrations.MIGRATION_2_3);

        val migratedDatabaseV3 = getMigratedDatabase()
        val listFromDB = migratedDatabaseV3.streamDAO().all.blockingFirst()
        assertEquals(1, listFromDB.size)

        val streamFromMigratedDatabase = listFromDB.first()
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
    }

    private fun getMigratedDatabase(): AppDatabase {
        val database: AppDatabase = Room.databaseBuilder(ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java, AppDatabase.DATABASE_NAME)
                .build()
        testHelper.closeWhenFinished(database)
        return database
    }
}