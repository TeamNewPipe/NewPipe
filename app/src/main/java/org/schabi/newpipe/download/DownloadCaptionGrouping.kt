package org.schabi.newpipe.download

import java.util.Locale
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission

/** Associates companion captions by exact video basename and source, never by title alone. */
object DownloadCaptionGrouping {
    private val formats = setOf("srt", "vtt", "ttml")

    private fun language(videoFilename: String, subtitleFilename: String): String? {
        val root = videoFilename.substringBeforeLast('.', videoFilename)
        val prefix = "$root."
        if (!subtitleFilename.startsWith(prefix, ignoreCase = true)) return null
        val remainder = subtitleFilename.substring(prefix.length)
        val format = remainder.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (format !in formats) return null
        val lang = remainder.substringBeforeLast('.')
        return lang.takeIf { it.matches(Regex("[A-Za-z0-9_-]{2,24}")) }
    }

    @JvmStatic
    fun associated(video: FinishedMission, finished: List<FinishedMission>): List<Pair<FinishedMission, String>> {
        if (video.kind != 'v') return emptyList()
        return finished.asSequence()
            .filter { it.kind == 's' && it.source == video.source && it.syncId != video.syncId }
            .mapNotNull { caption ->
                language(video.displayName.orEmpty(), caption.displayName.orEmpty())
                    ?.let { caption to it }
            }
            .take(12)
            .toList()
    }

    @JvmStatic
    fun captionCounts(finished: List<FinishedMission>): Map<String, Int> {
        val bySource = finished.groupBy { it.source }
        return finished.filter { it.kind == 'v' && !it.syncId.isNullOrBlank() }
            .associate { video -> video.syncId to associated(video, bySource[video.source].orEmpty()).size }
    }

    @JvmStatic
    fun displayName(mission: Mission, title: String): String {
        if (mission.kind != 's') return title
        val name = mission.storage?.name.orEmpty()
        val suffix = name.substringAfterLast('.', "").uppercase(Locale.ROOT)
        return if (suffix in setOf("SRT", "VTT", "TTML")) "$suffix • $title" else title
    }
}
