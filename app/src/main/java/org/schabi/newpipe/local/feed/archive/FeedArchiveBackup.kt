package org.schabi.newpipe.local.feed.archive

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

/** Bounded profile-scoped metadata in full backups, never blind SQLite file replacement. */
object FeedArchiveBackup {
    private const val MAX_JSON_BYTES = 24 * 1024 * 1024

    @JvmStatic
    fun currentRows(context: Context): List<FeedArchive.BackupRow> {
        if (!context.getDatabasePath(FeedArchive.DATABASE_NAME).exists()) return emptyList()
        return withFeedArchive(context) { it.backupRows() }
    }

    @JvmStatic
    fun exportIfPresent(context: Context, archive: ZipOutputStream): Boolean {
        val knownProfiles = ProfileManager.getProfiles(context).map { it.id }.toSet()
        val rows = currentRows(context).filter { it.profileId in knownProfiles }
        if (rows.isEmpty()) return false
        val json = JSONArray()
        rows.forEach { row ->
            json.put(
                JSONObject()
                    .put("profile", row.profileId)
                    .put("service", row.serviceId)
                    .put("url", row.url)
                    .put("title", row.title)
                    .put("uploader", row.uploader)
                    .put("savedAt", row.savedAt)
            )
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_JSON_BYTES) throw IOException("Feed archive exceeds backup size limit")
        archive.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_FEED_ARCHIVE))
        try {
            archive.write(bytes)
        } finally {
            archive.closeEntry()
        }
        return true
    }

    /** Legacy full backups without an archive are supported and produce an empty archive. */
    @JvmStatic
    fun read(backup: StoredFileHelper): List<FeedArchive.BackupRow> {
        var data: ByteArray? = null
        val found = ZipHelper.extractFileFromZip(
            backup,
            BackupFileLocator.FILE_NAME_FEED_ARCHIVE
        ) { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                if (output.size() + size > MAX_JSON_BYTES) {
                    throw IOException("Feed archive backup entry is too large")
                }
                output.write(buffer, 0, size)
            }
            data = output.toByteArray()
        }
        if (!found) return emptyList()
        return try {
            val list = JSONArray(checkNotNull(data).toString(Charsets.UTF_8))
            if (list.length() > FeedArchive.MAX_BACKUP_ROWS) {
                throw IOException("Feed archive backup contains too many entries")
            }
            List(list.length()) { index ->
                val entry = list.getJSONObject(index)
                FeedArchive.BackupRow(
                    entry.getString("profile"),
                    entry.getInt("service"),
                    entry.getString("url"),
                    entry.getString("title"),
                    entry.getString("uploader"),
                    entry.getLong("savedAt")
                ).also(::validate)
            }
        } catch (error: JSONException) {
            throw IOException("Invalid feed archive backup data", error)
        }
    }

    private fun validate(row: FeedArchive.BackupRow) {
        if (row.profileId.length !in 1..64 ||
            !row.profileId.all { it.isLetterOrDigit() || it == '-' } ||
            row.serviceId < 0 ||
            row.url.length !in 1..2048 ||
            !(row.url.startsWith("https://") || row.url.startsWith("http://")) ||
            row.title.length > 250 ||
            row.uploader.length > 140 ||
            row.savedAt !in 1..(System.currentTimeMillis() + 86_400_000L)
        ) {
            throw IOException("Invalid feed archive row")
        }
    }

    @JvmStatic
    fun restore(context: Context, rows: List<FeedArchive.BackupRow>) {
        require(rows.size <= FeedArchive.MAX_BACKUP_ROWS)
        val profiles = ProfileManager.getProfiles(context).map { it.id }.toSet()
        val validated = rows.filter { it.profileId in profiles }.onEach(::validate)
        if (validated.isEmpty() && !context.getDatabasePath(FeedArchive.DATABASE_NAME).exists()) {
            return
        }
        withFeedArchive(context) { archive ->
            archive.replaceBackupRows(validated, profiles)
        }
    }
}
