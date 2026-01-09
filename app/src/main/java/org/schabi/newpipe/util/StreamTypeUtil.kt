/*
 * SPDX-FileCopyrightText: 2021-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Utility class for [StreamType].
 */
object StreamTypeUtil {
    /**
     * Check if the [StreamType] of a stream is a livestream.
     *
     * @param streamType the stream type of the stream
     * @return whether the stream type is [StreamType.AUDIO_STREAM],
     * [StreamType.AUDIO_LIVE_STREAM] or [StreamType.POST_LIVE_AUDIO_STREAM]
     */
    @JvmStatic
    fun isAudio(streamType: StreamType): Boolean {
        return streamType == StreamType.AUDIO_STREAM ||
                streamType == StreamType.AUDIO_LIVE_STREAM ||
                streamType == StreamType.POST_LIVE_AUDIO_STREAM
    }

    /**
     * Check if the [StreamType] of a stream is a livestream.
     *
     * @param streamType the stream type of the stream
     * @return whether the stream type is [StreamType.VIDEO_STREAM],
     * [StreamType.LIVE_STREAM] or [StreamType.POST_LIVE_STREAM]
     */
    @JvmStatic
    fun isVideo(streamType: StreamType): Boolean {
        return streamType == StreamType.VIDEO_STREAM ||
                streamType == StreamType.LIVE_STREAM ||
                streamType == StreamType.POST_LIVE_STREAM
    }

    /**
     * Check if the [StreamType] of a stream is a livestream.
     *
     * @param streamType the stream type of the stream
     * @return whether the stream type is [StreamType.LIVE_STREAM] or
     * [StreamType.AUDIO_LIVE_STREAM]
     */
    @JvmStatic
    fun isLiveStream(streamType: StreamType): Boolean {
        return streamType == StreamType.LIVE_STREAM ||
                streamType == StreamType.AUDIO_LIVE_STREAM
    }
}
