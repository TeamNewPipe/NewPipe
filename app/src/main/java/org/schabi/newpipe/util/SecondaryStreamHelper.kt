package org.schabi.newpipe.util

import android.content.Context
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper
import java.util.function.Predicate

class SecondaryStreamHelper<T : Stream?>(private val streams: StreamInfoWrapper<T?>,
                                         selectedStream: T) {
    private val position: Int

    init {
        position = streams.getStreamsList().indexOf(selectedStream)
        if (position < 0) {
            throw RuntimeException("selected stream not found")
        }
    }

    val stream: T?
        get() {
            return streams.getStreamsList().get(position)
        }
    val sizeInBytes: Long
        get() {
            return streams.getSizeInBytes(position)
        }

    companion object {
        /**
         * Finds an audio stream compatible with the provided video-only stream, so that the two streams
         * can be combined in a single file by the downloader. If there are multiple available audio
         * streams, chooses either the highest or the lowest quality one based on
         * [ListHelper.isLimitingDataUsage].
         *
         * @param context      Android context
         * @param audioStreams list of audio streams
         * @param videoStream  desired video-ONLY stream
         * @return the selected audio stream or null if a candidate was not found
         */
        fun getAudioStreamFor(context: Context,
                              audioStreams: List<AudioStream?>,
                              videoStream: VideoStream): AudioStream? {
            val mediaFormat: MediaFormat? = videoStream.getFormat()
            if (mediaFormat == MediaFormat.WEBM) {
                return audioStreams
                        .stream()
                        .filter(Predicate({ audioStream: AudioStream? ->
                            (audioStream!!.getFormat() == MediaFormat.WEBMA
                                    || audioStream.getFormat() == MediaFormat.WEBMA_OPUS)
                        }))
                        .max(ListHelper.getAudioFormatComparator(MediaFormat.WEBMA,
                                ListHelper.isLimitingDataUsage(context)))
                        .orElse(null)
            } else if (mediaFormat == MediaFormat.MPEG_4) {
                return audioStreams
                        .stream()
                        .filter(Predicate({ audioStream: AudioStream? -> audioStream!!.getFormat() == MediaFormat.M4A }))
                        .max(ListHelper.getAudioFormatComparator(MediaFormat.M4A,
                                ListHelper.isLimitingDataUsage(context)))
                        .orElse(null)
            } else {
                return null
            }
        }
    }
}
