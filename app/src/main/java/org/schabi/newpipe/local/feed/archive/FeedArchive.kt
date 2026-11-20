package org.schabi.newpipe.local.feed.archive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.preference.PreferenceManager
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * An opt-in, compact feed catalog kept separately from Room's active feed and its in-memory limit.
 * No playback history, cookies, authentication, or user behavior is stored.
 */
class FeedArchive(private val context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    1
) {
    data class Row(
        val serviceId: Int,
        val url: String,
        val title: String,
        val uploader: String,
        val savedAt: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE feed_archive (
                profile_id TEXT NOT NULL,
                service_id INTEGER NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                uploader TEXT NOT NULL,
                saved_at INTEGER NOT NULL,
                PRIMARY KEY (profile_id, service_id, url)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX feed_archive_recent ON feed_archive(profile_id, saved_at DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun record(profile: String, items: List<StreamInfoItem>) {
        val mode = FeedArchiveSettings.mode(context, profile) ?: return
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            items.take(600).forEach { item ->
                if (item.url.isBlank() || item.url.length > 2048) return@forEach
                val values = ContentValues().apply {
                    put("profile_id", profile)
                    put("service_id", item.serviceId)
                    put("url", item.url)
                    put("title", item.name.orEmpty().take(250))
                    put("uploader", item.uploaderName.orEmpty().take(140))
                    put("saved_at", now)
                }
                writableDatabase.insertWithOnConflict(
                    "feed_archive",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            pruneInternal(profile, mode, now)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun page(profile: String, page: Int, pageSize: Int = 50): List<Row> {
        val safeLimit = pageSize.coerceIn(1, 100)
        val offset = page.coerceAtLeast(0).coerceAtMost(100_000) * safeLimit
        return readableDatabase.query(
            "feed_archive",
            arrayOf("service_id", "url", "title", "uploader", "saved_at"),
            "profile_id = ?",
            arrayOf(profile),
            null,
            null,
            "saved_at DESC, rowid DESC",
            "$offset, $safeLimit"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Row(
                            cursor.getInt(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getLong(4)
                        )
                    )
                }
            }
        }
    }

    data class BackupRow(
        val profileId: String,
        val serviceId: Int,
        val url: String,
        val title: String,
        val uploader: String,
        val savedAt: Long
    )

    fun backupRows(): List<BackupRow> {
        return readableDatabase.query(
            "feed_archive",
            arrayOf("profile_id", "service_id", "url", "title", "uploader", "saved_at"),
            null,
            null,
            null,
            null,
            "profile_id, saved_at DESC",
            (MAX_BACKUP_ROWS + 1).toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (size == MAX_BACKUP_ROWS) {
                        throw java.io.IOException("Feed archive exceeds the backup row limit")
                    }
                    add(
                        BackupRow(
                            cursor.getString(0),
                            cursor.getInt(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getString(4),
                            cursor.getLong(5)
                        )
                    )
                }
            }
        }
    }

    /** Restore and prune atomically so malformed backups cannot leave half-restored rows. */
    fun replaceBackupRows(rows: List<BackupRow>, profiles: Set<String>) {
        require(rows.size <= MAX_BACKUP_ROWS)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("feed_archive", null, null)
            rows.forEach { row ->
                val values = ContentValues().apply {
                    put("profile_id", row.profileId)
                    put("service_id", row.serviceId)
                    put("url", row.url)
                    put("title", row.title)
                    put("uploader", row.uploader)
                    put("saved_at", row.savedAt)
                }
                db.insertWithOnConflict("feed_archive", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val now = System.currentTimeMillis()
            profiles.forEach { profile ->
                val mode = FeedArchiveSettings.mode(context, profile)
                if (mode == null) {
                    db.delete("feed_archive", "profile_id = ?", arrayOf(profile))
                } else {
                    pruneInternal(profile, mode, now)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear(profile: String) {
        writableDatabase.delete("feed_archive", "profile_id = ?", arrayOf(profile))
    }

    fun prune(profile: String) {
        val mode = FeedArchiveSettings.mode(context, profile)
        if (mode == null) {
            clear(profile)
        } else {
            writableDatabase.beginTransaction()
            try {
                pruneInternal(profile, mode, System.currentTimeMillis())
                writableDatabase.setTransactionSuccessful()
            } finally {
                writableDatabase.endTransaction()
            }
        }
    }

    private fun pruneInternal(profile: String, mode: FeedArchiveSettings.Mode, now: Long) {
        writableDatabase.delete(
            "feed_archive",
            "profile_id = ? AND saved_at < ?",
            arrayOf(profile, (now - mode.days * 86_400_000L).toString())
        )
        writableDatabase.execSQL(
            """
            DELETE FROM feed_archive
            WHERE profile_id = ?
              AND rowid NOT IN (
                SELECT rowid FROM feed_archive
                WHERE profile_id = ?
                ORDER BY saved_at DESC, rowid DESC
                LIMIT ?
              )
            """.trimIndent(),
            arrayOf<Any>(profile, profile, mode.maxEntries)
        )
    }

    companion object {
        internal const val DATABASE_NAME = "wize_feed_archive.db"
        internal const val MAX_BACKUP_ROWS = 50_000
    }
}

object FeedArchiveSettings {
    data class Mode(val days: Int, val maxEntries: Int)

    // Off by default; options have independently bounded age and entry-count limits.
    private val options: List<Mode?> = listOf(
        null,
        Mode(days = 30, maxEntries = 2_500),
        Mode(days = 90, maxEntries = 5_000),
        Mode(days = 180, maxEntries = 10_000)
    )

    private fun key(profile: String) = "feed_archive_mode_$profile"

    fun mode(context: Context, profile: String): Mode? {
        val setting = PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(key(profile), 0)
        return options.getOrNull(setting)
    }

    fun set(context: Context, profile: String, index: Int) {
        require(index in options.indices)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(key(profile), index)
            .apply()
    }

    internal fun option(index: Int): Mode? = options.getOrNull(index)

    internal fun forgetProfile(context: Context, profile: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(key(profile))
            .apply()
    }
}

/**
 * Remove retained feed metadata when a profile is deleted, including through direct profile
 * deletion or sync. Do not create an unused archive database just to clean up a profile.
 */
internal fun forgetProfileFeedArchive(context: Context, profile: String) {
    if (context.getDatabasePath(FeedArchive.DATABASE_NAME).exists()) {
        withFeedArchive(context) { it.clear(profile) }
    }
    FeedArchiveSettings.forgetProfile(context, profile)
}

/**
 * Closes the archive on every supported Android version, including API 23 where
 * SQLiteOpenHelper cannot be cast to AutoCloseable.
 */
internal fun <T> withFeedArchive(context: Context, block: (FeedArchive) -> T): T {
    val archive = FeedArchive(context)
    return try {
        block(archive)
    } finally {
        archive.close()
    }
}
