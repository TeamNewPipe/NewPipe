/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

class EmbeddedOfflinePlaybackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun ordinaryLocalFileAndContentAliasExposeTheirOwnChaptersWithoutSavedDownloadMetadata() {
        EmbeddedChapterFixture(context).use { fixture ->
            val original = fixture.file.readBytes()
            val file = OfflinePlaybackMetadata.resolve(context, fixture.uri, knownMissions = emptyList())
            val alias = FileProvider.getUriForFile(context, "${context.packageName}.provider", fixture.file).toString()
            fixture.rememberAlias(alias)
            val content = OfflinePlaybackMetadata.resolve(context, alias, knownMissions = emptyList())
            listOf(file, content).forEach { result ->
                assertEquals(listOf(0L, 1500L, 3500L), result.chapters.map(ChapterTimes::startMillis))
                assertEquals(listOf("Introduction", "Résumé – فصل", "Closing"), result.chapters.map { it.title })
                assertNull(result.sourceInfo)
                assertTrue(result.subtitles.isEmpty())
                assertFalse(result.staleDownload)
            }
            assertEquals(file.identity, content.identity)
            val item = PlayQueueItem.localMedia("Local video", fixture.uri, 4, null, null, null, "video/mp4", -1, true, null)
            val tag = LocalMediaItemTag.of(item, file.chapters)
            assertEquals(1500L, ChapterTimes.startMillis(tag.streamSegments[1]))
            assertFalse(tag.maybeStreamInfo.isPresent)
            assertArrayEquals(original, fixture.file.readBytes())
        }
    }

    @Test
    fun savedDownloadChaptersTakePrecedenceOverEmbeddedChapters() {
        EmbeddedChapterFixture(context).use { fixture ->
            fixture.save(listOf(OfflineChapter("Saved chapter", 1234)))
            val result = OfflinePlaybackMetadata.resolve(context, fixture.uri, knownMissions = emptyList())
            assertEquals(listOf("Saved chapter"), result.chapters.map { it.title })
            assertEquals(listOf(1234L), result.chapters.map(ChapterTimes::startMillis))
            assertEquals("Saved download title", result.sourceInfo!!.name)
            assertFalse(result.staleDownload)
        }
    }

    @Test
    fun emptySavedChaptersFallBackToEmbeddedChaptersAndHonorKnownDuration() {
        EmbeddedChapterFixture(context).use { fixture ->
            fixture.save(emptyList(), duration = 3500)
            val result = OfflinePlaybackMetadata.resolve(context, fixture.uri, knownMissions = emptyList())
            assertEquals(listOf(0L, 1500L), result.chapters.map(ChapterTimes::startMillis))
            assertEquals(result.chapters, result.sourceInfo!!.streamSegments)
            assertEquals(3500L, result.durationMillis)
            assertFalse(result.staleDownload)
        }
    }

    @Test
    fun changedFileRejectsItsOldSourceButStillReadsItsCurrentEmbeddedChapters() {
        EmbeddedChapterFixture(context).use { fixture ->
            fixture.save(listOf(OfflineChapter("Old saved chapter", 1234)))
            val bytes = fixture.file.readBytes()
            val title = "Introduction".toByteArray()
            val offset = (0..bytes.size - title.size).last { start -> title.indices.all { bytes[start + it] == title[it] } }
            bytes[offset] = 'R'.code.toByte()
            fixture.file.writeBytes(bytes)
            val result = OfflinePlaybackMetadata.resolve(context, fixture.uri, knownMissions = emptyList())
            assertEquals(listOf("Rntroduction", "Résumé – فصل", "Closing"), result.chapters.map { it.title })
            assertEquals(listOf(0L, 1500L, 3500L), result.chapters.map(ChapterTimes::startMillis))
            assertTrue(result.staleDownload)
            assertNull(result.sourceInfo)
            assertTrue(result.subtitles.isEmpty())
            assertEquals(0L, result.durationMillis)
            assertArrayEquals(bytes, fixture.file.readBytes())
        }
    }
}
