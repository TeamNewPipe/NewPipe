package org.schabi.newpipe.fragments.detail

import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.StreamTypeUtil

/** Resolve detail controls from playable sources, not only extractor stream-list counts. */
object StreamDetailActionPolicy {
    data class Actions(
        val showDownload: Boolean,
        val showBackground: Boolean,
        val showPopup: Boolean
    )

    @JvmStatic
    fun forStream(info: StreamInfo): Actions {
        if (info.isUpcoming) return Actions(false, false, false)

        val manifestPlayback = info.streamType in setOf(
            StreamType.LIVE_STREAM,
            StreamType.AUDIO_LIVE_STREAM,
            StreamType.POST_LIVE_STREAM
        ) && (!info.hlsUrl.isNullOrBlank() || !info.dashMpdUrl.isNullOrBlank())

        val videoPlayback = info.videoStreams.isNotEmpty() ||
            info.videoOnlyStreams.isNotEmpty() ||
            manifestPlayback && info.streamType != StreamType.AUDIO_LIVE_STREAM
        val backgroundPlayback = info.audioStreams.isNotEmpty() ||
            info.videoStreams.isNotEmpty() || manifestPlayback

        // DownloadStreamCatalogFactory only offers progressive HTTP files, not playback-only
        // HLS/DASH manifests. Avoid showing a Download button that opens an empty dialog.
        val directDownload = !StreamTypeUtil.isLiveStream(info.streamType) &&
            sequenceOf(
                info.audioStreams,
                info.videoStreams,
                info.videoOnlyStreams,
                info.subtitles
            ).flatten().any {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }

        return Actions(directDownload, backgroundPlayback, videoPlayback)
    }
}
