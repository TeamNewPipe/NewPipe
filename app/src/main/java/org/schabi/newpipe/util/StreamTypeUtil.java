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
     * @return <code>true</code> if the streamType is a
     * {@link StreamType#LIVE_STREAM} or {@link StreamType#AUDIO_LIVE_STREAM}
     */
    public static boolean isLiveStream(final StreamType streamType) {
        return streamType == StreamType.LIVE_STREAM
                || streamType == StreamType.AUDIO_LIVE_STREAM;
    }
}
