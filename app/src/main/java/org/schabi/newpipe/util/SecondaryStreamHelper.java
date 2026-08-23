package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;

import java.util.List;

public class SecondaryStreamHelper<T extends Stream> {
    private final int position;
    private final StreamSizeWrapper<T> streams;

    public SecondaryStreamHelper(final StreamSizeWrapper<T> streams, final T selectedStream) {
        this.streams = streams;
        this.position = streams.getStreamsList().indexOf(selectedStream);
        if (this.position < 0) {
            throw new RuntimeException("selected stream not found");
        }
    }

    /**
     * Find the correct audio stream for the desired video stream, considering user's preferred audio language.
     *
     * @param context      Android app context (can be null, but language preference will be ignored)
     * @param audioStreams list of audio streams
     * @param videoStream  desired video ONLY stream
     * @return selected audio stream or null if a candidate was not found
     */
    public static AudioStream getAudioStreamFor(@Nullable final Context context,
                                                @NonNull final List<AudioStream> audioStreams,
                                                @NonNull final VideoStream videoStream) {
        switch (videoStream.getFormat()) {
            case WEBM:
            case MPEG_4:
                break;
            default:
                return null;
        }

        final boolean m4v = videoStream.getFormat() == MediaFormat.MPEG_4;
        final MediaFormat targetFormat = m4v ? MediaFormat.M4A : MediaFormat.WEBMA;

        List<AudioStream> filteredStreams = audioStreams;
        if (context != null) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final String preferredAudioLanguage = prefs.getString(
                    context.getString(R.string.preferred_audio_language_key), "original");
            filteredStreams = ListHelper.filterAudioStreamsByLanguage(audioStreams, preferredAudioLanguage);
            if (filteredStreams.isEmpty()) {
                filteredStreams = audioStreams;
            }
        }

        for (final AudioStream audio : filteredStreams) {
            if (audio.getFormat() == targetFormat) {
                return audio;
            }
        }

        if (m4v) {
            return null;
        }

        for (int i = filteredStreams.size() - 1; i >= 0; i--) {
            final AudioStream audio = filteredStreams.get(i);
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
