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

import java.util.*;
import java.util.stream.Collectors;

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
    // Use a HashSet for better performance
    private static final Set<String> HIGH_RESOLUTION_LIST = new HashSet<>(
            Arrays.asList("1440p", "2160p"));

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
     * @return index of the video stream with the default index
     */
    public static int getPopupDefaultResolutionIndex(final Context context,
                                                     final List<VideoStream> videoStreams) {
        final String defaultResolution = computeDefaultResolution(context,
                R.string.default_popup_resolution_key, R.string.default_popup_resolution_value);
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams);
    }

    /**
     * Finds the stream matching a quality selected on a previous video.
     *
     * <p>Stream list positions are not stable between videos. Match the selected resolution and
     * codec family instead, preferring an exact frame-rate variant when available, then the same
     * effective resolution, and finally the closest lower resolution.</p>
     *
     * @param targetResolution resolution selected by the user
     * @param targetCodec codec selected by the user, or {@code null} if it is unknown
     * @param videoStreams streams available for the new video
     * @return a valid stream index, or {@code -1} if the stream list is empty
     */
    public static int getResolutionAndCodecIndex(
            @NonNull final String targetResolution,
            @Nullable final String targetCodec,
            @Nullable final List<VideoStream> videoStreams) {
        if (videoStreams == null || videoStreams.isEmpty()) {
            return -1;
        }

        final String normalizedTarget = normalizeResolutionKey(targetResolution);
        final String codecFamily = codecFamilyOf(targetCodec);
        int index;

        if (codecFamily != null) {
            index = findBestVideoStreamIndex(videoStreams, targetResolution, null, codecFamily);
            if (index >= 0) {
                return index;
            }
            index = findBestVideoStreamIndex(videoStreams, null, normalizedTarget, codecFamily);
            if (index >= 0) {
                return index;
            }
        }

        index = findBestVideoStreamIndex(videoStreams, targetResolution, null, null);
        if (index >= 0) {
            return index;
        }
        index = findBestVideoStreamIndex(videoStreams, null, normalizedTarget, null);
        if (index >= 0) {
            return index;
        }

        String fallbackResolution = null;
        for (final VideoStream stream : videoStreams) {
            final String resolution = normalizeResolutionKey(
                    Objects.toString(stream.getResolution(), ""));
            if (compareVideoStreamResolution(resolution, normalizedTarget) < 0
                    && (fallbackResolution == null || compareVideoStreamResolution(
                    resolution, fallbackResolution) > 0)) {
                fallbackResolution = resolution;
            }
        }
        if (fallbackResolution == null) {
            for (final VideoStream stream : videoStreams) {
                final String resolution = normalizeResolutionKey(
                        Objects.toString(stream.getResolution(), ""));
                if (fallbackResolution == null || compareVideoStreamResolution(
                        resolution, fallbackResolution) < 0) {
                    fallbackResolution = resolution;
                }
            }
        }

        if (codecFamily != null) {
            index = findBestVideoStreamIndex(
                    videoStreams, null, fallbackResolution, codecFamily);
            if (index >= 0) {
                return index;
            }
        }
        index = findBestVideoStreamIndex(videoStreams, null, fallbackResolution, null);
        return index >= 0 ? index : 0;
    }

    public static int getDefaultAudioFormat(final Context context,
                                            final List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return -1;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String preferredAudioLanguage = prefs.getString(
                context.getString(R.string.preferred_audio_language_key), "original");

        List<AudioStream> filteredStreams = filterAudioStreamsByLanguage(
                audioStreams, preferredAudioLanguage);

        if (filteredStreams.isEmpty()) {
            filteredStreams = audioStreams;
        }

        final AudioStream selectedStream;
        if (isLimitingDataUsage(context)) {
            selectedStream = getMostCompactAudioStream(null, filteredStreams);
        } else {
            selectedStream = getHighestQualityAudioStream(null, filteredStreams);
        }

        if (selectedStream == null) {
            return -1;
        }

        for (int i = 0; i < audioStreams.size(); i++) {
            if (audioStreams.get(i) == selectedStream) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static AudioStream getHighestQualityAudioStream(@Nullable final MediaFormat format,
                                                            @Nullable final List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return null;
        }
        return audioStreams.stream()
                .filter(audioStream -> format == null || audioStream.getFormat() == format)
                .max((s1, s2) -> compareAudioStreamBitrate(s1, s2, AUDIO_FORMAT_QUALITY_RANKING))
                .orElse(null);
    }

    @Nullable
    private static AudioStream getMostCompactAudioStream(@Nullable final MediaFormat format,
                                                         @Nullable final List<AudioStream> audioStreams) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return null;
        }
        return audioStreams.stream()
                .filter(audioStream -> format == null || audioStream.getFormat() == format)
                .min((s1, s2) -> compareAudioStreamBitrate(s1, s2, AUDIO_FORMAT_EFFICIENCY_RANKING))
                .orElse(null);
    }

    public static List<AudioStream> filterAudioStreamsByLanguage(
            final List<AudioStream> audioStreams, final String preferredLanguage) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return Collections.emptyList();
        }

        if ("original".equals(preferredLanguage)) {
            final List<AudioStream> originalStreams = audioStreams.stream()
                    .filter(stream -> {
                        final String trackName = stream.getAudioTrackName();
                        if (trackName == null) return true;
                        final String nameLower = trackName.toLowerCase();
                        return nameLower.contains("original") || nameLower.contains("default");
                    })
                    .collect(Collectors.toList());
            if (!originalStreams.isEmpty()) {
                return originalStreams;
            }
            final List<AudioStream> noTrackInfoStreams = audioStreams.stream()
                    .filter(stream -> stream.getAudioTrackId() == null)
                    .collect(Collectors.toList());
            if (!noTrackInfoStreams.isEmpty()) {
                return noTrackInfoStreams;
            }
            return audioStreams;
        }

        final List<AudioStream> matchedStreams = audioStreams.stream()
                .filter(stream -> {
                    final String locale = stream.getAudioLocale();
                    if (locale != null && locale.equals(preferredLanguage)) {
                        return true;
                    }
                    final String trackId = stream.getAudioTrackId();
                    if (trackId != null) {
                        final String langCode = trackId.split("\\.")[0].split("-")[0];
                        return langCode.equals(preferredLanguage);
                    }
                    return false;
                })
                .collect(Collectors.toList());

        return matchedStreams.isEmpty() ? audioStreams : matchedStreams;
    }

    public static List<AudioStream> getFilteredAudioStreams(
            @NonNull final Context context,
            @Nullable final List<AudioStream> audioStreams) {
        if (audioStreams == null) {
            return Collections.emptyList();
        }

        final Map<String, AudioStream> collectedStreams = new LinkedHashMap<>();

        final Comparator<AudioStream> cmp = (s1, s2) ->
                compareAudioStreamBitrate(s1, s2, AUDIO_FORMAT_QUALITY_RANKING);

        for (final AudioStream stream : audioStreams) {
            if (stream.getDeliveryMethod() == DeliveryMethod.TORRENT
                    || (stream.getDeliveryMethod() == DeliveryMethod.HLS
                    && stream.getFormat() == MediaFormat.OPUS)) {
                continue;
            }

            final String trackId = Objects.toString(stream.getAudioTrackId(), "");

            final AudioStream presentStream = collectedStreams.get(trackId);
            if (presentStream == null || cmp.compare(stream, presentStream) > 0) {
                collectedStreams.put(trackId, stream);
            }
        }

        if (collectedStreams.size() > 1) {
            collectedStreams.remove("");
        }

        return collectedStreams.values().stream()
                .sorted(getAudioTrackNameComparator())
                .collect(Collectors.toList());
    }

    public static int getAudioFormatIndex(final Context context,
                                          final List<AudioStream> audioStreams,
                                          @Nullable final String trackId) {
        if (trackId != null) {
            for (int i = 0; i < audioStreams.size(); i++) {
                final AudioStream s = audioStreams.get(i);
                if (s.getAudioTrackId() != null
                        && s.getAudioTrackId().equals(trackId)) {
                    return i;
                }
            }
        }
        return getDefaultAudioFormat(context, audioStreams);
    }

    private static Comparator<AudioStream> getAudioTrackNameComparator() {
        return (s1, s2) -> {
            final String name1 = s1.getAudioTrackName() != null
                    ? s1.getAudioTrackName() : "";
            final String name2 = s2.getAudioTrackName() != null
                    ? s2.getAudioTrackName() : "";
            return name1.compareToIgnoreCase(name2);
        };
    }

    /**
     * Return a {@link Stream} list which uses the given delivery method from a {@link Stream}
     * list.
     *
     * @param streamList     the original stream list
     * @param deliveryMethod the delivery method
     * @param <S>            the item type's class that extends {@link Stream}
     * @return a stream list which uses the given delivery method
     */
    @NonNull
    public static <S extends Stream> List<S> keepStreamsWithDelivery(
            @NonNull final List<S> streamList,
            final DeliveryMethod deliveryMethod) {
        if (streamList.isEmpty()) {
            return Collections.emptyList();
        }

        final Iterator<S> streamListIterator = streamList.iterator();
        while (streamListIterator.hasNext()) {
            if (streamListIterator.next().getDeliveryMethod() != deliveryMethod) {
                streamListIterator.remove();
            }
        }

        return streamList;
    }

    /**
     * Return a {@link Stream} list which only contains URL streams and non-torrent streams.
     *
     * @param streamList the original stream list
     * @param <S>        the item type's class that extends {@link Stream}
     * @return a stream list which only contains URL streams and non-torrent streams
     */
    @NonNull
    public static <S extends Stream> List<S> removeNonUrlAndTorrentStreams(
            @NonNull final List<S> streamList) {
        if (streamList.isEmpty()) {
            return Collections.emptyList();
        }

        final Iterator<S> streamListIterator = streamList.iterator();
        while (streamListIterator.hasNext()) {
            final S stream = streamListIterator.next();
            if (stream.getDeliveryMethod() == DeliveryMethod.TORRENT) {
                streamListIterator.remove();
            }
        }

        return streamList;
    }

    /**
     * Return a {@link Stream} list which only contains non-torrent streams.
     *
     * @param streamList the original stream list
     * @param <S>        the item type's class that extends {@link Stream}
     * @return a stream list which only contains non-torrent streams
     */
    @NonNull
    public static <S extends Stream> List<S> removeTorrentStreams(
            @NonNull final List<S> streamList) {
        if (streamList.isEmpty()) {
            return Collections.emptyList();
        }

        final Iterator<S> streamListIterator = streamList.iterator();
        while (streamListIterator.hasNext()) {
            final S stream = streamListIterator.next();
            if (stream.getDeliveryMethod() == DeliveryMethod.TORRENT) {
                streamListIterator.remove();
            }
        }

        return streamList;
    }

    public static List<AudioStream> filterUnsupportedFormats(@NonNull final List<AudioStream> streamList,
                                                             @NonNull final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        Set<String> advancedFormats = sharedPreferences.getStringSet(context.getString(R.string.advanced_formats_key), new HashSet<>());
        boolean useDolbyAudio = advancedFormats.contains("EC-3");
        return streamList.stream()
                .filter(stream -> {
                    if (stream.getCodec() == null) {
                        return true;
                    }
                    if (stream.getCodec().toLowerCase(Locale.ROOT).equals("flac")) {
                        return false; // flac support has issue: InsufficientCapacityException, at least for BiliBili
                    } else if (stream.getCodec().equals("ec-3")) {
                        return useDolbyAudio;
                    } else {
                        return true;
                    }
                }).collect(Collectors.toList());
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
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        Set<String> advancedFormats = sharedPreferences.getStringSet(context.getString(R.string.advanced_formats_key), new HashSet<>());
        return getSortedStreamVideosList(advancedFormats, videoStreams,
                videoOnlyStreams, ascendingOrder, preferVideoOnlyStreams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private static String computeDefaultResolution(final Context context, final int key,
                                                   final int value) {
        final SharedPreferences preferences
                = PreferenceManager.getDefaultSharedPreferences(context);

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
     * Convert any 720p60/HFR/HDR/… string into a pure numeric key so they compare equal:
     * 1080p60 → 1080p, 1440p HDR → 1440p, etc.
     */
    private static String normalizeResolutionKey(@NonNull final String raw) {
        return raw.replaceAll("(?i)p.*", "p");  // CASE-insensitive removal after the "p"
    }

    @Nullable
    private static String codecFamilyOf(@Nullable final String codec) {
        if (codec == null || codec.isEmpty()) {
            return null;
        }
        final String normalized = codec.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("av01")) {
            return "av1";
        }
        if (normalized.startsWith("vp09") || normalized.startsWith("vp9")) {
            return "vp9";
        }
        if (normalized.startsWith("hev1") || normalized.startsWith("hvc1")) {
            return "hevc";
        }
        if (normalized.startsWith("avc")) {
            return "avc";
        }
        final int separator = normalized.indexOf('.');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }

    private static int findBestVideoStreamIndex(
            @NonNull final List<VideoStream> streams,
            @Nullable final String exactResolution,
            @Nullable final String normalizedResolution,
            @Nullable final String codecFamily) {
        int bestIndex = -1;
        for (int i = 0; i < streams.size(); i++) {
            final VideoStream stream = streams.get(i);
            final String resolution = Objects.toString(stream.getResolution(), "");
            final boolean resolutionMatches = exactResolution != null
                    ? exactResolution.equals(resolution)
                    : normalizedResolution != null && normalizedResolution.equals(
                    normalizeResolutionKey(resolution));
            if (!resolutionMatches || (codecFamily != null
                    && !codecFamily.equals(codecFamilyOf(stream.getCodec())))) {
                continue;
            }
            if (bestIndex < 0 || compareVideoStreamResolution(
                    stream, streams.get(bestIndex)) > 0) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }


    /**
     * Core selection logic that groups by *effective* resolution (ignoring suffixes),
     * then selects inside each bucket via bitrate / codec-rank.
     */
    static int getDefaultResolutionIndex(@NonNull final String targetRes,
                                         @NonNull final String bestResolutionKey,
                                         @Nullable final MediaFormat filterFormat,
                                         @Nullable final List<VideoStream> streams) {

        if (streams == null || streams.isEmpty()) {
            return -1;
        }

        // 1. User picked the best resolution key → simply return the highest actual stream
        if (bestResolutionKey.equals(targetRes)) {
            return streams.indexOf(
                    Collections.max(streams, ListHelper::compareVideoStreamResolution));
        }

        // 2. Strip suffixes to group variants together:   1080p HDR → 1080p
        final String normalizedTarget = normalizeResolutionKey(targetRes);

        // 3. Build the bucket of streams that share the same effective resolution
        List<VideoStream> bucket = new ArrayList<>();
        for (VideoStream s : streams) {
            if (normalizedTarget.equals(normalizeResolutionKey(s.getResolution()))) {
                if (filterFormat == null || s.getFormat() == filterFormat) {
                    bucket.add(s);
                }
            }
        }

        // 4. No exact format match? Drop the format filter and use everything.
        if (bucket.isEmpty() && filterFormat != null) {
            for (VideoStream s : streams) {
                if (normalizedTarget.equals(normalizeResolutionKey(s.getResolution()))) {
                    bucket.add(s);
                }
            }
        }

        if (bucket.isEmpty()) {
            String fallbackResolution = null;
            for (final VideoStream stream : streams) {
                final String resolution = normalizeResolutionKey(stream.getResolution());
                if (compareVideoStreamResolution(resolution, normalizedTarget) < 0
                        && (fallbackResolution == null || compareVideoStreamResolution(
                        resolution, fallbackResolution) > 0)) {
                    fallbackResolution = resolution;
                }
            }

            if (fallbackResolution == null) {
                for (final VideoStream stream : streams) {
                    final String resolution = normalizeResolutionKey(stream.getResolution());
                    if (fallbackResolution == null || compareVideoStreamResolution(
                            resolution, fallbackResolution) < 0) {
                        fallbackResolution = resolution;
                    }
                }
            }

            for (final VideoStream stream : streams) {
                if (fallbackResolution.equals(normalizeResolutionKey(stream.getResolution()))
                        && (filterFormat == null || stream.getFormat() == filterFormat)) {
                    bucket.add(stream);
                }
            }

            if (bucket.isEmpty() && filterFormat != null) {
                for (final VideoStream stream : streams) {
                    if (fallbackResolution.equals(
                            normalizeResolutionKey(stream.getResolution()))) {
                        bucket.add(stream);
                    }
                }
            }
        }

        bucket.sort(ListHelper::compareVideoStreamResolution);
        final VideoStream best = bucket.get(bucket.size() - 1);
        return streams.indexOf(best);
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
            final Set<String> advancedFormats,
            @Nullable final List<VideoStream> videoStreams,
            @Nullable final List<VideoStream> videoOnlyStreams,
            final boolean ascendingOrder,
            final boolean preferVideoOnlyStreams
    ) {
        boolean useWebM = advancedFormats.contains("VP9");
        boolean useAV1 = advancedFormats.contains("AV01");
        boolean useH265 = advancedFormats.contains("HEVC");
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
                .filter(stream -> {
                    try {
                        if (stream.getFormat() == MediaFormat.WEBM) {
                            return useWebM;
                        } else if (stream.getCodec().startsWith("av01")) {
                            return useAV1;
                        } else if (stream.getCodec().startsWith("hev1") || stream.getCodec().startsWith("hvc1")) {
                            return useH265;
                        } else {
                            return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Return the sorted list
        final HashMap<String, VideoStream> hashMap = new HashMap<>();
        for (final VideoStream videoStream : allInitialStreams) {
            final String trackSuffix = videoStream.getAudioTrackId() != null
                    ? "|" + videoStream.getAudioTrackId() : "";
            hashMap.put(videoStream.getCodec().split("\\.")[0]
                    + videoStream.getResolution() + trackSuffix, videoStream);
        }

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
        final Comparator<VideoStream> comparator = ListHelper::compareVideoStreamResolution;
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
                (s1, s2) -> compareAudioStreamBitrate(s1, s2, AUDIO_FORMAT_QUALITY_RANKING)
        );
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
                // The "-" is important -> Compares ascending (first = highest rank)
                (s1, s2) -> -compareAudioStreamBitrate(s1, s2, AUDIO_FORMAT_EFFICIENCY_RANKING)
        );
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
            final MediaFormat format
                    = targetFormat == null ? null : videoStreams.get(idx).getFormat();
            final String resolution = videoStreams.get(idx).getResolution();
            final String resolutionNoRefresh = resolution.replaceAll("p\\d+$", "p");

            if (format == targetFormat && calculateResolution(resolution) == calculateResolution(targetResolution)) {
                fullMatchIndex = idx;
            }

            if (format == targetFormat && calculateResolution(resolutionNoRefresh) == calculateResolution(targetResolutionNoRefresh)) {
                fullMatchNoRefreshIndex = idx;
            }

            if (resMatchOnlyIndex == -1 && calculateResolution(resolution) == calculateResolution(targetResolution)) {
                resMatchOnlyIndex = idx;
            }

            if (resMatchOnlyNoRefreshIndex == -1
                    && calculateResolution(resolutionNoRefresh) == calculateResolution(targetResolutionNoRefresh)) {
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
        if (lowerResMatchNoRefreshIndex != -1) {
            return lowerResMatchNoRefreshIndex;
        }
        return videoStreams.size() - 1;
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
//        final MediaFormat defaultFormat = MediaFormat.MPEG_4;
        return getDefaultResolutionIndex(defaultResolution,
                context.getString(R.string.best_resolution_key), null, videoStreams);
    }

    private static MediaFormat getDefaultFormat(@NonNull final Context context,
                                                @StringRes final int defaultFormatKey,
                                                @StringRes final int defaultFormatValueKey) {
        final SharedPreferences preferences
                = PreferenceManager.getDefaultSharedPreferences(context);

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

    public static int calculateResolution(String x){
        int res = 0;
        if(x.contains("8K")) {
            res = 4320;
        } else if(x.contains("4K")) {
            res = 2160;
        } else if(x.contains("高帧率")) {
            res = 1083;
        } else if(x.contains("高码率")) {
            res = 1082;
        } else if (x.contains("HDR")) {
            res = 2162;
        } else if (x.contains("杜比")){
            res = 2163;
        } else if (x.contains("低画質")) {
            res = 240;
        } else{
            res = Integer.parseInt(x.replaceAll("0p\\d+$", "1")
                    .replaceAll("[^\\d.]", ""));
        }
        return res;
    }

    private static int compareVideoStreamResolution(@NonNull final String r1,
                                                    @NonNull final String r2) {

        try {
            return calculateResolution(r1) - calculateResolution(r2);
        } catch (final NumberFormatException e) {
            // Consider the first one greater because we don't know if the two streams are
            // different or not (a NumberFormatException was thrown so we don't know the resolution
            // of one stream or of all streams)
            return 1;
        }
    }

    private static int getCodecPriority(final VideoStream stream) {
        final String codecFamily = codecFamilyOf(stream.getCodec());
        if (codecFamily == null) {
            return 0;
        }
        switch (codecFamily) {
            case "av1":
                return 4;
            case "vp9":
                return 3;
            case "hevc":
                return 2;
            case "avc":
                return 1;
            default:
                return 0;
        }
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

        final int resComp = compareVideoStreamResolution(streamA.getResolution(),
                streamB.getResolution());
        if (resComp != 0) {
            return resComp;
        }

        final int codecComp = getCodecPriority(streamA) - getCodecPriority(streamB);
        if (codecComp != 0) {
            return codecComp;
        }

        if (streamA.getBitrate() - streamB.getBitrate() != 0) {
            return - streamA.getBitrate() + streamB.getBitrate();
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
    private static String getResolutionLimit(@NonNull final Context context) {
        String resolutionLimit = null;
        if (isMeteredNetwork(context)) {
            final SharedPreferences preferences
                    = PreferenceManager.getDefaultSharedPreferences(context);
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
        final ConnectivityManager manager
                = ContextCompat.getSystemService(context, ConnectivityManager.class);
        if (manager == null || manager.getActiveNetworkInfo() == null) {
            return false;
        }

        return manager.isActiveNetworkMetered();
    }

    public static List<VideoStream> filterVideoStreamsByPreferredLanguage(
            final Context context,
            final List<VideoStream> videoStreams,
            final List<AudioStream> audioStreams) {
        final boolean hasAudioTracks = videoStreams.stream()
                .anyMatch(s -> s.getAudioTrackId() != null);
        if (!hasAudioTracks) {
            return videoStreams;
        }
        final List<AudioStream> filtered = getFilteredAudioStreams(context, audioStreams);
        final int defaultIdx = getDefaultAudioFormat(context, filtered);
        String defaultTrackId = null;
        if (defaultIdx >= 0 && defaultIdx < filtered.size()) {
            defaultTrackId = filtered.get(defaultIdx).getAudioTrackId();
        }
        if (defaultTrackId == null) {
            return collapseAudioTrackVariants(videoStreams);
        }
        final String trackId = defaultTrackId;
        final List<VideoStream> result = new ArrayList<>();
        for (final VideoStream s : videoStreams) {
            if (trackId.equals(s.getAudioTrackId()) || s.getAudioTrackId() == null) {
                result.add(s);
            }
        }
        return result.isEmpty()
                ? collapseAudioTrackVariants(videoStreams)
                : collapseAudioTrackVariants(result);
    }

    private static List<VideoStream> collapseAudioTrackVariants(final List<VideoStream> videoStreams) {
        final Map<String, VideoStream> result = new LinkedHashMap<>();
        for (final VideoStream stream : videoStreams) {
            final String key = stream.getFormat() + "|"
                    + getCodecFamily(stream) + "|" + stream.getResolution() + "|"
                    + stream.isVideoOnly();
            final VideoStream existing = result.get(key);
            if (existing == null || shouldPreferCollapsedVariant(existing, stream)) {
                result.put(key, stream);
            }
        }
        return new ArrayList<>(result.values());
    }

    private static boolean shouldPreferCollapsedVariant(final VideoStream existing,
                                                        final VideoStream candidate) {
        if (existing.getDeliveryMethod() == DeliveryMethod.HLS
                && candidate.getDeliveryMethod() != DeliveryMethod.HLS) {
            return true;
        }
        return isOriginalAudioTrack(candidate) && !isOriginalAudioTrack(existing);
    }

    private static String getCodecFamily(final VideoStream stream) {
        final String codec = stream.getCodec();
        if (codec == null || codec.isEmpty()) {
            return "";
        }
        return codec.split("\\.")[0];
    }

    private static boolean isOriginalAudioTrack(final VideoStream stream) {
        final String trackName = stream.getAudioTrackName();
        return trackName != null && trackName.toLowerCase(Locale.ROOT).contains("original");
    }

    public static List<AudioStream> filterDownloadableAudioStreams(
            final List<AudioStream> audioStreams) {
        final List<AudioStream> result = new ArrayList<>();
        for (final AudioStream a : audioStreams) {
            if (a.getDeliveryMethod() != DeliveryMethod.HLS || a.isUrl()) {
                result.add(a);
            }
        }
        return result;
    }
}
