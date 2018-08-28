package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public final class ListHelper {

    // Video format in order of quality. 0=lowest quality, n=highest quality
    private static final List<MediaFormat> VIDEO_FORMAT_QUALITY_RANKING =
        Arrays.asList(MediaFormat.v3GPP, MediaFormat.WEBM, MediaFormat.MPEG_4);

    // Audio format in order of quality. 0=lowest quality, n=highest quality
    private static final List<MediaFormat> AUDIO_FORMAT_QUALITY_RANKING =
        Arrays.asList(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A);
    // Audio format in order of efficiency. 0=most efficient, n=least efficient
    private static final List<MediaFormat> AUDIO_FORMAT_EFFICIENCY_RANKING =
            Arrays.asList(MediaFormat.WEBMA, MediaFormat.M4A, MediaFormat.MP3);

    private static final List<String> HIGH_RESOLUTION_LIST = Arrays.asList("1440p", "2160p", "1440p60", "2160p60");

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getDefaultResolutionIndex(Context context, List<VideoStream> videoStreams) {
        String defaultResolution = computeDefaultResolution(context,
                R.string.default_resolution_key, R.string.default_resolution_value);
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getResolutionIndex(Context context, List<VideoStream> videoStreams, String defaultResolution) {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getPopupDefaultResolutionIndex(Context context, List<VideoStream> videoStreams) {
        String defaultResolution = computeDefaultResolution(context,
                R.string.default_popup_resolution_key, R.string.default_popup_resolution_value);
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    public static int getPopupResolutionIndex(Context context, List<VideoStream> videoStreams, String defaultResolution) {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    public static int getDefaultAudioFormat(Context context, List<AudioStream> audioStreams) {
        MediaFormat defaultFormat = getDefaultFormat(context, R.string.default_audio_format_key,
                R.string.default_audio_format_value);

        // If the user has chosen to limit resolution to conserve mobile data
        // usage then we should also limit our audio usage.
        if (isLimitingDataUsage(context)) {
            return getMostCompactAudioIndex(defaultFormat, audioStreams);
        } else {
            return getHighestQualityAudioIndex(defaultFormat, audioStreams);
        }
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

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private static String computeDefaultResolution(Context context, int key, int value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Load the prefered resolution otherwise the best available
        String resolution = preferences != null
                ? preferences.getString(context.getString(key), context.getString(value))
                : context.getString(R.string.best_resolution_key);

        String maxResolution = getResolutionLimit(context);
        if (maxResolution != null && compareVideoStreamResolution(maxResolution, resolution) < 1){
            resolution = maxResolution;
        }
        return resolution;
    }

    /**
     * Return the index of the default stream in the list, based on the parameters
     * defaultResolution and defaultFormat
     *
     * @return index of the default resolution&format
     */
    static int getDefaultResolutionIndex(String defaultResolution, String bestResolutionKey,
                                         MediaFormat defaultFormat, List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) return -1;

        sortStreamList(videoStreams, false);
        if (defaultResolution.equals(bestResolutionKey)) {
            return 0;
        }

        int defaultStreamIndex = getVideoStreamIndex(defaultResolution, defaultFormat, videoStreams);

        // this is actually an error,
        // but maybe there is really no stream fitting to the default value.
        if (defaultStreamIndex == -1) {
            return 0;
        }
        return defaultStreamIndex;
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
    static List<VideoStream> getSortedStreamVideosList(MediaFormat defaultFormat, boolean showHigherResolutions, List<VideoStream> videoStreams, List<VideoStream> videoOnlyStreams, boolean ascendingOrder) {
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
    private static void sortStreamList(List<VideoStream> videoStreams, final boolean ascendingOrder) {
        Collections.sort(videoStreams, (o1, o2) -> {
            int result = compareVideoStreamResolution(o1, o2);
            return result == 0 ? 0 : (ascendingOrder ? result : -result);
        });
    }

    /**
     * Get the audio from the list with the highest quality. Format will be ignored if it yields
     * no results.
     *
     * @param audioStreams list the audio streams
     * @return index of the audio with the highest average bitrate of the default format
     */
    static int getHighestQualityAudioIndex(MediaFormat format, List<AudioStream> audioStreams) {
        int result = -1;
        if (audioStreams != null) {
            while(result == -1) {
                AudioStream prevStream = null;
                for (int idx = 0; idx < audioStreams.size(); idx++) {
                    AudioStream stream = audioStreams.get(idx);
                    if ((format == null || stream.getFormat() == format) &&
                            (prevStream == null || compareAudioStreamBitrate(prevStream, stream,
                                    AUDIO_FORMAT_QUALITY_RANKING) < 0)) {
                        prevStream = stream;
                        result = idx;
                    }
                }
                if (result == -1 && format == null) {
                    break;
                }
                format = null;
            }
        }
        return result;
    }

    /**
     * Get the audio from the list with the lowest bitrate and efficient format. Format will be
     * ignored if it yields no results.
     *
     * @param format The target format type or null if it doesn't matter
     * @param audioStreams list the audio streams
     * @return index of the audio stream that can produce the most compact results or -1 if not found.
     */
    static int getMostCompactAudioIndex(MediaFormat format, List<AudioStream> audioStreams) {
        int result = -1;
        if (audioStreams != null) {
            while(result == -1) {
                AudioStream prevStream = null;
                for (int idx = 0; idx < audioStreams.size(); idx++) {
                    AudioStream stream = audioStreams.get(idx);
                    if ((format == null || stream.getFormat() == format) &&
                            (prevStream == null || compareAudioStreamBitrate(prevStream, stream,
                                    AUDIO_FORMAT_EFFICIENCY_RANKING) > 0)) {
                        prevStream = stream;
                        result = idx;
                    }
                }
                if (result == -1 && format == null) {
                    break;
                }
                format = null;
            }
        }
        return result;
    }

    /**
     * Locates a possible match for the given resolution and format in the provided list.
     * In this order:
     *  1. Find a format and resolution match
     *  2. Find a format and resolution match and ignore the refresh
     *  3. Find a resolution match
     *  4. Find a resolution match and ignore the refresh
     *  5. Find a resolution just below the requested resolution and ignore the refresh
     *  6. Give up
     */
    static int getVideoStreamIndex(String targetResolution, MediaFormat targetFormat,
                                   List<VideoStream> videoStreams) {
        int fullMatchIndex = -1;
        int fullMatchNoRefreshIndex = -1;
        int resMatchOnlyIndex = -1;
        int resMatchOnlyNoRefreshIndex = -1;
        int lowerResMatchNoRefreshIndex = -1;
        String targetResolutionNoRefresh = targetResolution.replaceAll("p\\d+$", "p");

        for (int idx = 0; idx < videoStreams.size(); idx++) {
            MediaFormat format = targetFormat == null ? null : videoStreams.get(idx).getFormat();
            String resolution = videoStreams.get(idx).getResolution();
            String resolutionNoRefresh = resolution.replaceAll("p\\d+$", "p");

            if (format == targetFormat && resolution.equals(targetResolution)) {
                fullMatchIndex = idx;
            }

            if (format == targetFormat && resolutionNoRefresh.equals(targetResolutionNoRefresh)) {
                fullMatchNoRefreshIndex = idx;
            }

            if (resMatchOnlyIndex == -1 && resolution.equals(targetResolution)) {
                resMatchOnlyIndex = idx;
            }

            if (resMatchOnlyNoRefreshIndex == -1 && resolutionNoRefresh.equals(targetResolutionNoRefresh)) {
                resMatchOnlyNoRefreshIndex = idx;
            }

            if (lowerResMatchNoRefreshIndex == -1 && compareVideoStreamResolution(resolutionNoRefresh, targetResolutionNoRefresh) < 0) {
                lowerResMatchNoRefreshIndex = idx;
            }
        }

        if (fullMatchIndex != -1) {
            return fullMatchIndex;
        }
        if (fullMatchNoRefreshIndex != -1) {
            return fullMatchNoRefreshIndex;
        }
        if (resMatchOnlyIndex != -1) {
            return resMatchOnlyIndex;
        }
        if (resMatchOnlyNoRefreshIndex != -1) {
            return resMatchOnlyNoRefreshIndex;
        }
        return lowerResMatchNoRefreshIndex;
    }

    /**
     * Fetches the desired resolution or returns the default if it is not found. The resolution
     * will be reduced if video chocking is active.
     */
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

    // Compares the quality of two audio streams
    private static int compareAudioStreamBitrate(AudioStream streamA, AudioStream streamB,
                                                 List<MediaFormat> formatRanking) {
        if (streamA == null) {
            return -1;
        }
        if (streamB == null) {
            return 1;
        }
        if (streamA.getAverageBitrate() < streamB.getAverageBitrate()) {
            return -1;
        }
        if (streamA.getAverageBitrate() > streamB.getAverageBitrate()) {
            return 1;
        }

        // Same bitrate and format
        return formatRanking.indexOf(streamA.getFormat()) - formatRanking.indexOf(streamB.getFormat());
    }

    private static int compareVideoStreamResolution(String r1, String r2) {
        int res1 = Integer.parseInt(r1.replaceAll("0p\\d+$", "1")
                .replaceAll("[^\\d.]", ""));
        int res2 = Integer.parseInt(r2.replaceAll("0p\\d+$", "1")
                .replaceAll("[^\\d.]", ""));
        return res1 - res2;
    }

    // Compares the quality of two video streams.
    private static int compareVideoStreamResolution(VideoStream streamA, VideoStream streamB) {
        if (streamA == null) {
            return -1;
        }
        if (streamB == null) {
            return 1;
        }

        int resComp = compareVideoStreamResolution(streamA.getResolution(), streamB.getResolution());
        if (resComp != 0) {
            return resComp;
        }

        // Same bitrate and format
        return ListHelper.VIDEO_FORMAT_QUALITY_RANKING.indexOf(streamA.getFormat()) - ListHelper.VIDEO_FORMAT_QUALITY_RANKING.indexOf(streamB.getFormat());
    }



    private static boolean isLimitingDataUsage(Context context) {
        return getResolutionLimit(context) != null;
    }

    /**
     * The maximum resolution allowed
     * @param context App context
     * @return maximum resolution allowed or null if there is no maximum
     */
    private static String getResolutionLimit(Context context) {
        String resolutionLimit = null;
        if (!isWifiActive(context)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String defValue = context.getString(R.string.limit_data_usage_none_key);
            String value = preferences.getString(
                    context.getString(R.string.limit_mobile_data_usage_key), defValue);
            resolutionLimit = value.equals(defValue) ? null : value;
        }
        return resolutionLimit;
    }

    /**
     * Are we connected to wifi?
     * @param context App context
     * @return {@code true} if connected to wifi
     */
    private static boolean isWifiActive(Context context)
    {
        ConnectivityManager manager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return manager != null && manager.getActiveNetworkInfo() != null && manager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI;
    }
}
