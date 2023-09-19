package org.schabi.newpipe.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper;

import java.util.List;

public class SecondaryStreamHelper<T extends Stream> {
    private final int position;
    private final StreamInfoWrapper<T> streams;

    public SecondaryStreamHelper(@NonNull final StreamInfoWrapper<T> streams,
                                 final T selectedStream) {
        this.streams = streams;
        this.position = streams.getStreamsList().indexOf(selectedStream);
        if (this.position < 0) {
            throw new RuntimeException("selected stream not found");
        }
    }

    /**
     * Find the correct audio stream for the desired video stream.
     *
     * @param audioStreams list of audio streams
     * @param videoStream  desired video ONLY stream
     * @return selected audio stream or null if a candidate was not found
     */
    @Nullable
    public static AudioStream getAudioStreamFor(@NonNull final List<AudioStream> audioStreams,
                                                @NonNull final VideoStream videoStream) {
        final MediaFormat mediaFormat = videoStream.getFormat();
        if (mediaFormat == null) {
            return null;
        }

        switch (mediaFormat) {
            case WEBM:
            case MPEG_4:// ¿is mpeg-4 DASH?
                break;
            default:
                return null;
        }

        final boolean m4v = (mediaFormat == MediaFormat.MPEG_4);

        for (final AudioStream audio : audioStreams) {
            if (audio.getFormat() == (m4v ? MediaFormat.M4A : MediaFormat.WEBMA)) {
                return audio;
            }
        }

        if (m4v) {
            return null;
        }

        // retry, but this time in reverse order
        for (int i = audioStreams.size() - 1; i >= 0; i--) {
            final AudioStream audio = audioStreams.get(i);
            if (audio.getFormat() == MediaFormat.WEBMA_OPUS) {
                return audio;
            }
        }

        return null;
    }

    public T getStream() {
        return streams.getStreamsList().get(position);
    }

    public long getSizeInBytes() {
        return streams.getSizeInBytes(position);
    }
}
