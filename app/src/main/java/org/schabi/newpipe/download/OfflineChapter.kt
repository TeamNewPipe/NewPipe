/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download

import java.io.Serializable
import org.schabi.newpipe.extractor.stream.StreamSegment

data class OfflineChapter(val title: String, val startMillis: Long) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** Local container chapters can have subsecond timestamps; keep them through selection and seeking. */
class PreciseStreamSegment(title: String, val startMillis: Long) : StreamSegment(title, (startMillis / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

object ChapterTimes {
    @JvmStatic fun startMillis(segment: StreamSegment): Long = (segment as? PreciseStreamSegment)?.startMillis ?: segment.startTimeSeconds * 1000L

    fun normalize(chapters: List<OfflineChapter>, durationMillis: Long): List<OfflineChapter> = chapters.take(1000)
        .filter { it.startMillis >= 0 && (durationMillis <= 0 || it.startMillis < durationMillis) && it.title.isNotBlank() }
        .map { it.copy(title = it.title.trim().take(512)) }.distinctBy { it.startMillis }.sortedBy { it.startMillis }

    /** A new chapter begins at each retained range, even when the cut starts inside a chapter. */
    internal fun remap(chapters: List<OfflineChapter>, ranges: List<DownloadSegment>, fallback: (Int) -> String): List<OfflineChapter> {
        val result = mutableListOf<OfflineChapter>()
        var offset = 0L
        ranges.forEachIndexed { index, range ->
            val active = chapters.lastOrNull { it.startMillis <= range.startMs }
            result += OfflineChapter(active?.title ?: fallback(index + 1), offset)
            chapters.filter { it.startMillis > range.startMs && it.startMillis < range.endMs }.forEach { result += it.copy(startMillis = offset + it.startMillis - range.startMs) }
            offset += range.endMs - range.startMs
        }
        return normalize(result, offset)
    }

    @JvmStatic fun next(chapters: List<StreamSegment>, currentMillis: Long): Long? = chapters.map(::startMillis).firstOrNull { it > currentMillis + 100 }

    @JvmStatic fun previous(chapters: List<StreamSegment>, currentMillis: Long): Long? = chapters.map(::startMillis).lastOrNull { it < currentMillis - 100 }
}
