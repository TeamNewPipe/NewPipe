package org.schabi.newpipe.util

import androidx.annotation.IdRes
import java.time.OffsetDateTime
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.download.DownloadedAvailabilityIndex
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

enum class StreamListFilter(@IdRes val chipId: Int) {
    NONE(0),
    UNWATCHED(R.id.filter_unwatched),
    LIVE(R.id.filter_live),
    VIDEOS(R.id.filter_videos),
    AUDIO(R.id.filter_audio),
    LIVE_AUDIO(R.id.filter_live_audio),
    POST_LIVE_VIDEO(R.id.filter_post_live_video),
    POST_LIVE_AUDIO(R.id.filter_post_live_audio),
    TRACKS(R.id.filter_tracks),
    PODCASTS(R.id.filter_podcasts),
    COURSES(R.id.filter_courses),
    SHORTS(R.id.filter_shorts),
    UPCOMING(R.id.filter_upcoming),
    PARTIALLY_WATCHED(R.id.filter_partially_watched),
    WATCHED(R.id.filter_watched),
    DOWNLOADED(R.id.filter_downloaded);

    companion object {
        @JvmStatic
        fun fromChipId(@IdRes chipId: Int): StreamListFilter = entries
            .firstOrNull { it.chipId == chipId } ?: NONE

        @JvmStatic
        fun matches(
            filter: StreamListFilter,
            stream: StreamInfoItem,
            state: StreamStateEntity?
        ): Boolean = when (filter) {
            NONE -> true

            UNWATCHED -> state == null || !state.isValid(stream.duration)

            DOWNLOADED -> DownloadedAvailabilityIndex.isAvailable(stream.serviceId, stream.url)

            WATCHED -> state?.isFinished(stream.duration) == true

            LIVE -> categoryOf(stream) == LIVE

            VIDEOS -> categoryOf(stream) == VIDEOS

            AUDIO -> categoryOf(stream, includeAudio = true) == AUDIO

            TRACKS, PODCASTS, COURSES -> false

            LIVE_AUDIO -> stream.streamType == StreamType.AUDIO_LIVE_STREAM

            POST_LIVE_VIDEO -> stream.streamType == StreamType.POST_LIVE_STREAM

            POST_LIVE_AUDIO -> stream.streamType == StreamType.POST_LIVE_AUDIO_STREAM

            SHORTS -> categoryOf(stream) == SHORTS

            UPCOMING -> categoryOf(stream) == UPCOMING

            PARTIALLY_WATCHED -> state?.isValid(stream.duration) == true &&
                !state.isFinished(stream.duration)
        }

        @JvmStatic
        fun matches(
            filter: StreamListFilter,
            historyEntry: StreamStatisticsEntry
        ): Boolean = matches(
            filter,
            historyEntry.toStreamInfoItem(),
            StreamStateEntity(historyEntry.streamId, historyEntry.progressMillis)
        )

        @JvmStatic
        @JvmOverloads
        fun categoryOf(
            stream: StreamInfoItem,
            now: OffsetDateTime = OffsetDateTime.now(),
            includeAudio: Boolean = false
        ): StreamListFilter = when {
            stream.uploadDate?.offsetDateTime()?.isAfter(now) == true -> UPCOMING
            StreamTypeUtil.isLiveStream(stream.streamType) -> LIVE
            isShort(stream) -> SHORTS
            includeAudio && stream.streamType == StreamType.AUDIO_STREAM -> AUDIO
            else -> VIDEOS
        }

        private fun isShort(stream: StreamInfoItem): Boolean {
            return !StreamTypeUtil.isLiveStream(stream.streamType) &&
                (stream.isShortFormContent || stream.url.contains("/shorts/"))
        }
    }
}
