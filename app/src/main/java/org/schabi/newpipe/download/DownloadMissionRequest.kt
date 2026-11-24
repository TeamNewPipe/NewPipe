/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import us.shandian.giga.get.DownloadRequestHeaders
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing

internal class DownloadMissionRequest(
    val urls: Array<String>,
    val kind: Char,
    val threads: Int,
    val postprocessingName: String?,
    val postprocessingArguments: Array<String>?,
    val nearLength: Long,
    val recoveryInfo: ArrayList<MissionRecoveryInfo>,
    val requestHeaders: ArrayList<HashMap<String, String>>? = null
)

/** Builds downloader inputs from the streams selected in the download dialog. */
internal object DownloadMissionRequestFactory {
    @JvmStatic
    fun forAudio(
        selectedStream: AudioStream,
        muxedFallbackSource: VideoStream?,
        mp3OutputSelected: Boolean,
        mp3BitrateKbps: Int,
        threads: Int
    ): DownloadMissionRequest {
        val postprocessingName: String?
        val postprocessingArguments: Array<String>?
        when {
            Mp3DownloadPolicy.shouldTranscode(
                mp3OutputSelected,
                selectedStream.format
            ) -> {
                postprocessingName = Postprocessing.ALGORITHM_MP3_FROM_AUDIO
                postprocessingArguments = arrayOf(mp3BitrateKbps.toString())
            }

            muxedFallbackSource != null -> {
                postprocessingName = Postprocessing.ALGORITHM_M4A_FROM_MP4_DEMUXER
                postprocessingArguments = null
            }

            selectedStream.format == MediaFormat.M4A -> {
                postprocessingName = Postprocessing.ALGORITHM_M4A_NO_DASH
                postprocessingArguments = null
            }

            selectedStream.format == MediaFormat.WEBMA_OPUS -> {
                postprocessingName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER
                postprocessingArguments = null
            }

            else -> {
                postprocessingName = null
                postprocessingArguments = null
            }
        }

        return singleStreamRequest(
            selectedStream = selectedStream,
            recoveryStream = muxedFallbackSource ?: selectedStream,
            kind = 'a',
            threads = threads,
            postprocessingName = postprocessingName,
            postprocessingArguments = postprocessingArguments
        )
    }

    @JvmStatic
    fun forVideo(
        selectedStream: VideoStream,
        secondaryStream: AudioStream?,
        videoSize: Long,
        secondarySize: Long,
        threads: Int
    ): DownloadMissionRequest {
        if (secondaryStream == null) {
            return singleStreamRequest(
                selectedStream = selectedStream,
                recoveryStream = selectedStream,
                kind = 'v',
                threads = threads
            )
        }
        require(secondaryStream.deliveryMethod == PROGRESSIVE_HTTP) {
            "Unsupported stream delivery format${secondaryStream.deliveryMethod}"
        }

        val postprocessingName = if (selectedStream.format == MediaFormat.MPEG_4) {
            Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER
        } else {
            Postprocessing.ALGORITHM_WEBM_MUXER
        }
        val nearLength = if (secondarySize > 0 && videoSize > 0) {
            secondarySize + videoSize
        } else {
            0
        }
        return DownloadMissionRequest(
            urls = arrayOf(selectedStream.content, secondaryStream.content),
            kind = 'v',
            threads = threads,
            postprocessingName = postprocessingName,
            postprocessingArguments = null,
            nearLength = nearLength,
            recoveryInfo = arrayListOf(
                MissionRecoveryInfo(selectedStream),
                MissionRecoveryInfo(secondaryStream)
            ),
            requestHeaders = DownloadRequestHeaders.capture(selectedStream, secondaryStream)
        )
    }

    @JvmStatic
    fun forSubtitle(selectedStream: SubtitlesStream): DownloadMissionRequest {
        val format = selectedStream.format
        val postprocessingName = if (format == MediaFormat.TTML) {
            Postprocessing.ALGORITHM_TTML_CONVERTER
        } else {
            null
        }
        val postprocessingArguments = if (format == MediaFormat.TTML) {
            arrayOf(format.getSuffix(), "false")
        } else {
            null
        }
        return singleStreamRequest(
            selectedStream = selectedStream,
            recoveryStream = selectedStream,
            kind = 's',
            threads = 1,
            postprocessingName = postprocessingName,
            postprocessingArguments = postprocessingArguments
        )
    }

    private fun singleStreamRequest(
        selectedStream: Stream,
        recoveryStream: Stream,
        kind: Char,
        threads: Int,
        postprocessingName: String? = null,
        postprocessingArguments: Array<String>? = null
    ): DownloadMissionRequest {
        return DownloadMissionRequest(
            urls = arrayOf(selectedStream.content),
            kind = kind,
            threads = threads,
            postprocessingName = postprocessingName,
            postprocessingArguments = postprocessingArguments,
            nearLength = 0,
            recoveryInfo = arrayListOf(MissionRecoveryInfo(recoveryStream)),
            requestHeaders = DownloadRequestHeaders.capture(recoveryStream)
        )
    }
}
