package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.schabi.newpipe.extractor.stream_info.VideoStream;

import java.util.List;

public class Utils {

    /**
     * Return the index of the default stream in the list, based on the
     * preferred resolution and format chosen in the settings
     *
     * @param videoStreams      the list that will be extracted the index
     * @return index of the preferred resolution&format
     */
    public static int getPreferredResolution(Context context, List<VideoStream> videoStreams) {
        SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (defaultPreferences == null) return 0;

        String defaultResolution = defaultPreferences
                .getString(context.getString(R.string.default_resolution_key),
                        context.getString(R.string.default_resolution_value));

        String preferredFormat = defaultPreferences
                .getString(context.getString(R.string.preferred_video_format_key),
                        context.getString(R.string.preferred_video_format_default));

        // first try to find the one with the right resolution
        int selectedFormat = 0;
        for (int i = 0; i < videoStreams.size(); i++) {
            VideoStream item = videoStreams.get(i);
            if (defaultResolution.equals(item.resolution)) {
                selectedFormat = i;
            }
        }

        // than try to find the one with the right resolution and format
        for (int i = 0; i < videoStreams.size(); i++) {
            VideoStream item = videoStreams.get(i);
            if (defaultResolution.equals(item.resolution)
                    && preferredFormat.equals(MediaFormat.getNameById(item.format))) {
                selectedFormat = i;
            }
        }

        // this is actually an error,
        // but maybe there is really no stream fitting to the default value.
        return selectedFormat;
    }

    /**
     * Return the index of the default stream in the list, based on the
     * preferred audio format chosen in the settings
     *
     * @param audioStreams      the list that will be extracted the index
     * @return index of the preferred format
     */
    public static int getPreferredAudioFormat(Context context, List<AudioStream> audioStreams) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences == null) return 0;

        String preferredFormatString = sharedPreferences.getString(context.getString(R.string.default_audio_format_key), "webm");

        int preferredFormat = MediaFormat.WEBMA.id;
        switch (preferredFormatString) {
            case "webm":
                preferredFormat = MediaFormat.WEBMA.id;
                break;
            case "m4a":
                preferredFormat = MediaFormat.M4A.id;
                break;
            default:
                break;
        }

        for (int i = 0; i < audioStreams.size(); i++) {
            if (audioStreams.get(i).format == preferredFormat) {
                return i;
            }
        }

        return 0;
    }
}
