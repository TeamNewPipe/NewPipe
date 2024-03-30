package org.schabi.newpipe.util

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.Objects
import java.util.function.Predicate
import java.util.stream.Collectors

object ListHelper {
    // Video format in order of quality. 0=lowest quality, n=highest quality
    private val VIDEO_FORMAT_QUALITY_RANKING = java.util.List.of(MediaFormat.v3GPP, MediaFormat.WEBM, MediaFormat.MPEG_4)

    // Audio format in order of quality. 0=lowest quality, n=highest quality
    private val AUDIO_FORMAT_QUALITY_RANKING = java.util.List.of(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A)

    // Audio format in order of efficiency. 0=least efficient, n=most efficient
    private val AUDIO_FORMAT_EFFICIENCY_RANKING = java.util.List.of(MediaFormat.MP3, MediaFormat.M4A, MediaFormat.WEBMA)

    // Use a Set for better performance
    private val HIGH_RESOLUTION_LIST = setOf("1440p", "2160p")

    // Audio track types in order of priority. 0=lowest, n=highest
    private val AUDIO_TRACK_TYPE_RANKING = java.util.List.of(AudioTrackType.DESCRIPTIVE, AudioTrackType.DUBBED, AudioTrackType.ORIGINAL)

    // Audio track types in order of priority when descriptive audio is preferred.
    private val AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE = java.util.List.of(AudioTrackType.ORIGINAL, AudioTrackType.DUBBED, AudioTrackType.DESCRIPTIVE)

    /**
     * List of supported YouTube Itag ids.
     * The original order is kept.
     * @see {@link org.schabi.newpipe.extractor.services.youtube.ItagItem.ITAG_LIST}
     */
    private val SUPPORTED_ITAG_IDS = listOf(
            17, 36,  // video v3GPP
            18, 34, 35, 59, 78, 22, 37, 38,  // video MPEG4
            43, 44, 45, 46,  // video webm
            171, 172, 139, 140, 141, 249, 250, 251,  // audio
            160, 133, 134, 135, 212, 136, 298, 137, 299, 266,  // video only
            278, 242, 243, 244, 245, 246, 247, 248, 271, 272, 302, 303, 308, 313, 315
    )

    /**
     * @param context      Android app context
     * @param videoStreams list of the video streams to check
     * @return index of the video stream with the default index
     * @see .getDefaultResolutionIndex
     */
    fun getDefaultResolutionIndex(context: Context,
                                  videoStreams: List<VideoStream?>): Int {
        val defaultResolution = computeDefaultResolution(context,
                R.string.default_resolution_key, R.string.default_resolution_value)
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @param context           Android app context
     * @param videoStreams      list of the video streams to check
     * @param defaultResolution the default resolution to look for
     * @return index of the video stream with the default index
     * @see .getDefaultResolutionIndex
     */
    fun getResolutionIndex(context: Context,
                           videoStreams: List<VideoStream?>,
                           defaultResolution: String?): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @param context      Android app context
     * @param videoStreams list of the video streams to check
     * @return index of the video stream with the default index
     * @see .getDefaultResolutionIndex
     */
    fun getPopupDefaultResolutionIndex(context: Context,
                                       videoStreams: List<VideoStream?>): Int {
        val defaultResolution = computeDefaultResolution(context,
                R.string.default_popup_resolution_key, R.string.default_popup_resolution_value)
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @param context           Android app context
     * @param videoStreams      list of the video streams to check
     * @param defaultResolution the default resolution to look for
     * @return index of the video stream with the default index
     * @see .getDefaultResolutionIndex
     */
    fun getPopupResolutionIndex(context: Context,
                                videoStreams: List<VideoStream?>,
                                defaultResolution: String?): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    fun getDefaultAudioFormat(context: Context?,
                              audioStreams: List<AudioStream?>?): Int {
        return getAudioIndexByHighestRank(audioStreams,
                getAudioTrackComparator(context!!).thenComparing(getAudioFormatComparator(context)))
    }

    fun getDefaultAudioTrackGroup(context: Context,
                                  groupedAudioStreams: List<List<AudioStream?>?>?): Int {
        if (groupedAudioStreams == null || groupedAudioStreams.isEmpty()) {
            return -1
        }
        val cmp = getAudioTrackComparator(context)
        val highestRanked = groupedAudioStreams.stream()
                .max { o1: List<AudioStream?>?, o2: List<AudioStream?>? -> cmp.compare(o1!![0], o2!![0]) }
                .orElse(null)
        return groupedAudioStreams.indexOf(highestRanked)
    }

    fun getAudioFormatIndex(context: Context?,
                            audioStreams: List<AudioStream?>?,
                            trackId: String?): Int {
        if (trackId != null) {
            for (i in audioStreams!!.indices) {
                val s = audioStreams[i]
                if (s!!.audioTrackId != null && s.audioTrackId == trackId) {
                    return i
                }
            }
        }
        return getDefaultAudioFormat(context, audioStreams)
    }

    /**
     * Return a [Stream] list which uses the given delivery method from a [Stream]
     * list.
     *
     * @param streamList     the original [stream][Stream] list
     * @param deliveryMethod the [delivery method][DeliveryMethod]
     * @param <S>            the item type's class that extends [Stream]
     * @return a [stream][Stream] list which uses the given delivery method
    </S> */
    fun <S : Stream?> getStreamsOfSpecifiedDelivery(
            streamList: List<S>?,
            deliveryMethod: DeliveryMethod): List<S> {
        return getFilteredStreamList(streamList
        ) { stream: S -> stream!!.deliveryMethod == deliveryMethod }
    }

    /**
     * Return a [Stream] list which only contains URL streams and non-torrent streams.
     *
     * @param streamList the original stream list
     * @param <S>        the item type's class that extends [Stream]
     * @return a stream list which only contains URL streams and non-torrent streams
    </S> */
    fun <S : Stream?> getUrlAndNonTorrentStreams(
            streamList: List<S>?): List<S> {
        return getFilteredStreamList(streamList
        ) { stream: S -> stream!!.isUrl && stream.deliveryMethod != DeliveryMethod.TORRENT }
    }

    /**
     * Return a [Stream] list which only contains streams which can be played by the player.
     *
     *
     *
     * Some formats are not supported, see [.SUPPORTED_ITAG_IDS] for more details.
     * Torrent streams are also removed, because they cannot be retrieved, like OPUS streams using
     * HLS as their delivery method, since they are not supported by ExoPlayer.
     *
     *
     * @param <S>        the item type's class that extends [Stream]
     * @param streamList the original stream list
     * @param serviceId  the service ID from which the streams' list comes from
     * @return a stream list which only contains streams that can be played the player
    </S> */
    fun <S : Stream?> getPlayableStreams(
            streamList: List<S>?, serviceId: Int): List<S> {
        val youtubeServiceId = ServiceList.YouTube.serviceId
        return getFilteredStreamList(streamList
        ) { stream: S ->
            (stream!!.deliveryMethod != DeliveryMethod.TORRENT && (stream.deliveryMethod != DeliveryMethod.HLS
                    || stream.format != MediaFormat.OPUS)
                    && (serviceId != youtubeServiceId || stream.itagItem == null || SUPPORTED_ITAG_IDS.contains(stream.itagItem!!.id)))
        }
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
     * streams and normal video streams are available
     * @return the sorted list
     */
    fun getSortedStreamVideosList(
            context: Context,
            videoStreams: List<VideoStream?>?,
            videoOnlyStreams: List<VideoStream?>?,
            ascendingOrder: Boolean,
            preferVideoOnlyStreams: Boolean): List<VideoStream?> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val showHigherResolutions = preferences.getBoolean(
                context.getString(R.string.show_higher_resolutions_key), false)
        val defaultFormat = getDefaultFormat(context,
                R.string.default_video_format_key, R.string.default_video_format_value)
        return getSortedStreamVideosList(defaultFormat, showHigherResolutions, videoStreams,
                videoOnlyStreams, ascendingOrder, preferVideoOnlyStreams)
    }

    /**
     * Get a sorted list containing a set of default resolution info
     * and additional resolution info if showHigherResolutions is true.
     *
     * @param resources the resources to get the resolutions from
     * @param defaultResolutionKey the settings key of the default resolution
     * @param additionalResolutionKey the settings key of the additional resolutions
     * @param showHigherResolutions if higher resolutions should be included in the sorted list
     * @return a sorted list containing the default and maybe additional resolutions
     */
    fun getSortedResolutionList(
            resources: Resources,
            defaultResolutionKey: Int,
            additionalResolutionKey: Int,
            showHigherResolutions: Boolean): List<String> {
        val resolutions: MutableList<String> = ArrayList(Arrays.asList(
                *resources.getStringArray(defaultResolutionKey)))
        if (!showHigherResolutions) {
            return resolutions
        }
        val additionalResolutions = Arrays.asList(
                *resources.getStringArray(additionalResolutionKey))
        // keep "best resolution" at the top
        resolutions.addAll(1, additionalResolutions)
        return resolutions
    }

    fun isHighResolutionSelected(selectedResolution: String,
                                 additionalResolutionKey: Int,
                                 resources: Resources): Boolean {
        return Arrays.asList(*resources.getStringArray(
                additionalResolutionKey))
                .contains(selectedResolution)
    }

    /**
     * Filter the list of audio streams and return a list with the preferred stream for
     * each audio track. Streams are sorted with the preferred language in the first position.
     *
     * @param context      the context to search for the track to give preference
     * @param audioStreams the list of audio streams
     * @return the sorted, filtered list
     */
    fun getFilteredAudioStreams(
            context: Context,
            audioStreams: List<AudioStream?>?): List<AudioStream?> {
        if (audioStreams == null) {
            return emptyList<AudioStream>()
        }
        val collectedStreams = HashMap<String, AudioStream?>()
        val cmp = getAudioFormatComparator(context)
        for (stream in audioStreams) {
            if (stream!!.deliveryMethod == DeliveryMethod.TORRENT
                    || (stream.deliveryMethod == DeliveryMethod.HLS
                            && stream.format == MediaFormat.OPUS)) {
                continue
            }
            val trackId = Objects.toString(stream.audioTrackId, "")
            val presentStream = collectedStreams[trackId]
            if (presentStream == null || cmp.compare(stream, presentStream) > 0) {
                collectedStreams[trackId] = stream
            }
        }

        // Filter unknown audio tracks if there are multiple tracks
        if (collectedStreams.size > 1) {
            collectedStreams.remove("")
        }

        // Sort collected streams by name
        return collectedStreams.values.stream().sorted(getAudioTrackNameComparator(context))
                .collect(Collectors.toList())
    }

    /**
     * Group the list of audioStreams by their track ID and sort the resulting list by track name.
     *
     * @param context      app context to get track names for sorting
     * @param audioStreams list of audio streams
     * @return list of audio streams lists representing individual tracks
     */
    fun getGroupedAudioStreams(
            context: Context,
            audioStreams: List<AudioStream?>?): List<List<AudioStream?>> {
        if (audioStreams == null) {
            return emptyList()
        }
        val collectedStreams = HashMap<String, MutableList<AudioStream?>>()
        for (stream in audioStreams) {
            val trackId = Objects.toString(stream!!.audioTrackId, "")
            if (collectedStreams.containsKey(trackId)) {
                collectedStreams[trackId]!!.add(stream)
            } else {
                val list: MutableList<AudioStream?> = ArrayList()
                list.add(stream)
                collectedStreams[trackId] = list
            }
        }

        // Filter unknown audio tracks if there are multiple tracks
        if (collectedStreams.size > 1) {
            collectedStreams.remove("")
        }

        // Sort tracks alphabetically, sort track streams by quality
        val nameCmp = getAudioTrackNameComparator(context)
        val formatCmp = getAudioFormatComparator(context)
        return collectedStreams.values.stream()
                .sorted { o1: List<AudioStream?>, o2: List<AudioStream?> -> nameCmp.compare(o1[0], o2[0]) }
                .map { streams: List<AudioStream?> -> streams.stream().sorted(formatCmp).collect(Collectors.toList()) }
                .collect(Collectors.toList())
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    /**
     * Get a filtered stream list, by using Java 8 Stream's API and the given predicate.
     *
     * @param streamList          the stream list to filter
     * @param streamListPredicate the predicate which will be used to filter streams
     * @param <S>                 the item type's class that extends [Stream]
     * @return a new stream list filtered using the given predicate
    </S> */
    private fun <S : Stream?> getFilteredStreamList(
            streamList: List<S>?,
            streamListPredicate: Predicate<S>): List<S> {
        return if (streamList == null) {
            emptyList()
        } else streamList.stream()
                .filter(streamListPredicate)
                .collect(Collectors.toList())
    }

    private fun computeDefaultResolution(context: Context, key: Int,
                                         value: Int): String? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Load the preferred resolution otherwise the best available
        var resolution = if (preferences != null) preferences.getString(context.getString(key), context.getString(value)) else context.getString(R.string.best_resolution_key)
        val maxResolution = getResolutionLimit(context)
        if (maxResolution != null
                && (resolution == context.getString(R.string.best_resolution_key) || compareVideoStreamResolution(maxResolution, resolution!!) < 1)) {
            resolution = maxResolution
        }
        return resolution
    }

    /**
     * Return the index of the default stream in the list, that will be sorted in the process, based
     * on the parameters defaultResolution and defaultFormat.
     *
     * @param defaultResolution the default resolution to look for
     * @param bestResolutionKey key of the best resolution
     * @param defaultFormat     the default format to look for
     * @param videoStreams      a mutable list of the video streams to check (it will be sorted in
     * place)
     * @return index of the default resolution&format in the sorted videoStreams
     */
    fun getDefaultResolutionIndex(defaultResolution: String,
                                  bestResolutionKey: String,
                                  defaultFormat: MediaFormat?,
                                  videoStreams: List<VideoStream?>?): Int {
        if (videoStreams == null || videoStreams.isEmpty()) {
            return -1
        }
        sortStreamList(videoStreams, false)
        if (defaultResolution == bestResolutionKey) {
            return 0
        }
        val defaultStreamIndex = getVideoStreamIndex(defaultResolution, defaultFormat, videoStreams)

        // this is actually an error,
        // but maybe there is really no stream fitting to the default value.
        return if (defaultStreamIndex == -1) {
            0
        } else defaultStreamIndex
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
     * streams and normal video streams are available
     * @return the sorted list
     */
    fun getSortedStreamVideosList(
            defaultFormat: MediaFormat?,
            showHigherResolutions: Boolean,
            videoStreams: List<VideoStream?>?,
            videoOnlyStreams: List<VideoStream?>?,
            ascendingOrder: Boolean,
            preferVideoOnlyStreams: Boolean
    ): List<VideoStream?> {
        // Determine order of streams
        // The last added list is preferred
        val videoStreamsOrdered = if (preferVideoOnlyStreams) Arrays.asList(videoStreams, videoOnlyStreams) else Arrays.asList(videoOnlyStreams, videoStreams)
        val allInitialStreams = videoStreamsOrdered.stream() // Ignore lists that are null
                .filter { obj: List<VideoStream?>? -> Objects.nonNull(obj) }
                .flatMap { obj: List<VideoStream?>? -> obj!!.stream() } // Filter out higher resolutions (or not if high resolutions should always be shown)
                .filter { stream: VideoStream? ->
                    (showHigherResolutions
                            || !HIGH_RESOLUTION_LIST.contains(stream!!.getResolution() // Replace any frame rate with nothing
                            .replace("p\\d+$".toRegex(), "p")))
                }
                .collect(Collectors.toList())
        val hashMap = HashMap<String, VideoStream?>()
        // Add all to the hashmap
        for (videoStream in allInitialStreams) {
            hashMap[videoStream!!.getResolution()] = videoStream
        }

        // Override the values when the key == resolution, with the defaultFormat
        for (videoStream in allInitialStreams) {
            if (videoStream!!.format == defaultFormat) {
                hashMap[videoStream.getResolution()] = videoStream
            }
        }

        // Return the sorted list
        return sortStreamList(ArrayList(hashMap.values), ascendingOrder)
    }

    /**
     * Sort the streams list depending on the parameter ascendingOrder;
     *
     *
     * It works like that:<br></br>
     * - Take a string resolution, remove the letters, replace "0p60" (for 60fps videos) with "1"
     * and sort by the greatest:<br></br>
     * <blockquote><pre>
     * 720p     ->  720
     * 720p60   ->  721
     * 360p     ->  360
     * 1080p    ->  1080
     * 1080p60  ->  1081
     * <br></br>
     * ascendingOrder  ? 360 < 720 < 721 < 1080 < 1081
     * !ascendingOrder ? 1081 < 1080 < 721 < 720 < 360</pre></blockquote>
     *
     * @param videoStreams   list that the sorting will be applied
     * @param ascendingOrder true -> smallest to greatest | false -> greatest to smallest
     * @return The sorted list (same reference as parameter videoStreams)
     */
    private fun sortStreamList(videoStreams: List<VideoStream?>,
                               ascendingOrder: Boolean): List<VideoStream?> {
        // Compares the quality of two video streams.
        val comparator = Comparator.nullsLast(Comparator
                .comparing({ obj: VideoStream? -> obj!!.getResolution() }) { obj: String?, r1: String? -> compareVideoStreamResolution(r1!!) }
                .thenComparingInt { s: VideoStream? -> VIDEO_FORMAT_QUALITY_RANKING.indexOf(s!!.format) })
        Collections.sort(videoStreams, if (ascendingOrder) comparator else comparator.reversed())
        return videoStreams
    }

    /**
     * Get the audio-stream from the list with the highest rank, depending on the comparator.
     * Format will be ignored if it yields no results.
     *
     * @param audioStreams List of audio streams
     * @param comparator   The comparator used for determining the max/best/highest ranked value
     * @return Index of audio stream that produces the highest ranked result or -1 if not found
     */
    fun getAudioIndexByHighestRank(audioStreams: List<AudioStream?>?,
                                   comparator: Comparator<AudioStream?>?): Int {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return -1
        }
        val highestRankedAudioStream = audioStreams.stream()
                .max(comparator).orElse(null)
        return audioStreams.indexOf(highestRankedAudioStream)
    }

    /**
     * Locates a possible match for the given resolution and format in the provided list.
     *
     *
     * In this order:
     *
     *
     *  1. Find a format and resolution match
     *  1. Find a format and resolution match and ignore the refresh
     *  1. Find a resolution match
     *  1. Find a resolution match and ignore the refresh
     *  1. Find a resolution just below the requested resolution and ignore the refresh
     *  1. Give up
     *
     *
     * @param targetResolution the resolution to look for
     * @param targetFormat     the format to look for
     * @param videoStreams     the available video streams
     * @return the index of the preferred video stream
     */
    fun getVideoStreamIndex(targetResolution: String,
                            targetFormat: MediaFormat?,
                            videoStreams: List<VideoStream?>): Int {
        var fullMatchIndex = -1
        var fullMatchNoRefreshIndex = -1
        var resMatchOnlyIndex = -1
        var resMatchOnlyNoRefreshIndex = -1
        var lowerResMatchNoRefreshIndex = -1
        val targetResolutionNoRefresh = targetResolution.replace("p\\d+$".toRegex(), "p")
        for (idx in videoStreams.indices) {
            val format = if (targetFormat == null) null else videoStreams[idx]!!.format
            val resolution = videoStreams[idx]!!.getResolution()
            val resolutionNoRefresh = resolution.replace("p\\d+$".toRegex(), "p")
            if (format == targetFormat && resolution == targetResolution) {
                fullMatchIndex = idx
            }
            if (format == targetFormat && resolutionNoRefresh == targetResolutionNoRefresh) {
                fullMatchNoRefreshIndex = idx
            }
            if (resMatchOnlyIndex == -1 && resolution == targetResolution) {
                resMatchOnlyIndex = idx
            }
            if (resMatchOnlyNoRefreshIndex == -1 && resolutionNoRefresh == targetResolutionNoRefresh) {
                resMatchOnlyNoRefreshIndex = idx
            }
            if (lowerResMatchNoRefreshIndex == -1 && compareVideoStreamResolution(
                            resolutionNoRefresh, targetResolutionNoRefresh) < 0) {
                lowerResMatchNoRefreshIndex = idx
            }
        }
        if (fullMatchIndex != -1) {
            return fullMatchIndex
        }
        if (fullMatchNoRefreshIndex != -1) {
            return fullMatchNoRefreshIndex
        }
        if (resMatchOnlyIndex != -1) {
            return resMatchOnlyIndex
        }
        return if (resMatchOnlyNoRefreshIndex != -1) {
            resMatchOnlyNoRefreshIndex
        } else lowerResMatchNoRefreshIndex
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
    private fun getDefaultResolutionWithDefaultFormat(context: Context,
                                                      defaultResolution: String?,
                                                      videoStreams: List<VideoStream?>): Int {
        val defaultFormat = getDefaultFormat(context,
                R.string.default_video_format_key, R.string.default_video_format_value)
        return getDefaultResolutionIndex(defaultResolution!!,
                context.getString(R.string.best_resolution_key), defaultFormat, videoStreams)
    }

    private fun getDefaultFormat(context: Context,
                                 @StringRes defaultFormatKey: Int,
                                 @StringRes defaultFormatValueKey: Int): MediaFormat? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultFormat = context.getString(defaultFormatValueKey)
        val defaultFormatString = preferences.getString(
                context.getString(defaultFormatKey),
                defaultFormat
        )
        return getMediaFormatFromKey(context, defaultFormatString!!)
    }

    private fun getMediaFormatFromKey(context: Context,
                                      formatKey: String): MediaFormat? {
        var format: MediaFormat? = null
        if (formatKey == context.getString(R.string.video_webm_key)) {
            format = MediaFormat.WEBM
        } else if (formatKey == context.getString(R.string.video_mp4_key)) {
            format = MediaFormat.MPEG_4
        } else if (formatKey == context.getString(R.string.video_3gp_key)) {
            format = MediaFormat.v3GPP
        } else if (formatKey == context.getString(R.string.audio_webm_key)) {
            format = MediaFormat.WEBMA
        } else if (formatKey == context.getString(R.string.audio_m4a_key)) {
            format = MediaFormat.M4A
        }
        return format
    }

    private fun compareVideoStreamResolution(r1: String,
                                             r2: String): Int {
        return try {
            val res1 = r1.replace("0p\\d+$".toRegex(), "1")
                    .replace("[^\\d.]".toRegex(), "").toInt()
            val res2 = r2.replace("0p\\d+$".toRegex(), "1")
                    .replace("[^\\d.]".toRegex(), "").toInt()
            res1 - res2
        } catch (e: NumberFormatException) {
            // Consider the first one greater because we don't know if the two streams are
            // different or not (a NumberFormatException was thrown so we don't know the resolution
            // of one stream or of all streams)
            1
        }
    }

    fun isLimitingDataUsage(context: Context): Boolean {
        return getResolutionLimit(context) != null
    }

    /**
     * The maximum resolution allowed.
     *
     * @param context App context
     * @return maximum resolution allowed or null if there is no maximum
     */
    private fun getResolutionLimit(context: Context): String? {
        var resolutionLimit: String? = null
        if (isMeteredNetwork(context)) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val defValue = context.getString(R.string.limit_data_usage_none_key)
            val value = preferences.getString(
                    context.getString(R.string.limit_mobile_data_usage_key), defValue)
            resolutionLimit = if (defValue == value) null else value
        }
        return resolutionLimit
    }

    /**
     * The current network is metered (like mobile data)?
     *
     * @param context App context
     * @return `true` if connected to a metered network
     */
    fun isMeteredNetwork(context: Context): Boolean {
        val manager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        return if (manager == null || manager.activeNetworkInfo == null) {
            false
        } else manager.isActiveNetworkMetered
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their format and bitrate.
     *
     *
     * The preferred stream will be ordered last.
     *
     * @param context app context
     * @return Comparator
     */
    private fun getAudioFormatComparator(
            context: Context): Comparator<AudioStream?> {
        val defaultFormat = getDefaultFormat(context,
                R.string.default_audio_format_key, R.string.default_audio_format_value)
        return getAudioFormatComparator(defaultFormat, isLimitingDataUsage(context))
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their format and bitrate.
     *
     *
     * The preferred stream will be ordered last.
     *
     * @param defaultFormat  the default format to look for
     * @param limitDataUsage choose low bitrate audio stream
     * @return Comparator
     */
    @JvmStatic
    fun getAudioFormatComparator(
            defaultFormat: MediaFormat?, limitDataUsage: Boolean): Comparator<AudioStream?> {
        val formatRanking = if (limitDataUsage) AUDIO_FORMAT_EFFICIENCY_RANKING else AUDIO_FORMAT_QUALITY_RANKING
        var bitrateComparator = Comparator.comparingInt { obj: AudioStream? -> obj!!.averageBitrate }
        if (limitDataUsage) {
            bitrateComparator = bitrateComparator.reversed()
        }
        return Comparator.comparing({ obj: AudioStream? -> obj!!.format }) { o1: MediaFormat?, o2: MediaFormat? ->
            if (defaultFormat != null) {
                return@comparing java.lang.Boolean.compare(o1 == defaultFormat, o2 == defaultFormat)
            }
            0
        }.thenComparing(bitrateComparator).thenComparingInt { stream: AudioStream? -> formatRanking.indexOf(stream!!.format) }
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their tracks.
     *
     *
     * Tracks will be compared this order:
     *
     *  1. If `preferOriginalAudio`: use original audio
     *  1. Language matches `preferredLanguage`
     *  1.
     * Track type ranks highest in this order:
     * *Original* > *Dubbed* > *Descriptive*
     *
     * If `preferDescriptiveAudio`:
     * *Descriptive* > *Dubbed* > *Original*
     *
     *  1. Language is English
     *
     *
     *
     * The preferred track will be ordered last.
     *
     * @param context App context
     * @return Comparator
     */
    private fun getAudioTrackComparator(
            context: Context): Comparator<AudioStream?> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val preferredLanguage = Localization.getPreferredLocale(context)
        val preferOriginalAudio = preferences.getBoolean(context.getString(R.string.prefer_original_audio_key),
                false)
        val preferDescriptiveAudio = preferences.getBoolean(context.getString(R.string.prefer_descriptive_audio_key),
                false)
        return getAudioTrackComparator(preferredLanguage, preferOriginalAudio,
                preferDescriptiveAudio)
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their tracks.
     *
     *
     * Tracks will be compared this order:
     *
     *  1. If `preferOriginalAudio`: use original audio
     *  1. Language matches `preferredLanguage`
     *  1.
     * Track type ranks highest in this order:
     * *Original* > *Dubbed* > *Descriptive*
     *
     * If `preferDescriptiveAudio`:
     * *Descriptive* > *Dubbed* > *Original*
     *
     *  1. Language is English
     *
     *
     *
     * The preferred track will be ordered last.
     *
     * @param preferredLanguage      Preferred audio stream language
     * @param preferOriginalAudio    Get the original audio track regardless of its language
     * @param preferDescriptiveAudio Prefer the descriptive audio track if available
     * @return Comparator
     */
    @JvmStatic
    fun getAudioTrackComparator(
            preferredLanguage: Locale?,
            preferOriginalAudio: Boolean,
            preferDescriptiveAudio: Boolean): Comparator<AudioStream?> {
        val langCode = preferredLanguage!!.getISO3Language()
        val trackTypeRanking = if (preferDescriptiveAudio) AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE else AUDIO_TRACK_TYPE_RANKING
        return Comparator.comparing({ obj: AudioStream? -> obj!!.audioTrackType }) { o1: AudioTrackType?, o2: AudioTrackType? ->
            if (preferOriginalAudio) {
                return@comparing java.lang.Boolean.compare(
                        o1 == AudioTrackType.ORIGINAL, o2 == AudioTrackType.ORIGINAL)
            }
            0
        }.thenComparing({ obj: AudioStream? -> obj!!.audioLocale },
                Comparator.nullsFirst(Comparator.comparing { locale: Locale? -> locale!!.getISO3Language() == langCode }))
                .thenComparing({ obj: AudioStream? -> obj!!.audioTrackType },
                        Comparator.nullsFirst(Comparator.comparingInt { o: AudioTrackType? -> trackTypeRanking.indexOf(o) }))
                .thenComparing({ obj: AudioStream? -> obj!!.audioLocale },
                        Comparator.nullsFirst(Comparator.comparing { locale: Locale? ->
                            locale!!.getISO3Language() ==
                                    Locale.ENGLISH.getISO3Language()
                        }))
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their languages and track types
     * for alphabetical sorting.
     *
     * @param context app context for localization
     * @return Comparator
     */
    private fun getAudioTrackNameComparator(
            context: Context): Comparator<AudioStream?> {
        val appLoc = Localization.getAppLocale(context)
        return Comparator.comparing({ obj: AudioStream? -> obj!!.audioLocale }, Comparator.nullsLast(
                Comparator.comparing { locale: Locale? -> locale!!.getDisplayName(appLoc) }))
                .thenComparing({ obj: AudioStream? -> obj!!.audioTrackType }, Comparator.nullsLast(
                        Comparator.naturalOrder()))
    }
}
