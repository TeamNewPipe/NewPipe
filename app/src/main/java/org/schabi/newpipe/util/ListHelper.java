package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public final class ListHelper {

    private static final List<String> HIGH_RESOLUTION_LIST = Arrays.asList("1440p", "2160p", "1440p60", "2160p60");

    /**
     * Return the index of the default stream in the list, based on the parameters
     * defaultResolution and defaultFormat
     *
     * @return index of the default resolution&format
     */
    public static int getDefaultResolutionIndex(String defaultResolution, String bestResolutionKey, MediaFormat defaultFormat, List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) return -1;

        sortStreamList(videoStreams, false);
        if (defaultResolution.equals(bestResolutionKey)) {
            return 0;
        }

        int defaultStreamIndex = getDefaultStreamIndex(defaultResolution, defaultFormat, videoStreams);
        if (defaultStreamIndex == -1 && defaultResolution.contains("p60")) {
            defaultStreamIndex = getDefaultStreamIndex(defaultResolution.replace("p60", "p"), defaultFormat, videoStreams);
        }

        // this is actually an error,
        // but maybe there is really no stream fitting to the default value.
        if (defaultStreamIndex == -1) return 0;

        return defaultStreamIndex;
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getDefaultResolutionIndex(Context context, List<VideoStream> videoStreams) {
        SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (defaultPreferences == null) return 0;

        String defaultResolution = defaultPreferences.getString(context.getString(R.string.default_resolution_key), context.getString(R.string.default_resolution_value));
        return getDefaultResolutionIndex(context, videoStreams, defaultResolution);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getDefaultResolutionIndex(Context context, List<VideoStream> videoStreams, String defaultResolution) {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getPopupDefaultResolutionIndex(Context context, List<VideoStream> videoStreams) {
        SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (defaultPreferences == null) return 0;

        String defaultResolution = defaultPreferences.getString(context.getString(R.string.default_popup_resolution_key), context.getString(R.string.default_popup_resolution_value));
        return getPopupDefaultResolutionIndex(context, videoStreams, defaultResolution);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getPopupDefaultResolutionIndex(Context context, List<VideoStream> videoStreams, String defaultResolution) {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    public static int getDefaultAudioFormat(Context context, List<AudioStream> audioStreams) {
        MediaFormat defaultFormat = getDefaultFormat(context, R.string.default_audio_format_key, R.string.default_audio_format_value);
        return getHighestQualityAudioIndex(defaultFormat, audioStreams);
    }

    public static int getHighestQualityAudioIndex(List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) return -1;

        int highestQualityIndex = 0;
        if (audioStreams.size() > 1) for (int i = 1; i < audioStreams.size(); i++) {
            AudioStream audioStream = audioStreams.get(i);
            if (audioStream.getAverageBitrate() >= audioStreams.get(highestQualityIndex).getAverageBitrate()) highestQualityIndex = i;
        }
        return highestQualityIndex;
    }

    /**
     * Get the audio from the list with the highest bitrate
     *
     * @param audioStreams list the audio streams
     * @return audio with highest average bitrate
     */
    public static AudioStream getHighestQualityAudio(List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) return null;

        return audioStreams.get(getHighestQualityAudioIndex(audioStreams));
    }

    /**
     * Get the audio from the list with the highest bitrate
     *
     * @param audioStreams list the audio streams
     * @return index of the audio with the highest average bitrate of the default format
     */
    public static int getHighestQualityAudioIndex(MediaFormat defaultFormat, List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty() || defaultFormat == null) return -1;

        int highestQualityIndex = -1;
        for (int i = 0; i < audioStreams.size(); i++) {
            AudioStream audioStream = audioStreams.get(i);
            if (highestQualityIndex == -1 && audioStream.getFormat() == defaultFormat) highestQualityIndex = i;

            if (highestQualityIndex != -1 && audioStream.getFormat() == defaultFormat
                    && audioStream.getAverageBitrate() > audioStreams.get(highestQualityIndex).getAverageBitrate()) {
                highestQualityIndex = i;
            }
        }
        if (highestQualityIndex == -1) highestQualityIndex = getHighestQualityAudioIndex(audioStreams);
        return highestQualityIndex;
    }

    /**
     * Join the two lists of video streams (video_only and normal videos), and sort them according with default format
     * chosen by the user
     *
     * @param context          context to search for the format to give preference
     * @param videoStreams     normal videos list
     * @param videoOnlyStreams video only stream list
     * @param ascendingOrder   true -> smallest to greatest | false -> greatest to smallest
     * @return the sorted list
     */
    public static List<VideoStream> getSortedStreamVideosList(Context context, List<VideoStream> videoStreams, List<VideoStream> videoOnlyStreams, boolean ascendingOrder) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        boolean showHigherResolutions = preferences.getBoolean(context.getString(R.string.show_higher_resolutions_key), false);
        MediaFormat defaultFormat = getDefaultFormat(context, R.string.default_video_format_key, R.string.default_video_format_value);

        return getSortedStreamVideosList(defaultFormat, showHigherResolutions, videoStreams, videoOnlyStreams, ascendingOrder);
    }

    /**
     * Join the two lists of video streams (video_only and normal videos), and sort them according with default format
     * chosen by the user
     *
     * @param defaultFormat       format to give preference
     * @param showHigherResolutions show >1080p resolutions
     * @param videoStreams          normal videos list
     * @param videoOnlyStreams      video only stream list
     * @param ascendingOrder        true -> smallest to greatest | false -> greatest to smallest    @return the sorted list
     * @return the sorted list
     */
    public static List<VideoStream> getSortedStreamVideosList(MediaFormat defaultFormat, boolean showHigherResolutions, List<VideoStream> videoStreams, List<VideoStream> videoOnlyStreams, boolean ascendingOrder) {
        ArrayList<VideoStream> retList = new ArrayList<>();
        HashMap<String, VideoStream> hashMap = new HashMap<>();

        if (videoOnlyStreams != null) {
            for (VideoStream stream : videoOnlyStreams) {
                if (!showHigherResolutions && HIGH_RESOLUTION_LIST.contains(stream.getResolution())) continue;
                retList.add(stream);
            }
        }
        if (videoStreams != null) {
            for (VideoStream stream : videoStreams) {
                if (!showHigherResolutions && HIGH_RESOLUTION_LIST.contains(stream.getResolution())) continue;
                retList.add(stream);
            }
        }

        // Add all to the hashmap
        for (VideoStream videoStream : retList) hashMap.put(videoStream.getResolution(), videoStream);

        // Override the values when the key == resolution, with the defaultFormat
        for (VideoStream videoStream : retList) {
            if (videoStream.getFormat() == defaultFormat) hashMap.put(videoStream.getResolution(), videoStream);
        }

        retList.clear();
        retList.addAll(hashMap.values());
        sortStreamList(retList, ascendingOrder);
        return retList;
    }

    /**
     * Sort the streams list depending on the parameter ascendingOrder;
     * <p>
     * It works like that:<br>
     * - Take a string resolution, remove the letters, replace "0p60" (for 60fps videos) with "1"
     * and sort by the greatest:<br>
     * <blockquote><pre>
     *      720p     ->  720
     *      720p60   ->  721
     *      360p     ->  360
     *      1080p    ->  1080
     *      1080p60  ->  1081
     * <br>
     *  ascendingOrder  ? 360 < 720 < 721 < 1080 < 1081
     *  !ascendingOrder ? 1081 < 1080 < 721 < 720 < 360</pre></blockquote>
     *
     * @param videoStreams   list that the sorting will be applied
     * @param ascendingOrder true -> smallest to greatest | false -> greatest to smallest
     */
    public static void sortStreamList(List<VideoStream> videoStreams, final boolean ascendingOrder) {
        Collections.sort(videoStreams, new Comparator<VideoStream>() {
            @Override
            public int compare(VideoStream o1, VideoStream o2) {
                int res1 = Integer.parseInt(o1.getResolution().replace("0p60", "1").replaceAll("[^\\d.]", ""));
                int res2 = Integer.parseInt(o2.getResolution().replace("0p60", "1").replaceAll("[^\\d.]", ""));

                return ascendingOrder ? res1 - res2 : res2 - res1;
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private static int getDefaultStreamIndex(String defaultResolution, MediaFormat defaultFormat, List<VideoStream> videoStreams) {
        int defaultStreamIndex = -1;
        for (int i = 0; i < videoStreams.size(); i++) {
            VideoStream stream = videoStreams.get(i);
            if (defaultStreamIndex == -1 && stream.getResolution().equals(defaultResolution)) defaultStreamIndex = i;

            if (stream.getFormat() == defaultFormat && stream.getResolution().equals(defaultResolution)) {
                return i;
            }
        }

        return defaultStreamIndex;
    }

    private static int getDefaultResolutionWithDefaultFormat(Context context, String defaultResolution, List<VideoStream> videoStreams) {
        MediaFormat defaultFormat = getDefaultFormat(context, R.string.default_video_format_key, R.string.default_video_format_value);
        return getDefaultResolutionIndex(defaultResolution, context.getString(R.string.best_resolution_key), defaultFormat, videoStreams);
    }

    private static MediaFormat getDefaultFormat(Context context, @StringRes int defaultFormatKey, @StringRes int defaultFormatValueKey) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        String defaultFormat = context.getString(defaultFormatValueKey);
        String defaultFormatString = preferences.getString(context.getString(defaultFormatKey), defaultFormat);

        MediaFormat defaultMediaFormat = getMediaFormatFromKey(context, defaultFormatString);
        if (defaultMediaFormat == null) {
            preferences.edit().putString(context.getString(defaultFormatKey), defaultFormat).apply();
            defaultMediaFormat = getMediaFormatFromKey(context, defaultFormat);
        }

        return defaultMediaFormat;
    }

    private static MediaFormat getMediaFormatFromKey(Context context, String formatKey) {
        MediaFormat format = null;
        if (formatKey.equals(context.getString(R.string.video_webm_key))) {
            format = MediaFormat.WEBM;
        } else if (formatKey.equals(context.getString(R.string.video_mp4_key))) {
            format = MediaFormat.MPEG_4;
        } else if (formatKey.equals(context.getString(R.string.video_3gp_key))) {
            format = MediaFormat.v3GPP;
        } else if (formatKey.equals(context.getString(R.string.audio_webm_key))) {
            format = MediaFormat.WEBMA;
        } else if (formatKey.equals(context.getString(R.string.audio_m4a_key))) {
            format = MediaFormat.M4A;
        }
        return format;
    }
}
