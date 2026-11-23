/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

class OfflinePlaybackMetadataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun coldReadsAndFileProviderAliasesRetainPreciseChaptersAfterRename() {
        OfflineDownloadFixture(context).use { fixture ->
            val first = OfflinePlaybackMetadata.resolve(context, fixture.uri())
            assertEquals(listOf(0L, 1250L, 5000L), first.chapters.map(ChapterTimes::startMillis))
            val alias = FileProvider.getUriForFile(context, "${context.packageName}.provider", fixture.video).toString()
            val aliased = OfflinePlaybackMetadata.resolve(context, alias)
            assertEquals(first.identity, aliased.identity)
            val renamed = File(fixture.directory, "renamed.mp4")
            assertTrue(fixture.video.renameTo(renamed))
            val local = OfflinePlaybackMetadata.resolve(context, Uri.fromFile(renamed).toString(), knownMissions = fixture.missions)
            assertEquals(first.identity, local.identity)
            assertEquals(listOf("Introduction", "First lesson", "Second lesson"), local.chapters.map { it.title })
            assertEquals(fixture.source, local.sourceInfo!!.url)
            assertEquals(8000L, local.durationMillis)
            assertEquals(fixture.uri(fixture.caption), local.subtitles.single().content)
            val item = PlayQueueItem.localMedia("Renamed", Uri.fromFile(renamed).toString(), 8, null, null, null, "video/mp4", -1, true, null)
            val tag = LocalMediaItemTag.of(item, local.chapters).withExtras("preserved")
            assertEquals(1250L, ChapterTimes.startMillis(tag.streamSegments[1]))
            assertFalse(tag.maybeStreamInfo.isPresent)
            OfflinePlaybackMetadata.remove(context, alias)
            OfflinePlaybackMetadata.remove(context, item.url)
        }
    }

    @Test fun replacingOrEditingMediaRejectsOldChaptersCaptionsAndSourceEvenWithMissionFallback() {
        OfflineDownloadFixture(context).use { fixture ->
            val originalSize = fixture.video.length()
            val bytes = fixture.video.readBytes()
            bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
            fixture.video.writeBytes(bytes)
            assertEquals(originalSize, fixture.video.length())
            val changed = OfflinePlaybackMetadata.resolve(context, fixture.uri(), fixture.videoMission, fixture.missions)
            assertTrue(changed.chapters.isEmpty())
            assertTrue(changed.subtitles.isEmpty())
            assertNull(changed.sourceInfo)
            fixture.save(fixture.video, 'v')
            val replacement = File(fixture.directory, "replacement.mp4").apply { writeBytes(bytes) }
            assertTrue(fixture.video.delete())
            assertTrue(replacement.renameTo(fixture.video))
            val replaced = OfflinePlaybackMetadata.resolve(context, fixture.uri(), fixture.videoMission, fixture.missions)
            assertTrue(replaced.chapters.isEmpty())
            assertNull(replaced.sourceInfo)
        }
    }

    @Test fun captionsRequireMatchingSourceAndUnchangedReadableFiles() {
        OfflineDownloadFixture(context).use { fixture ->
            fun resolve() = OfflinePlaybackMetadata.resolve(context, fixture.uri(), fixture.videoMission, fixture.missions)
            val original = resolve()
            assertEquals("en", original.subtitles.single().languageTag)
            assertNotNull(original.captionIdentities[fixture.uri(fixture.caption)])
            fixture.save(fixture.caption, 's', OfflinePlaybackMetadata.capture(context, fixture.info, 's', "https://www.youtube.com/watch?v=aaaaaaaaaaa", null)!!)
            assertTrue(resolve().subtitles.isEmpty())
            fixture.save(fixture.caption, 's')
            val bytes = fixture.caption.readBytes().apply { this[lastIndex] = 'x'.code.toByte() }
            fixture.caption.writeBytes(bytes)
            assertTrue(resolve().subtitles.isEmpty())
            fixture.save(fixture.caption, 's')
            val renamed = File(fixture.directory, "unrelated-name.vtt")
            assertTrue(fixture.caption.renameTo(renamed))
            assertTrue(resolve().subtitles.isEmpty())
            assertTrue(renamed.delete())
            assertTrue(resolve().subtitles.isEmpty())
        }
    }

    @Test fun selectedExportsPersistTheirOwnTimelineWithoutFullVideoCaptions() {
        OfflineDownloadFixture(context).use { fixture ->
            val ranges = listOf(DownloadSegment(500, 2500), DownloadSegment(4500, 7500))
            val source = fixture.source + "#wizestream-segments=" + DownloadSegments.encode(ranges)
            val captured = OfflinePlaybackMetadata.capture(context, fixture.info, 'v', source, DownloadSegments.encode(ranges))!!
            val bytes = ByteArrayOutputStream().apply { ObjectOutputStream(this).use { it.writeObject(captured) } }.toByteArray()
            val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as OfflineDownloadMetadata }
            fixture.save(fixture.video, 'v', restored)
            val resolved = OfflinePlaybackMetadata.resolve(context, fixture.uri(), fixture.videoMission, fixture.missions)
            assertEquals(listOf(0L, 750L, 2000L, 2500L), resolved.chapters.map(ChapterTimes::startMillis))
            assertEquals(5000L, resolved.durationMillis)
            assertTrue(resolved.subtitles.isEmpty())
            assertEquals(source, resolved.sourceInfo!!.url)
        }
    }

    @Test fun preferredDownloadedCopiesUseTheSameSavedChaptersAndRejectReplacedFiles() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val old = preferences.all[DownloadedCopyRepository.PREFERENCE] as? Boolean
        preferences.edit().putBoolean(DownloadedCopyRepository.PREFERENCE, true).commit()
        try {
            OfflineDownloadFixture(context).use { fixture ->
                fixture.register()
                assertEquals(fixture.source, DownloadPlayback.find(context, fixture.uri())?.source)
                val copy = checkNotNull(DownloadedCopyRepository.find(context, 0, fixture.source, "Remote title", false))
                assertEquals(listOf(0L, 1250L, 5000L), copy.streamSegments.map(ChapterTimes::startMillis))
                assertEquals(fixture.uri(fixture.caption), copy.subtitles.single().content)
                assertEquals(OfflinePlaybackMetadata.resolve(context, fixture.uri()).identity, copy.localIdentity)
                fixture.video.appendBytes(byteArrayOf(1))
                assertNull(DownloadedCopyRepository.find(context, 0, fixture.source, "Remote title", false))
            }
        } finally {
            preferences.edit().apply { if (old == null) remove(DownloadedCopyRepository.PREFERENCE) else putBoolean(DownloadedCopyRepository.PREFERENCE, old) }.commit()
        }
    }
}
