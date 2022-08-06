package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ListHelper {
    // Video format in order of quality. 0=lowest quality, n=highest quality
    private static final List<MediaFormat> VIDEO_FORMAT_QUALITY_RANKING =
            List.of(MediaFormat.v3GPP, MediaFormat.WEBM, MediaFormat.MPEG_4);

    // Audio format in order of quality. 0=lowest quality, n=highest quality
    private static final List<MediaFormat> AUDIO_FORMAT_QUALITY_RANKING =
            List.of(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A);
    // Audio format in order of efficiency. 0=most efficient, n=least efficient
    private static final List<MediaFormat> AUDIO_FORMAT_EFFICIENCY_RANKING =
            List.of(MediaFormat.WEBMA, MediaFormat.M4A, MediaFormat.MP3);
    // Use a Set for better performance
    private static final Set<String> HIGH_RESOLUTION_LIST = Set.of("1440p", "2160p");

    private ListHelper() { }

    /**
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     * @param context      Android app context
     * @param videoStreams list of the video streams to check
     * @return index of the video stream with the default index
     */
    public static int getDefaultResolutionIndex(final Context context,
                                                final List<VideoStream> videoStreams) {
        final String defaultResolution = computeDefaultResolution(context,
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
        final String defaultResolution = computeDefaultResolution(context,
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
        final MediaFormat defaultFormat = getDefaultFormat(context,
                R.string.default_audio_format_key, R.string.default_audio_format_value);

        // If the user has chosen to limit resolution to conserve mobile data
        // usage then we should also limit our audio usage.
        if (isLimitingDataUsage(context)) {
            return getMostCompactAudioIndex(defaultFormat, audioStreams);
        } else {
            return getHighestQualityAudioIndex(defaultFormat, audioStreams);
        }
    }

    /**
     * Return a {@link Stream} list which uses the given delivery method from a {@link Stream}
     * list.
     *
     * @param streamList     the original {@link Stream stream} list
     * @param deliveryMethod the {@link DeliveryMethod delivery method}
     * @param <S>            the item type's class that extends {@link Stream}
     * @return a {@link Stream stream} list which uses the given delivery method
     */
    @NonNull
    public static <S extends Stream> List<S> getStreamsOfSpecifiedDelivery(
            final List<S> streamList,
            final DeliveryMethod deliveryMethod) {
        return getFilteredStreamList(streamList,
                stream -> stream.getDeliveryMethod() == deliveryMethod);
    }

    /**
     * Return a {@link Stream} list which only contains URL streams and non-torrent streams.
     *
     * @param streamList the original stream list
     * @param <S>        the item type's class that extends {@link Stream}
     * @return a stream list which only contains URL streams and non-torrent streams
     */
    @NonNull
    public static <S extends Stream> List<S> getUrlAndNonTorrentStreams(
            final List<S> streamList) {
        return getFilteredStreamList(streamList,
                stream -> stream.isUrl() && stream.getDeliveryMethod() != DeliveryMethod.TORRENT);
    }

    /**
     * Return a {@link Stream} list which only contains non-torrent streams.
     *
     * @param streamList the original stream list
     * @param <S>        the item type's class that extends {@link Stream}
     * @return a stream list which only contains non-torrent streams
     */
    @NonNull
    public static <S extends Stream> List<S> getNonTorrentStreams(
            final List<S> streamList) {
        return getFilteredStreamList(streamList,
                stream -> stream.getDeliveryMethod() != DeliveryMethod.TORRENT);
    }

    /**
     * Join the two lists of video streams (video_only and normal videos),
     * and sort them according with default format chosen by the user.
     *
     * @param context                the context to search for the format to give preference
     * @param videoStreams           the normal videos list
     * @param videoOnlyStreams       the video-only stream list
     * @param ascendingOrder         true -> smallest to greatest | false -> greatest to smallest
     * @param preferVideoOnlyStreams if video-only streams should preferred when both video-only
     *                               streams and normal video streams are available
     * @return the sorted list
     */
    @NonNull
    public static List<VideoStream> getSortedStreamVideosList(
            @NonNull final Context context,
            @Nullable final List<VideoStream> videoStreams,
            @Nullable final List<VideoStream> videoOnlyStreams,
            final boolean ascendingOrder,
            final boolean preferVideoOnlyStreams) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        final boolean showHigherResolutions = preferences.getBoolean(
                context.getString(R.string.show_higher_resolutions_key), false);
        final MediaFormat defaultFormat = getDefaultFormat(context,
                R.string.default_video_format_key, R.string.default_video_format_value);

        return getSortedStreamVideosList(defaultFormat, showHigherResolutions, videoStreams,
                videoOnlyStreams, ascendingOrder, preferVideoOnlyStreams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Get a filtered stream list, by using Java 8 Stream's API and the given predicate.
     *
     * @param streamList          the stream list to filter
     * @param streamListPredicate the predicate which will be used to filter streams
     * @param <S>                 the item type's class that extends {@link Stream}
     * @return a new stream list filtered using the given predicate
     */
    private static <S extends Stream> List<S> getFilteredStreamList(
            final List<S> streamList,
            final Predicate<S> streamListPredicate) {
        if (streamList == null) {
            return Collections.emptyList();
        }

        return streamList.stream()
                .filter(streamListPredicate)
                .collect(Collectors.toList());
    }

    private static String computeDefaultResolution(final Context context, final int key,
                                                   final int value) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        // Load the preferred resolution otherwise the best available
        String resolution = preferences != null
                ? preferences.getString(context.getString(key), context.getString(value))
                : context.getString(R.string.best_resolution_key);

        final String maxResolution = getResolutionLimit(context);
        if (maxResolution != null
                && (resolution.equals(context.getString(R.string.best_resolution_key))
                || compareVideoStreamResolution(maxResolution, resolution) < 1)) {
            resolution = maxResolution;
        }
        return resolution;
    }

    /**
     * Return the index of the default stream in the list, that will be sorted in the process, based
     * on the parameters defaultResolution and defaultFormat.
     *
     * @param defaultResolution the default resolution to look for
     * @param bestResolutionKey key of the best resolution
     * @param defaultFormat     the default format to look for
     * @param videoStreams      a mutable list of the video streams to check (it will be sorted in
     *                          place)
     * @return index of the default resolution&format in the sorted videoStreams
     */
    static int getDefaultResolutionIndex(final String defaultResolution,
                                         final String bestResolutionKey,
                                         final MediaFormat defaultFormat,
                                         @Nullable final List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) {
            return -1;
        }

        sortStreamList(videoStreams, false);
        if (defaultResolution.equals(bestResolutionKey)) {
            return 0;
        }

        final int defaultStreamIndex =
                getVideoStreamIndex(defaultResolution, defaultFormat, videoStreams);

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
     * @param defaultFormat          format to give preference
     * @param showHigherResolutions  show >1080p resolutions
     * @param videoStreams           normal videos list
     * @param videoOnlyStreams       video only stream list
     * @param ascendingOrder         true -> smallest to greatest | false -> greatest to smallest
     * @param preferVideoOnlyStreams if video-only streams should preferred when both video-only
     *                               streams and normal video streams are available
     * @return the sorted list
     */
    @NonNull
    static List<VideoStream> getSortedStreamVideosList(
            @Nullable final MediaFormat defaultFormat,
            final boolean showHigherResolutions,
            @Nullable final List<VideoStream> videoStreams,
            @Nullable final List<VideoStream> videoOnlyStreams,
            final boolean ascendingOrder,
            final boolean preferVideoOnlyStreams
    ) {
        // Determine order of streams
        // The last added list is preferred
        final List<List<VideoStream>> videoStreamsOrdered =
                preferVideoOnlyStreams
                        ? Arrays.asList(videoStreams, videoOnlyStreams)
                        : Arrays.asList(videoOnlyStreams, videoStreams);

        final List<VideoStream> allInitialStreams = videoStreamsOrdered.stream()
                // Ignore lists that are null
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                // Filter out higher resolutions (or not if high resolutions should always be shown)
                .filter(stream -> showHigherResolutions
                        || !HIGH_RESOLUTION_LIST.contains(stream.getResolution()
                                // Replace any frame rate with nothing
                                .replaceAll("p\\d+$", "p")))
                .collect(Collectors.toList());

        final HashMap<String, VideoStream> hashMap = new HashMap<>();
        // Add all to the hashmap
        for (final VideoStream videoStream : allInitialStreams) {
            hashMap.put(videoStream.getResolution(), videoStream);
        }

        // Override the values when the key == resolution, with the defaultFormat
        for (final VideoStream videoStream : allInitialStreams) {
            if (videoStream.getFormat() == defaultFormat) {
                hashMap.put(videoStream.getResolution(), videoStream);
            }
        }

        // Return the sorted list
        return sortStreamList(new ArrayList<>(hashMap.values()), ascendingOrder);
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
     * ascendingOrder  ? 360 < 720 < 721 < 1080 < 1081
     * !ascendingOrder ? 1081 < 1080 < 721 < 720 < 360</pre></blockquote>
     *
     * @param videoStreams   list that the sorting will be applied
     * @param ascendingOrder true -> smallest to greatest | false -> greatest to smallest
     * @return The sorted list (same reference as parameter videoStreams)
     */
    private static List<VideoStream> sortStreamList(final List<VideoStream> videoStreams,
                                                    final boolean ascendingOrder) {
        // Compares the quality of two video streams.
        final Comparator<VideoStream> comparator = Comparator.nullsLast(Comparator
                .comparing(VideoStream::getResolution, ListHelper::compareVideoStreamResolution)
                .thenComparingInt(s -> VIDEO_FORMAT_QUALITY_RANKING.indexOf(s.getFormat())));
        Collections.sort(videoStreams, ascendingOrder ? comparator : comparator.reversed());
        return videoStreams;
    }

    /**
     * Get the audio from the list with the highest quality.
     * Format will be ignored if it yields no results.
     *
     * @param format       The target format type or null if it doesn't matter
     * @param audioStreams List of audio streams
     * @return Index of audio stream that produces the most compact results or -1 if not found
     */
    static int getHighestQualityAudioIndex(@Nullable final MediaFormat format,
                                           @Nullable final List<AudioStream> audioStreams) {
        return getAudioIndexByHighestRank(format, audioStreams,
                // Compares descending (last = highest rank)
                getAudioStreamComparator(AUDIO_FORMAT_QUALITY_RANKING));
    }

    /**
     * Get the audio from the list with the lowest bitrate and most efficient format.
     * Format will be ignored if it yields no results.
     *
     * @param format       The target format type or null if it doesn't matter
     * @param audioStreams List of audio streams
     * @return Index of audio stream that produces the most compact results or -1 if not found
     */
    static int getMostCompactAudioIndex(@Nullable final MediaFormat format,
                                        @Nullable final List<AudioStream> audioStreams) {
        return getAudioIndexByHighestRank(format, audioStreams,
                // The "reversed()" is important -> Compares ascending (first = highest rank)
                getAudioStreamComparator(AUDIO_FORMAT_EFFICIENCY_RANKING).reversed());
    }

    private static Comparator<AudioStream> getAudioStreamComparator(
            final List<MediaFormat> formatRanking) {
        return Comparator.nullsLast(Comparator.comparingInt(AudioStream::getAverageBitrate))
                .thenComparingInt(stream -> formatRanking.indexOf(stream.getFormat()));
    }

    /**
     * Get the audio-stream from the list with the highest rank, depending on the comparator.
     * Format will be ignored if it yields no results.
     *
     * @param targetedFormat The target format type or null if it doesn't matter
     * @param audioStreams   List of audio streams
     * @param comparator     The comparator used for determining the max/best/highest ranked value
     * @return Index of audio stream that produces the highest ranked result or -1 if not found
     */
    private static int getAudioIndexByHighestRank(@Nullable final MediaFormat targetedFormat,
                                                  @Nullable final List<AudioStream> audioStreams,
                                                  final Comparator<AudioStream> comparator) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return -1;
        }

        final AudioStream highestRankedAudioStream = audioStreams.stream()
                .filter(audioStream -> targetedFormat == null
                        || audioStream.getFormat() == targetedFormat)
                .max(comparator)
                .orElse(null);

        if (highestRankedAudioStream == null) {
            // Fallback: Ignore targetedFormat if not null
            if (targetedFormat != null) {
                return getAudioIndexByHighestRank(null, audioStreams, comparator);
            }
            // targetedFormat is already null -> return -1
            return -1;
        }

        return audioStreams.indexOf(highestRankedAudioStream);
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
     * @return the index of the preferred video stream
     */
    static int getVideoStreamIndex(@NonNull final String targetResolution,
                                   final MediaFormat targetFormat,
                                   @NonNull final List<VideoStream> videoStreams) {
        int fullMatchIndex = -1;
        int fullMatchNoRefreshIndex = -1;
        int resMatchOnlyIndex = -1;
        int resMatchOnlyNoRefreshIndex = -1;
        int lowerResMatchNoRefreshIndex = -1;
        final String targetResolutionNoRefresh = targetResolution.replaceAll("p\\d+$", "p");

        for (int idx = 0; idx < videoStreams.size(); idx++) {
            final MediaFormat format = targetFormat == null
                    ? null
                    : videoStreams.get(idx).getFormat();
            final String resolution = videoStreams.get(idx).getResolution();
            final String resolutionNoRefresh = resolution.replaceAll("p\\d+$", "p");

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
     * @return the index of the preferred video stream
     */
    private static int getDefaultResolutionWithDefaultFormat(@NonNull final Context context,
                                                             final String defaultResolution,
                                                             final List<VideoStream> videoStreams) {
        final MediaFormat defaultFormat = getDefaultFormat(context,
                R.string.default_video_format_key, R.string.default_video_format_value);
        return getDefaultResolutionIndex(defaultResolution,
                context.getString(R.string.best_resolution_key), defaultFormat, videoStreams);
    }

    private static MediaFormat getDefaultFormat(@NonNull final Context context,
                                                @StringRes final int defaultFormatKey,
                                                @StringRes final int defaultFormatValueKey) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        final String defaultFormat = context.getString(defaultFormatValueKey);
        final String defaultFormatString = preferences.getString(
                context.getString(defaultFormatKey), defaultFormat);

        MediaFormat defaultMediaFormat = getMediaFormatFromKey(context, defaultFormatString);
        if (defaultMediaFormat == null) {
            preferences.edit().putString(context.getString(defaultFormatKey), defaultFormat)
                    .apply();
            defaultMediaFormat = getMediaFormatFromKey(context, defaultFormat);
        }

        return defaultMediaFormat;
    }

    private static MediaFormat getMediaFormatFromKey(@NonNull final Context context,
                                                     @NonNull final String formatKey) {
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

    private static int compareVideoStreamResolution(@NonNull final String r1,
                                                    @NonNull final String r2) {
        try {
            final int res1 = Integer.parseInt(r1.replaceAll("0p\\d+$", "1")
                    .replaceAll("[^\\d.]", ""));
            final int res2 = Integer.parseInt(r2.replaceAll("0p\\d+$", "1")
                    .replaceAll("[^\\d.]", ""));
            return res1 - res2;
        } catch (final NumberFormatException e) {
            // Consider the first one greater because we don't know if the two streams are
            // different or not (a NumberFormatException was thrown so we don't know the resolution
            // of one stream or of all streams)
            return 1;
        }
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
    private static String getResolutionLimit(@NonNull final Context context) {
        String resolutionLimit = null;
        if (isMeteredNetwork(context)) {
            final SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(context);
            final String defValue = context.getString(R.string.limit_data_usage_none_key);
            final String value = preferences.getString(
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
    public static boolean isMeteredNetwork(@NonNull final Context context) {
        final ConnectivityManager manager =
                ContextCompat.getSystemService(context, ConnectivityManager.class);
        if (manager == null || manager.getActiveNetworkInfo() == null) {
            return false;
        }

        return manager.isActiveNetworkMetered();
    }
}
