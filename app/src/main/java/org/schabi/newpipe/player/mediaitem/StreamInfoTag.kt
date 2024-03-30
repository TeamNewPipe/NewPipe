package org.schabi.newpipe.player.mediaitem

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.image.ImageStrategy
import java.util.Optional
import java.util.function.Function

/**
 * This [MediaItemTag] object contains metadata for a resolved stream
 * that is ready for playback. This object guarantees the [StreamInfo]
 * is available and may provide the [Quality] of video stream used in
 * the [MediaItem].
 */
class StreamInfoTag private constructor(private val streamInfo: StreamInfo,
                                        private val quality: MediaItemTag.Quality?,
                                        private val audioTrack: MediaItemTag.AudioTrack?,
                                        private val extras: Any?) : MediaItemTag {
    override val errors: List<Exception>
        get() {
            return emptyList()
        }
    override val serviceId: Int
        get() {
            return streamInfo.getServiceId()
        }
    override val title: String
        get() {
            return streamInfo.getName()
        }
    override val uploaderName: String
        get() {
            return streamInfo.getUploaderName()
        }
    override val durationSeconds: Long
        get() {
            return streamInfo.getDuration()
        }
    override val streamUrl: String
        get() {
            return streamInfo.getUrl()
        }
    override val thumbnailUrl: String?
        get() {
            return ImageStrategy.choosePreferredImage(streamInfo.getThumbnails())
        }
    override val uploaderUrl: String?
        get() {
            return streamInfo.getUploaderUrl()
        }
    override val streamType: StreamType
        get() {
            return streamInfo.getStreamType()
        }
    override val maybeStreamInfo: Optional<StreamInfo>
        get() {
            return Optional.of(streamInfo)
        }
    override val maybeQuality: Optional<MediaItemTag.Quality?>
        get() {
            return Optional.ofNullable(quality)
        }
    override val maybeAudioTrack: Optional<MediaItemTag.AudioTrack?>
        get() {
            return Optional.ofNullable(audioTrack)
        }

    public override fun <T> getMaybeExtras(type: Class<T>): Optional<T>? {
        return Optional.ofNullable(extras).map(Function({ obj: Any? -> type.cast(obj) }))
    }

    public override fun withExtras(extra: Any): StreamInfoTag {
        return StreamInfoTag(streamInfo, quality, audioTrack, extra)
    }

    companion object {
        fun of(streamInfo: StreamInfo,
               sortedVideoStreams: List<VideoStream?>,
               selectedVideoStreamIndex: Int,
               audioStreams: List<AudioStream?>,
               selectedAudioStreamIndex: Int): StreamInfoTag {
            val quality: MediaItemTag.Quality = MediaItemTag.Quality.Companion.of(sortedVideoStreams, selectedVideoStreamIndex)
            val audioTrack: MediaItemTag.AudioTrack = MediaItemTag.AudioTrack.Companion.of(audioStreams, selectedAudioStreamIndex)
            return StreamInfoTag(streamInfo, quality, audioTrack, null)
        }

        fun of(streamInfo: StreamInfo,
               audioStreams: List<AudioStream?>,
               selectedAudioStreamIndex: Int): StreamInfoTag {
            val audioTrack: MediaItemTag.AudioTrack = MediaItemTag.AudioTrack.Companion.of(audioStreams, selectedAudioStreamIndex)
            return StreamInfoTag(streamInfo, null, audioTrack, null)
        }

        fun of(streamInfo: StreamInfo): StreamInfoTag {
            return StreamInfoTag(streamInfo, null, null, null)
        }
    }
}
