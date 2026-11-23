/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamSegment

class OfflineChapterTest {
    @Test fun normalizesInvalidDuplicateAndOutOfOrderChaptersWithinKnownDuration() {
        val actual = ChapterTimes.normalize(
            listOf(
                OfflineChapter("Second", 1500),
                OfflineChapter(" First ", 0),
                OfflineChapter("Duplicate", 1500),
                OfflineChapter("Before", -1),
                OfflineChapter("End", 4000),
                OfflineChapter(" ", 3000)
            ),
            4000
        )
        assertEquals(listOf(OfflineChapter("First", 0), OfflineChapter("Second", 1500)), actual)
        assertEquals(listOf(OfflineChapter("Later", 5000)), ChapterTimes.normalize(listOf(OfflineChapter("Later", 5000)), 0))
    }

    @Test fun stitchedExportsRemapChapterBoundariesAndLabelCutsInsideAChapter() {
        val chapters = listOf(OfflineChapter("Intro", 0), OfflineChapter("Lesson", 10_000), OfflineChapter("End", 30_000))
        val selected = DownloadSegments.normalize(listOf(DownloadSegment(5_000, 15_000), DownloadSegment(25_000, 35_000)), 40_000)
        assertEquals(
            listOf(
                OfflineChapter("Intro", 0),
                OfflineChapter("Lesson", 5000),
                OfflineChapter("Lesson", 10_000),
                OfflineChapter("End", 15_000)
            ),
            ChapterTimes.remap(chapters, selected) { "Part $it" }
        )
    }

    @Test fun unchapteredRangesStillHaveNavigableOutputBoundaries() {
        val ranges = listOf(DownloadSegment(1000, 2000), DownloadSegment(5000, 7500))
        assertEquals(listOf(OfflineChapter("Part 1", 0), OfflineChapter("Part 2", 1000)), ChapterTimes.remap(emptyList(), ranges) { "Part $it" })
    }

    @Test fun segmentStartsAtExcludedRangeEndAreNotCopiedIntoTheEarlierExport() {
        val chapters = listOf(OfflineChapter("First", 0), OfflineChapter("Excluded", 1000), OfflineChapter("Last", 2000))
        assertEquals(listOf(OfflineChapter("First", 0), OfflineChapter("Last", 1000)), ChapterTimes.remap(chapters, listOf(DownloadSegment(0, 1000), DownloadSegment(2000, 3000))) { "Part $it" })
    }

    @Test fun precisionSurvivesLocalTagSerializationAndMixedLegacySegments() {
        val segment = PreciseStreamSegment("Subsecond", 1750)
        val bytes = ByteArrayOutputStream().apply { ObjectOutputStream(this).use { it.writeObject(segment) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as StreamSegment }
        assertEquals(1750L, ChapterTimes.startMillis(restored))
        assertEquals(2000L, ChapterTimes.startMillis(StreamSegment("Legacy", 2)))
    }

    @Test fun chapterNavigationUsesMillisecondsAndStopsAtBothEnds() {
        val chapters = listOf(PreciseStreamSegment("One", 0), PreciseStreamSegment("Two", 1500), PreciseStreamSegment("Three", 3250))
        assertNull(ChapterTimes.previous(chapters, 0))
        assertEquals(1500L, ChapterTimes.next(chapters, 0))
        assertEquals(0L, ChapterTimes.previous(chapters, 1500))
        assertEquals(1500L, ChapterTimes.previous(chapters, 1800))
        assertEquals(3250L, ChapterTimes.next(chapters, 1500))
        assertNull(ChapterTimes.next(chapters, 3250))
    }

    @Test fun boundsChapterCountAndTitleSize() {
        val chapters = (0L..1500).map { OfflineChapter("x".repeat(1000), it) }
        val normalized = ChapterTimes.normalize(chapters, 0)
        assertEquals(1000, normalized.size)
        assertEquals(512, normalized.last().title.length)
    }
}
