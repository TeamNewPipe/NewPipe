package org.schabi.newpipe.local.feed.archive

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

@RunWith(AndroidJUnit4::class)
class FeedArchiveBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profile = ProfileManager.DEFAULT_PROFILE_ID

    @Test
    fun fullBackupRestoresProfileFeedMetadataAndOriginalSavedTime() {
        val previousMode = FeedArchiveSettings.mode(context, profile)
        val previousRows = FeedArchiveBackup.currentRows(context)
        val fakeMainDatabase = File.createTempFile("backup-source-", ".db", context.cacheDir)
        val zip = File.createTempFile("feed-archive-backup-", ".zip", context.cacheDir)
        val backup = StoredFileHelper(context, null, Uri.fromFile(zip), "test")
        val item = StreamInfoItem(
            0,
            "https://example.org/watch?v=feed-archive-roundtrip",
            "Old feed video",
            StreamType.VIDEO_STREAM
        ).apply { uploaderName = "Original channel" }

        try {
            FeedArchiveSettings.set(context, profile, 1)
            withFeedArchive(context) { archive ->
                archive.clear(profile)
                archive.record(profile, listOf(item))
            }
            val original = FeedArchiveBackup.currentRows(context)
                .single { it.profileId == profile && it.url == item.url }
            fakeMainDatabase.writeText("Main database fixture is irrelevant to archive serialization")
            ImportExportManager(BackupFileLocator(context)).exportDatabase(
                PreferenceManager.getDefaultSharedPreferences(context),
                backup,
                fakeMainDatabase.toPath(),
                null,
                context
            )

            assertTrue(ZipHelper.zipContainsFile(backup, BackupFileLocator.FILE_NAME_FEED_ARCHIVE))
            val imported = FeedArchiveBackup.read(backup)
            assertTrue(imported.any { it.profileId == profile && it.url == item.url })
            withFeedArchive(context) { it.clear(profile) }
            FeedArchiveBackup.restore(context, imported)

            val actual = FeedArchiveBackup.currentRows(context)
                .single { it.profileId == profile && it.url == item.url }
            assertEquals(original.savedAt, actual.savedAt)
            assertEquals(original.title, actual.title)
            assertEquals(original.uploader, actual.uploader)
        } finally {
            restoreOriginalMode(previousMode)
            FeedArchiveBackup.restore(context, previousRows)
            fakeMainDatabase.delete()
            zip.delete()
        }
    }

    @Test
    fun invalidOrUnknownArchiveDataIsRejectedWithoutChangingExistingRows() {
        val invalid = File.createTempFile("invalid-feed-archive-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(invalid.outputStream()).use { out ->
                out.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_FEED_ARCHIVE))
                out.write(
                    """[{"profile":"bad/profile","service":0,"url":"file:///etc/passwd","title":"","uploader":"","savedAt":42}]"""
                        .toByteArray(Charsets.UTF_8)
                )
                out.closeEntry()
            }
            val backup = StoredFileHelper(context, null, Uri.fromFile(invalid), "test")
            val result = runCatching { FeedArchiveBackup.read(backup) }
            assertTrue(result.exceptionOrNull() is IOException)
        } finally {
            invalid.delete()
        }
    }

    private fun restoreOriginalMode(previous: FeedArchiveSettings.Mode?) {
        val setting = (0..3).first { FeedArchiveSettings.option(it) == previous }
        FeedArchiveSettings.set(context, profile, setting)
    }
}
