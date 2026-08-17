package org.schabi.newpipe.player.mediaitem

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * This [MediaItemTag] object contains metadata for a resolved stream
 * that is ready for playback. This object guarantees the [StreamInfo]
 * is available and may provide the [MediaItemTag.Quality] of video stream used in
 * the [androidx.media3.common.MediaItem].
 */
class StreamInfoTag private constructor(
    private val streamInfo: StreamInfo,
    private val quality: MediaItemTag.Quality? = null,
    private val audioTrack: MediaItemTag.AudioTrack? = null,
    private val extras: Any? = null
) : MediaItemTag {

    override val errors: List<Exception> = emptyList()

    override val serviceId: Int = streamInfo.serviceId

    override val title: String? = streamInfo.name

    override val uploaderName: String? = streamInfo.uploaderName

    override val durationSeconds: Long = streamInfo.duration

    override val streamUrl: String = streamInfo.url ?: ""

    override val thumbnailUrl: String? = ImageStrategy.choosePreferredImage(streamInfo.thumbnails)

    override val uploaderUrl: String? = streamInfo.uploaderUrl

    override val streamType: StreamType? = streamInfo.streamType

    override val maybeStreamInfo: StreamInfo = streamInfo

    override val maybeQuality: MediaItemTag.Quality? = quality

    override val maybeAudioTrack: MediaItemTag.AudioTrack? = audioTrack

    override fun <T : Any> getMaybeExtras(type: Class<T>): T? {
        return type.cast(extras)
    }

    override fun <T : Any> withExtras(extra: T): StreamInfoTag {
        return StreamInfoTag(streamInfo, quality, audioTrack, extra)
    }

    companion object {
        @JvmStatic
        fun of(
            streamInfo: StreamInfo,
            sortedVideoStreams: List<VideoStream>,
            selectedVideoStreamIndex: Int,
            audioStreams: List<AudioStream>,
            selectedAudioStreamIndex: Int
        ): StreamInfoTag {
            val quality = MediaItemTag.Quality.of(sortedVideoStreams, selectedVideoStreamIndex)
            val audioTrack = MediaItemTag.AudioTrack.of(audioStreams, selectedAudioStreamIndex)
            return StreamInfoTag(streamInfo, quality, audioTrack)
        }

        @JvmStatic
        fun of(
            streamInfo: StreamInfo,
            audioStreams: List<AudioStream>,
            selectedAudioStreamIndex: Int
        ): StreamInfoTag {
            val audioTrack = MediaItemTag.AudioTrack.of(audioStreams, selectedAudioStreamIndex)
            return StreamInfoTag(streamInfo, audioTrack = audioTrack)
        }

        @JvmStatic
        fun of(streamInfo: StreamInfo): StreamInfoTag {
            return StreamInfoTag(streamInfo)
        }
    }
}
