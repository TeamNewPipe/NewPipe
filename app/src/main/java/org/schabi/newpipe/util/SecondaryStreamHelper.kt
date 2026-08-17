package org.schabi.newpipe.util

import android.content.Context
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper

class SecondaryStreamHelper<T : Stream>(
    private val streams: StreamInfoWrapper<T>,
    selectedStream: T
) {
    private val position: Int = streams.streamsList.indexOf(selectedStream)

    init {
        if (position < 0) {
            throw RuntimeException("selected stream not found")
        }
    }

    val stream: T
        get() = streams.streamsList[position]

    val sizeInBytes: Long
        get() = streams.getSizeInBytes(position)

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
        @JvmStatic
        fun getAudioStreamFor(
            context: Context,
            audioStreams: List<AudioStream>,
            videoStream: VideoStream
        ): AudioStream? {
            return when (videoStream.format) {
                MediaFormat.WEBM -> {
                    audioStreams
                        .filter { it.format == MediaFormat.WEBMA || it.format == MediaFormat.WEBMA_OPUS }
                        .maxWithOrNull(
                            ListHelper.getAudioFormatComparator(
                                MediaFormat.WEBMA,
                                ListHelper.isLimitingDataUsage(context)
                            )
                        )
                }
                MediaFormat.MPEG_4 -> {
                    audioStreams
                        .filter { it.format == MediaFormat.M4A }
                        .maxWithOrNull(
                            ListHelper.getAudioFormatComparator(
                                MediaFormat.M4A,
                                ListHelper.isLimitingDataUsage(context)
                            )
                        )
                }
                else -> null
            }
        }
    }
}
