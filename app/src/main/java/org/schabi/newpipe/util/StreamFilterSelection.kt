package org.schabi.newpipe.util

import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.download.DownloadedAvailabilityIndex
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** OR within each row; AND between content type and watch status. */
class StreamFilterSelection private constructor(val filters: Set<StreamListFilter>) {
    val contentFilters = filters.filterTo(linkedSetOf()) { it in CONTENT_FILTERS }
    val downloadedOnly = StreamListFilter.DOWNLOADED in filters
    val watchFilters = filters - contentFilters - StreamListFilter.DOWNLOADED
    val isActive: Boolean get() = filters.isNotEmpty()
    fun matchesDownloaded(available: Boolean): Boolean = !downloadedOnly || available
    val needsStreamStates: Boolean get() = watchFilters.isNotEmpty()

    fun matches(stream: StreamInfoItem, state: StreamStateEntity?): Boolean {
        return matches(stream, state, includeAudio = StreamListFilter.AUDIO in contentFilters)
    }

    private fun matches(
        stream: StreamInfoItem,
        state: StreamStateEntity?,
        includeAudio: Boolean
    ): Boolean {
        val matchesContent = contentFilters.isEmpty() ||
            StreamListFilter.categoryOf(stream, includeAudio = includeAudio) in contentFilters
        val matchesWatch = watchFilters.isEmpty() || watchFilters.any { StreamListFilter.matches(it, stream, state) }
        return matchesContent && matchesWatch &&
            (!downloadedOnly || matchesDownloaded(DownloadedAvailabilityIndex.isAvailable(stream.serviceId, stream.url)))
    }

    fun matchesVisible(
        stream: StreamInfoItem,
        state: StreamStateEntity?,
        visibleCategories: Set<StreamListFilter>,
        showWatched: Boolean,
        showPartiallyWatched: Boolean
    ): Boolean {
        if (contentFilters.isEmpty() && StreamListFilter.categoryOf(stream) !in visibleCategories) return false
        if (watchFilters.isEmpty()) {
            val finished = state?.isFinished(stream.duration) == true
            val partial = state?.isValid(stream.duration) == true && !finished
            if (finished && !showWatched || partial && !showPartiallyWatched) return false
        }
        return matches(stream, state)
    }

    fun matches(entry: StreamStatisticsEntry): Boolean = matches(
        entry.toStreamInfoItem(),
        StreamStateEntity(entry.streamId, entry.progressMillis),
        includeAudio = true
    )

    fun encode(): String = StreamListFilter.entries.filter { it in filters }.joinToString(",") { it.name }

    companion object {
        val CONTENT_FILTERS = setOf(
            StreamListFilter.LIVE,
            StreamListFilter.VIDEOS,
            StreamListFilter.AUDIO,
            StreamListFilter.SHORTS,
            StreamListFilter.UPCOMING
        )

        @JvmStatic
        fun parse(value: String?): StreamFilterSelection = StreamFilterSelection(
            value.orEmpty().split(",").mapNotNull { name ->
                StreamListFilter.entries.firstOrNull { it.name == name && it != StreamListFilter.NONE }
            }.toSet()
        )
    }
}
