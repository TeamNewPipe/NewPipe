package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.stream.StreamType;

/**
 * Utility class for {@link StreamType}.
 */
public final class StreamTypeUtil {
    private StreamTypeUtil() {
        // No impl pls
    }

    /**
     * Check if the {@link StreamType} of a stream is a livestream.
     *
     * @param streamType the stream type of the stream
     * @return whether the stream type is {@link StreamType#AUDIO_STREAM},
     * {@link StreamType#AUDIO_LIVE_STREAM} or {@link StreamType#POST_LIVE_AUDIO_STREAM}
     */
    public static boolean isAudio(final StreamType streamType) {
        return streamType == StreamType.AUDIO_STREAM
                || streamType == StreamType.AUDIO_LIVE_STREAM
                || streamType == StreamType.POST_LIVE_AUDIO_STREAM;
    }

    /**
     * Check if the {@link StreamType} of a stream is a livestream.
     *
     * @param streamType the stream type of the stream
     * @return whether the stream type is {@link StreamType#VIDEO_STREAM},
     * {@link StreamType#LIVE_STREAM} or {@link StreamType#POST_LIVE_STREAM}
     */
    public static boolean isVideo(final StreamType streamType) {
        return streamType == StreamType.VIDEO_STREAM
                || streamType == StreamType.LIVE_STREAM
                || streamType == StreamType.POST_LIVE_STREAM;
    }

    /**
     * Check if the {@link StreamType} of a stream is a livestream.
     *
     * @param streamType the stream type of the stream
     * @return whether the stream type is {@link StreamType#LIVE_STREAM} or
     * {@link StreamType#AUDIO_LIVE_STREAM}
     */
    public static boolean isLiveStream(final StreamType streamType) {
        return streamType == StreamType.LIVE_STREAM
                || streamType == StreamType.AUDIO_LIVE_STREAM;
    }
}
