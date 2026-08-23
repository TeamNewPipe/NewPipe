package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.stream.StreamType;

/**
 * Utility class for {@link org.schabi.newpipe.extractor.stream.StreamType}.
 */
public final class StreamTypeUtil {
    private StreamTypeUtil() {
        // No impl pls
    }

    /**
     * Checks if the streamType is a livestream.
     *
     * @param streamType
     * @return <code>true</code> when the streamType is a
     * {@link StreamType#LIVE_STREAM} or {@link StreamType#AUDIO_LIVE_STREAM}
     */
    public static boolean isLiveStream(final StreamType streamType) {
        return streamType == StreamType.LIVE_STREAM
                || streamType == StreamType.AUDIO_LIVE_STREAM;
    }
}
