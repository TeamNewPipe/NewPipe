package org.schabi.newpipe.util;

import android.support.annotation.NonNull;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;

import java.util.List;

public class SecondaryStreamHelper<T extends Stream> {
    private final int position;
    private final StreamSizeWrapper<T> streams;

    public SecondaryStreamHelper(StreamSizeWrapper<T> streams, T selectedStream) {
        this.streams = streams;
        this.position = streams.getStreamsList().indexOf(selectedStream);
        if (this.position < 0) throw new RuntimeException("selected stream not found");
    }

    public T getStream() {
        return streams.getStreamsList().get(position);
    }

    public long getSizeInBytes() {
        return streams.getSizeInBytes(position);
    }

    /**
     * find the correct audio stream for the desired video stream
     *
     * @param audioStreams list of audio streams
     * @param videoStream  desired video ONLY stream
     * @return selected audio stream or null if a candidate was not found
     */
    public static AudioStream getAudioStreamFor(@NonNull List<AudioStream> audioStreams, @NonNull VideoStream videoStream) {
        switch (videoStream.getFormat()) {
            case WEBM:
            case MPEG_4:
                break;
            default:
                return null;
        }

        boolean m4v = videoStream.getFormat() == MediaFormat.MPEG_4;

        for (AudioStream audio : audioStreams) {
            if (audio.getFormat() == (m4v ? MediaFormat.M4A : MediaFormat.WEBMA)) {
                return audio;
            }
        }

        // retry, but this time in reverse order
        for (int i = audioStreams.size() - 1; i >= 0; i--) {
            AudioStream audio = audioStreams.get(i);
            if (audio.getFormat() == (m4v ? MediaFormat.MP3 : MediaFormat.OPUS)) {
                return audio;
            }
        }

        return null;
    }
}
