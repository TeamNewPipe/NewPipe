/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.get.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.streams.io.StoredFileHelper

@RunWith(AndroidJUnit4::class)
class FinishedMissionStoreMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFromVersion4AddsDownloadMetadata() {
        context.openOrCreateDatabase(
            DATABASE_NAME,
            Context.MODE_PRIVATE,
            null
        ).use { database ->
            database.execSQL(
                """
                CREATE TABLE finished_missions (
                    path TEXT NOT NULL,
                    url TEXT NOT NULL,
                    bytes_downloaded INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    UNIQUE(timestamp, path)
                )
                """.trimIndent()
            )
            database.insertOrThrow(
                "finished_missions",
                null,
                ContentValues().apply {
                    put("path", LOCAL_PATH)
                    put("url", SOURCE_URL)
                    put("bytes_downloaded", DOWNLOAD_SIZE)
                    put("timestamp", COMPLETED_AT)
                    put("kind", "v")
                }
            )
            database.version = 4
        }

        val store = FinishedMissionStore(context)
        try {
            val database = store.writableDatabase
            assertEquals(6, database.version)
            database.query(
                "finished_missions",
                arrayOf(
                    "sync_id",
                    "display_name",
                    "mime_type",
                    "title",
                    "uploader",
                    "description",
                    "upload_date",
                    "duration",
                    "tags",
                    "thumbnail_url"
                ),
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(StoredFileHelper.DEFAULT_MIME, cursor.getString(2))
                assertEquals("", cursor.getString(3))
                assertEquals("", cursor.getString(4))
                assertEquals("", cursor.getString(5))
                assertEquals("", cursor.getString(6))
                assertEquals(-1L, cursor.getLong(7))
                assertEquals("", cursor.getString(8))
                assertEquals("", cursor.getString(9))
            }

            val metadata = store.loadCompletedDownloadMetadata().single()

            assertEquals(SOURCE_URL, metadata.source)
            assertEquals(DOWNLOAD_SIZE, metadata.length)
            assertEquals(COMPLETED_AT, metadata.timestamp)
            assertEquals('v', metadata.kind)
            assertEquals("finished-video.mp4", metadata.displayName)
            assertEquals(StoredFileHelper.DEFAULT_MIME, metadata.mimeType)
            assertTrue(metadata.storage.isInvalid)
            assertFalse(metadata.syncId.isNullOrBlank())
            assertEquals(metadata.syncId, UUID.fromString(metadata.syncId).toString())
        } finally {
            store.close()
        }
    }

    @Test
    fun migrationFromVersion3CreatesCurrentColumnsOnlyOnce() {
        context.openOrCreateDatabase(
            DATABASE_NAME,
            Context.MODE_PRIVATE,
            null
        ).use { database ->
            database.execSQL(
                """
                CREATE TABLE download_missions (
                    url TEXT NOT NULL,
                    bytes_downloaded INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    location TEXT NOT NULL,
                    name TEXT NOT NULL,
                    kind TEXT
                )
                """.trimIndent()
            )
            database.insertOrThrow(
                "download_missions",
                null,
                ContentValues().apply {
                    put("url", SOURCE_URL)
                    put("bytes_downloaded", DOWNLOAD_SIZE)
                    put("timestamp", COMPLETED_AT)
                    put("location", "/private/downloads")
                    put("name", "legacy-video.mp4")
                    put("kind", "v")
                }
            )
            database.version = 3
        }

        val store = FinishedMissionStore(context)
        try {
            assertEquals(6, store.writableDatabase.version)

            val metadata = store.loadCompletedDownloadMetadata().single()
            assertEquals(SOURCE_URL, metadata.source)
            assertEquals("legacy-video.mp4", metadata.displayName)
            assertFalse(metadata.syncId.isNullOrBlank())
            assertTrue(metadata.storage.isInvalid)
        } finally {
            store.close()
        }
    }

    companion object {
        private const val DATABASE_NAME = "downloads.db"
        private const val LOCAL_PATH =
            "content://downloads/private/folder/finished-video.mp4"
        private const val SOURCE_URL = "https://example.com/watch/download"
        private const val DOWNLOAD_SIZE = 4_096L
        private const val COMPLETED_AT = 1_000L
    }
}
