package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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

    private static final List<String> HIGH_RESOLUTION_LIST
            = Arrays.asList("1440p", "2160p", "1440p60", "2160p60");

    private ListHelper() { }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     * @param context      Android app context
     * @param videoStreams list of the video streams to check
     * @return index of the video stream with the default index
     */
    public static int getDefaultResolutionIndex(final Context context,
                                                final List<VideoStream> videoStreams) {
        String defaultResolution = computeDefaultResolution(context,
                R.string.default_resolution_key, R.string.default_resolution_value);
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     * @param context           Android app context
     * @param videoStreams      list of the video streams to check
     * @param defaultResolution the default resolution to look for
     * @return index of the video stream with the default index
     */
    public static int getResolutionIndex(final Context context,
                                         final List<VideoStream> videoStreams,
                                         final String defaultResolution) {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     * @param context           Android app context
     * @param videoStreams      list of the video streams to check
     * @return index of the video stream with the default index
     */
    public static int getPopupDefaultResolutionIndex(final Context context,
                                                     final List<VideoStream> videoStreams) {
        String defaultResolution = computeDefaultResolution(context,
                R.string.default_popup_resolution_key, R.string.default_popup_resolution_value);
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     * @param context           Android app context
     * @param videoStreams      list of the video streams to check
     * @param defaultResolution the default resolution to look for
     * @return index of the video stream with the default index
     */
    public static int getPopupResolutionIndex(final Context context,
                                              final List<VideoStream> videoStreams,
                                              final String defaultResolution) {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    public static int getDefaultAudioFormat(final Context context,
                                            final List<AudioStream> audioStreams) {
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
     * Join the two lists of video streams (video_only and normal videos),
     * and sort them according with default format chosen by the user.
     *
     * @param context          context to search for the format to give preference
     * @param videoStreams     normal videos list
     * @param videoOnlyStreams video only stream list
     * @param ascendingOrder   true -> smallest to greatest | false -> greatest to smallest
     * @return the sorted list
     */
    public static List<VideoStream> getSortedStreamVideosList(final Context context,
                                                              final List<VideoStream> videoStreams,
                                                              final List<VideoStream>
                                                                      videoOnlyStreams,
                                                              final boolean ascendingOrder) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        boolean showHigherResolutions = preferences.getBoolean(
                context.getString(R.string.show_higher_resolutions_key), false);
        MediaFormat defaultFormat = getDefaultFormat(context, R.string.default_video_format_key,
                R.string.default_video_format_value);

        return getSortedStreamVideosList(defaultFormat, showHigherResolutions, videoStreams,
                videoOnlyStreams, ascendingOrder);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private static String computeDefaultResolution(final Context context, final int key,
                                                   final int value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Load the prefered resolution otherwise the best available
        String resolution = preferences != null
                ? preferences.getString(context.getString(key), context.getString(value))
                : context.getString(R.string.best_resolution_key);

        String maxResolution = getResolutionLimit(context);
        if (maxResolution != null
                && (resolution.equals(context.getString(R.string.best_resolution_key))
                || compareVideoStreamResolution(maxResolution, resolution) < 1)) {
            resolution = maxResolution;
        }
        return resolution;
    }

    /**
     * Return the index of the default stream in the list, based on the parameters
     * defaultResolution and defaultFormat.
     *
     * @param defaultResolution the default resolution to look for
     * @param bestResolutionKey key of the best resolution
     * @param defaultFormat     the default fomat to look for
     * @param videoStreams      list of the video streams to check
     * @return index of the default resolution&format
     */
    static int getDefaultResolutionIndex(final String defaultResolution,
                                         final String bestResolutionKey,
                                         final MediaFormat defaultFormat,
                                         final List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) {
            return -1;
        }

        sortStreamList(videoStreams, false);
        if (defaultResolution.equals(bestResolutionKey)) {
            return 0;
        }

        int defaultStreamIndex
                = getVideoStreamIndex(defaultResolution, defaultFormat, videoStreams);

        // this is actually an error,
        // but maybe there is really no stream fitting to the default value.
        if (defaultStreamIndex == -1) {
            return 0;
        }
        return defaultStreamIndex;
    }

    /**
     * Join the two lists of video streams (video_only and normal videos),
     * and sort them according with default format chosen by the user.
     *
     * @param defaultFormat         format to give preference
     * @param showHigherResolutions show >1080p resolutions
     * @param videoStreams          normal videos list
     * @param videoOnlyStreams      video only stream list
     * @param ascendingOrder        true -> smallest to greatest | false -> greatest to smallest
     * @return the sorted list
     */
    static List<VideoStream> getSortedStreamVideosList(final MediaFormat defaultFormat,
                                                       final boolean showHigherResolutions,
                                                       final List<VideoStream> videoStreams,
                                                       final List<VideoStream> videoOnlyStreams,
                                                       final boolean ascendingOrder) {
        ArrayList<VideoStream> retList = new ArrayList<>();
        HashMap<String, VideoStream> hashMap = new HashMap<>();

        if (videoOnlyStreams != null) {
            for (VideoStream stream : videoOnlyStreams) {
                if (!showHigherResolutions
                        && HIGH_RESOLUTION_LIST.contains(stream.getResolution())) {
                    continue;
                }
                retList.add(stream);
            }
        }
        if (videoStreams != null) {
            for (VideoStream stream : videoStreams) {
                if (!showHigherResolutions
                        && HIGH_RESOLUTION_LIST.contains(stream.getResolution())) {
                    continue;
                }
                retList.add(stream);
            }
        }

        // Add all to the hashmap
        for (VideoStream videoStream : retList) {
            hashMap.put(videoStream.getResolution(), videoStream);
        }

        // Override the values when the key == resolution, with the defaultFormat
        for (VideoStream videoStream : retList) {
            if (videoStream.getFormat() == defaultFormat) {
                hashMap.put(videoStream.getResolution(), videoStream);
            }
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
    private static void sortStreamList(final List<VideoStream> videoStreams,
                                       final boolean ascendingOrder) {
        Collections.sort(videoStreams, (o1, o2) -> {
            int result = compareVideoStreamResolution(o1, o2);
            return result == 0 ? 0 : (ascendingOrder ? result : -result);
        });
    }

    /**
     * Get the audio from the list with the highest quality.
     * Format will be ignored if it yields no results.
     *
     * @param format       The target format type or null if it doesn't matter
     * @param audioStreams List of audio streams
     * @return Index of audio stream that produces the most compact results or -1 if not found
     */
    static int getHighestQualityAudioIndex(@Nullable MediaFormat format,
                                           final List<AudioStream> audioStreams) {
        int result = -1;
        if (audioStreams != null) {
            while (result == -1) {
                AudioStream prevStream = null;
                for (int idx = 0; idx < audioStreams.size(); idx++) {
                    AudioStream stream = audioStreams.get(idx);
                    if ((format == null || stream.getFormat() == format)
                            && (prevStream == null || compareAudioStreamBitrate(prevStream, stream,
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
     * Get the audio from the list with the lowest bitrate and most efficient format.
     * Format will be ignored if it yields no results.
     *
     * @param format       The target format type or null if it doesn't matter
     * @param audioStreams List of audio streams
     * @return Index of audio stream that produces the most compact results or -1 if not found
     */
    static int getMostCompactAudioIndex(@Nullable MediaFormat format,
                                        final List<AudioStream> audioStreams) {
        int result = -1;
        if (audioStreams != null) {
            while (result == -1) {
                AudioStream prevStream = null;
                for (int idx = 0; idx < audioStreams.size(); idx++) {
                    AudioStream stream = audioStreams.get(idx);
                    if ((format == null || stream.getFormat() == format)
                            && (prevStream == null || compareAudioStreamBitrate(prevStream, stream,
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
     *
     * <p>In this order:</p>
     *
     * <ol>
     * <li>Find a format and resolution match</li>
     * <li>Find a format and resolution match and ignore the refresh</li>
     * <li>Find a resolution match</li>
     * <li>Find a resolution match and ignore the refresh</li>
     * <li>Find a resolution just below the requested resolution and ignore the refresh</li>
     * <li>Give up</li>
     * </ol>
     *
     * @param targetResolution the resolution to look for
     * @param targetFormat     the format to look for
     * @param videoStreams     the available video streams
     * @return the index of the prefered video stream
     */
    static int getVideoStreamIndex(final String targetResolution, final MediaFormat targetFormat,
                                   final List<VideoStream> videoStreams) {
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

            if (resMatchOnlyNoRefreshIndex == -1
                    && resolutionNoRefresh.equals(targetResolutionNoRefresh)) {
                resMatchOnlyNoRefreshIndex = idx;
            }

            if (lowerResMatchNoRefreshIndex == -1 && compareVideoStreamResolution(
                    resolutionNoRefresh, targetResolutionNoRefresh) < 0) {
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
     * Fetches the desired resolution or returns the default if it is not found.
     * The resolution will be reduced if video chocking is active.
     *
     * @param context           Android app context
     * @param defaultResolution the default resolution
     * @param videoStreams      the list of video streams to check
     * @return the index of the prefered video stream
     */
    private static int getDefaultResolutionWithDefaultFormat(final Context context,
                                                             final String defaultResolution,
                                                             final List<VideoStream> videoStreams) {
        MediaFormat defaultFormat = getDefaultFormat(context, R.string.default_video_format_key,
                R.string.default_video_format_value);
        return getDefaultResolutionIndex(defaultResolution,
                context.getString(R.string.best_resolution_key), defaultFormat, videoStreams);
    }

    private static MediaFormat getDefaultFormat(final Context context,
                                                @StringRes final int defaultFormatKey,
                                                @StringRes final int defaultFormatValueKey) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        String defaultFormat = context.getString(defaultFormatValueKey);
        String defaultFormatString = preferences.getString(
                context.getString(defaultFormatKey), defaultFormat);

        MediaFormat defaultMediaFormat = getMediaFormatFromKey(context, defaultFormatString);
        if (defaultMediaFormat == null) {
            preferences.edit().putString(context.getString(defaultFormatKey), defaultFormat)
                    .apply();
            defaultMediaFormat = getMediaFormatFromKey(context, defaultFormat);
        }

        return defaultMediaFormat;
    }

    private static MediaFormat getMediaFormatFromKey(final Context context,
                                                     final String formatKey) {
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
    private static int compareAudioStreamBitrate(final AudioStream streamA,
                                                 final AudioStream streamB,
                                                 final List<MediaFormat> formatRanking) {
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
        return formatRanking.indexOf(streamA.getFormat())
                - formatRanking.indexOf(streamB.getFormat());
    }

    private static int compareVideoStreamResolution(final String r1, final String r2) {
        int res1 = Integer.parseInt(r1.replaceAll("0p\\d+$", "1")
                .replaceAll("[^\\d.]", ""));
        int res2 = Integer.parseInt(r2.replaceAll("0p\\d+$", "1")
                .replaceAll("[^\\d.]", ""));
        return res1 - res2;
    }

    // Compares the quality of two video streams.
    private static int compareVideoStreamResolution(final VideoStream streamA,
                                                    final VideoStream streamB) {
        if (streamA == null) {
            return -1;
        }
        if (streamB == null) {
            return 1;
        }

        int resComp = compareVideoStreamResolution(streamA.getResolution(),
                streamB.getResolution());
        if (resComp != 0) {
            return resComp;
        }

        // Same bitrate and format
        return ListHelper.VIDEO_FORMAT_QUALITY_RANKING.indexOf(streamA.getFormat())
                - ListHelper.VIDEO_FORMAT_QUALITY_RANKING.indexOf(streamB.getFormat());
    }


    private static boolean isLimitingDataUsage(final Context context) {
        return getResolutionLimit(context) != null;
    }

    /**
     * The maximum resolution allowed.
     *
     * @param context App context
     * @return maximum resolution allowed or null if there is no maximum
     */
    private static String getResolutionLimit(final Context context) {
        String resolutionLimit = null;
        if (isMeteredNetwork(context)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String defValue = context.getString(R.string.limit_data_usage_none_key);
            String value = preferences.getString(
                    context.getString(R.string.limit_mobile_data_usage_key), defValue);
            resolutionLimit = defValue.equals(value) ? null : value;
        }
        return resolutionLimit;
    }

    /**
     * The current network is metered (like mobile data)?
     *
     * @param context App context
     * @return {@code true} if connected to a metered network
     */
    private static boolean isMeteredNetwork(final Context context) {
        ConnectivityManager manager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null || manager.getActiveNetworkInfo() == null) {
            return false;
        }

        return manager.isActiveNetworkMetered();
    }
}
