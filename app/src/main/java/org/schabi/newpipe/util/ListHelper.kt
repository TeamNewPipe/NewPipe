package org.schabi.newpipe.util

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.util.Collections
import java.util.Locale
import java.util.Objects
import java.util.function.Predicate
import java.util.stream.Collectors
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

object ListHelper {
    // Video format in order of quality. 0=lowest quality, n=highest quality
    private val VIDEO_FORMAT_QUALITY_RANKING =
        listOf(MediaFormat.v3GPP, MediaFormat.WEBM, MediaFormat.MPEG_4)

    // Audio format in order of quality. 0=lowest quality, n=highest quality
    private val AUDIO_FORMAT_QUALITY_RANKING =
        listOf(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A)

    // Audio format in order of efficiency. 0=least efficient, n=most efficient
    private val AUDIO_FORMAT_EFFICIENCY_RANKING =
        listOf(MediaFormat.MP3, MediaFormat.M4A, MediaFormat.WEBMA)

    // Use a Set for better performance
    private val HIGH_RESOLUTION_LIST = setOf(1440, 2160)

    // Audio track types in order of priority. 0=lowest, n=highest
    private val AUDIO_TRACK_TYPE_RANKING =
        listOf(
            AudioTrackType.DESCRIPTIVE,
            AudioTrackType.SECONDARY,
            AudioTrackType.DUBBED,
            AudioTrackType.ORIGINAL
        )

    // Audio track types in order of priority when descriptive audio is preferred.
    private val AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE =
        listOf(
            AudioTrackType.SECONDARY,
            AudioTrackType.DUBBED,
            AudioTrackType.ORIGINAL,
            AudioTrackType.DESCRIPTIVE
        )

    /**
     * List of supported YouTube Itag ids.
     * The original order is kept.
     * @see [org.schabi.newpipe.extractor.services.youtube.ItagItem.ITAG_LIST]
     */
    private val SUPPORTED_ITAG_IDS =
        listOf(
            17, 36, // video v3GPP
            18, 34, 35, 59, 78, 22, 37, 38, // video MPEG4
            43, 44, 45, 46, // video webm
            171, 172, 139, 140, 141, 249, 250, 251, // audio
            160, 133, 134, 135, 212, 136, 298, 137, 299, 266, // video only
            278, 242, 243, 244, 245, 246, 247, 248, 271, 272, 302, 303, 308, 313, 315
        )

    /**
     * @param context      Android app context
     * @param videoStreams list of the video streams to check
     * @return index of the video stream with the default index
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    @JvmStatic
    fun getDefaultResolutionIndex(
        context: Context,
        videoStreams: MutableList<VideoStream>
    ): Int {
        val defaultResolution = computeDefaultResolution(
            context,
            R.string.default_resolution_key,
            R.string.default_resolution_value
        )
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @param context           Android app context
     * @param videoStreams      list of the video streams to check
     * @param defaultResolution the default resolution to look for
     * @return index of the video stream with the default index
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    @JvmStatic
    fun getResolutionIndex(
        context: Context,
        videoStreams: MutableList<VideoStream>,
        defaultResolution: String
    ): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @param context      Android app context
     * @param videoStreams list of the video streams to check
     * @return index of the video stream with the default index
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    @JvmStatic
    fun getPopupDefaultResolutionIndex(
        context: Context,
        videoStreams: MutableList<VideoStream>
    ): Int {
        val defaultResolution = computeDefaultResolution(
            context,
            R.string.default_popup_resolution_key,
            R.string.default_popup_resolution_value
        )
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    /**
     * @param context           Android app context
     * @param videoStreams      list of the video streams to check
     * @param defaultResolution the default resolution to look for
     * @return index of the video stream with the default index
     * @see #getDefaultResolutionIndex(String, String, MediaFormat, List)
     */
    @JvmStatic
    fun getPopupResolutionIndex(
        context: Context,
        videoStreams: MutableList<VideoStream>,
        defaultResolution: String
    ): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    @JvmStatic
    fun getDefaultAudioFormat(
        context: Context,
        audioStreams: List<AudioStream>
    ): Int {
        return getAudioIndexByHighestRank(
            audioStreams,
            getAudioTrackComparator(context).thenComparing(getAudioFormatComparator(context))
        )
    }

    @JvmStatic
    fun getDefaultAudioTrackGroup(
        context: Context,
        groupedAudioStreams: List<List<AudioStream>>?
    ): Int {
        if (groupedAudioStreams.isNullOrEmpty()) {
            return -1
        }
        val cmp = getAudioTrackComparator(context)
        val highestRanked =
            groupedAudioStreams.maxWithOrNull(Comparator { o1, o2 -> cmp.compare(o1[0], o2[0]) })
        return groupedAudioStreams.indexOf(highestRanked)
    }

    @JvmStatic
    fun getAudioFormatIndex(
        context: Context,
        audioStreams: List<AudioStream>,
        trackId: String?
    ): Int {
        if (trackId != null) {
            for (i in audioStreams.indices) {
                val s = audioStreams[i]
                if (s.audioTrackId != null &&
                    s.audioTrackId == trackId
                ) {
                    return i
                }
            }
        }
        return getDefaultAudioFormat(context, audioStreams)
    }

    /**
     * Return a [Stream] list which uses the given delivery method from a [Stream] list.
     *
     * @param streamList     the original [Stream] list
     * @param deliveryMethod the [DeliveryMethod] delivery method
     * @param <S>            the item type's class that extends [Stream]
     * @return a [Stream] list which uses the given delivery method
     */
    @JvmStatic
    fun <S : Stream> getStreamsOfSpecifiedDelivery(
        streamList: List<S>?,
        deliveryMethod: DeliveryMethod
    ): List<S> {
        return getFilteredStreamList(
            streamList
        )
        { stream -> stream.deliveryMethod == deliveryMethod }
    }

    /**
     * Return a [Stream] list which only contains URL streams and non-torrent streams.
     *
     * @param streamList the original stream list
     * @param <S>        the item type's class that extends [Stream]
     * @return a stream list which only contains URL streams and non-torrent streams
     */
    @JvmStatic
    fun <S : Stream> getUrlAndNonTorrentStreams(
        streamList: List<S>?
    ): List<S> {
        return getFilteredStreamList(
            streamList
        )
        { stream -> stream.isUrl && stream.deliveryMethod != DeliveryMethod.TORRENT }
    }

    /**
     * Return a [Stream] list which only contains streams which can be played by the player.
     *
     * Some formats are not supported, see [ListHelper.SUPPORTED_ITAG_IDS] for more details.
     * Torrent streams are also removed, because they cannot be retrieved, like OPUS streams using
     * HLS as their delivery method, since they are not supported by ExoPlayer.
     *
     * @param <S>        the item type's class that extends [Stream]
     * @param streamList the original stream list
     * @param serviceId  the service ID from which the streams' list comes from
     * @return a stream list which only contains streams that can be played the player
     */
    @JvmStatic
    fun <S : Stream> getPlayableStreams(
        streamList: List<S>?,
        serviceId: Int
    ): List<S> {
        val youtubeServiceId = ServiceList.YouTube.serviceId
        return getFilteredStreamList(
            streamList
        )
        { stream ->
            stream.deliveryMethod != DeliveryMethod.TORRENT &&
                (
                    stream.deliveryMethod != DeliveryMethod.HLS ||
                        stream.format != MediaFormat.OPUS
                    ) &&
                (
                    serviceId != youtubeServiceId ||
                        stream.itagItem == null ||
                        SUPPORTED_ITAG_IDS.contains(stream.itagItem!!.id)
                    )
        }
    }

    /**
     * Join the two lists of video streams (video_only and normal videos),
     * and sort them according to the default format chosen by the user.
     *
     * @param context                the context to search for the format to give preference
     * @param videoStreams           the normal videos list
     * @param videoOnlyStreams       the video-only stream list
     * @param ascendingOrder         true -> smallest to greatest | false -> greatest to smallest
     * @param preferVideoOnlyStreams if video-only streams should be preferred when both video-only
     *                               streams and normal video streams are available
     * @return the sorted list
     */
    @JvmStatic
    fun getSortedStreamVideosList(
        context: Context,
        videoStreams: List<VideoStream>?,
        videoOnlyStreams: List<VideoStream>?,
        ascendingOrder: Boolean,
        preferVideoOnlyStreams: Boolean
    ): MutableList<VideoStream> {
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(context)
        val showHigherResolutions = preferences.getBoolean(
            context.getString(R.string.show_higher_resolutions_key),
            false
        )
        val defaultFormat = getDefaultFormat(
            context,
            R.string.default_video_format_key,
            R.string.default_video_format_value
        )
        return getSortedStreamVideosList(
            defaultFormat,
            showHigherResolutions,
            videoStreams,
            videoOnlyStreams,
            ascendingOrder,
            preferVideoOnlyStreams
        )
    }

    /**
     * Get a sorted list containing a set of default resolution info
     * and additional resolution info if showHigherResolutions is true.
     *
     * @param resources               the resources to get the resolutions from
     * @param defaultResolutionKey    the settings key of the default resolution
     * @param additionalResolutionKey the settings key of the additional resolutions
     * @param showHigherResolutions   if higher resolutions should be included in the sorted list
     * @return a sorted list containing the default and maybe additional resolutions
     */
    @JvmStatic
    fun getSortedResolutionList(
        resources: Resources,
        defaultResolutionKey: Int,
        additionalResolutionKey: Int,
        showHigherResolutions: Boolean
    ): List<String> {
        val resolutions =
            resources.getStringArray(defaultResolutionKey).toMutableList()
        if (!showHigherResolutions) {
            return resolutions
        }
        val additionalResolutions =
            resources.getStringArray(additionalResolutionKey).toList()
        // keep "best resolution" at the top
        resolutions.addAll(1, additionalResolutions)
        return resolutions
    }

    @JvmStatic
    fun isHighResolutionSelected(
        selectedResolution: String,
        additionalResolutionKey: Int,
        resources: Resources
    ): Boolean {
        return resources.getStringArray(
            additionalResolutionKey
        )
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
    @JvmStatic
    fun getFilteredAudioStreams(
        context: Context,
        audioStreams: List<AudioStream>?
    ): List<AudioStream> {
        if (audioStreams == null) {
            return Collections.emptyList()
        }
        val collectedStreams: HashMap<String, AudioStream> = HashMap()
        val cmp = getAudioFormatComparator(context)
        for (stream in audioStreams) {
            if (stream.deliveryMethod == DeliveryMethod.TORRENT ||
                (
                    stream.deliveryMethod == DeliveryMethod.HLS &&
                        stream.format == MediaFormat.OPUS
                    )
            ) {
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
        return collectedStreams.values.sortedWith(getAudioTrackNameComparator())
    }

    /**
     * Group the list of audioStreams by their track ID and sort the resulting list by track name.
     *
     * @param context      app context to get track names for sorting
     * @param audioStreams list of audio streams
     * @return list of audio streams lists representing individual tracks
     */
    @JvmStatic
    fun getGroupedAudioStreams(
        context: Context,
        audioStreams: List<AudioStream>?
    ): List<List<AudioStream>> {
        if (audioStreams == null) {
            return Collections.emptyList()
        }
        val collectedStreams: HashMap<String, MutableList<AudioStream>> = HashMap()
        for (stream in audioStreams) {
            val trackId = Objects.toString(stream.audioTrackId, "")
            if (collectedStreams.containsKey(trackId)) {
                collectedStreams[trackId]!!.add(stream)
            } else {
                val list: MutableList<AudioStream> = ArrayList()
                list.add(stream)
                collectedStreams[trackId] = list
            }
        }
        // Filter unknown audio tracks if there are multiple tracks
        if (collectedStreams.size > 1) {
            collectedStreams.remove("")
        }
        // Sort tracks alphabetically, sort track streams by quality
        val nameCmp = getAudioTrackNameComparator()
        val formatCmp = getAudioFormatComparator(context)
        return collectedStreams.values
            .sortedWith(Comparator { o1, o2 -> nameCmp.compare(o1[0], o2[0]) })
            .map { streams -> streams.sortedWith(formatCmp) }
    }

    // ////////////////////////////////////////////////////////////////////////
    // Utils
    // ////////////////////////////////////////////////////////////////////////

    /**
     * Get a filtered stream list, by using Java 8 Stream's API and the given predicate.
     *
     * @param streamList          the stream list to filter
     * @param streamListPredicate the predicate which will be used to filter streams
     * @param <S>                 the item type's class that extends [Stream]
     * @return a new stream list filtered using the given predicate
     */
    private fun <S : Stream> getFilteredStreamList(
        streamList: List<S>?,
        streamListPredicate: Predicate<S>
    ): List<S> {
        if (streamList == null) {
            return Collections.emptyList()
        }
        return streamList.stream()
            .filter(streamListPredicate)
            .collect(Collectors.toList())
    }

    private fun computeDefaultResolution(
        context: Context,
        key: Int,
        value: Int
    ): String {
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(context)
        val bestResolutionKey = context.getString(R.string.best_resolution_key)

        // Load the preferred resolution otherwise the best available
        var resolution = preferences?.getString(context.getString(key), context.getString(value))
            ?: bestResolutionKey

        val maxResolution = getResolutionLimit(context)
        if (maxResolution != null &&
            (
                resolution == bestResolutionKey ||
                    compareVideoStreamResolution(maxResolution, resolution) < 1
                )
        ) {
            resolution = maxResolution
        }
        return resolution
    }

    /**
     * Return the index of the default stream in the list, that will be sorted in the process, based
     * on the parameters defaultResolution and defaultFormat.
     *
     * The method performs the following steps:
     *
     * 1. Validate that streams exist
     * 2. Sort the streams by quality
     * 3. Handle the special case where the user requested the "best" resolution
     * 4. Use the matching algorithm to find the best candidate
     * 5. Fallback to index 0 if no match was found
     *
     * @param defaultResolution the default resolution to look for
     * @param bestResolutionKey key of the best resolution
     * @param defaultFormat     the default format to look for
     * @param videoStreams a mutable list of the video streams to check (it will be sorted in
     * place)
     * @return index of the default resolution&format in the sorted videoStreams
     */
    @JvmStatic
    fun getDefaultResolutionIndex(
        defaultResolution: String,
        bestResolutionKey: String,
        defaultFormat: MediaFormat?,
        videoStreams: MutableList<VideoStream>?
    ): Int {
        if (videoStreams.isNullOrEmpty()) return -1

        val streamsWithParsedQuality = videoStreams.wrapWithQuality().toMutableList()

        // Ensure streams are sorted by quality before selecting one.
        sortStreamList(streamsWithParsedQuality, false)
        videoStreams.clear()
        videoStreams.addAll(streamsWithParsedQuality.map { it.stream })

        // If the user explicitly requested the "best" resolution,
        // simply return the first stream since the list is already sorted.
        if (defaultResolution == bestResolutionKey) return 0

        // Find the index of the best matching stream
        val defaultStreamIndex = internalGetVideoStreamIndex(
            defaultResolution,
            defaultFormat,
            streamsWithParsedQuality
        )

        // If no suitable match was found, fall back to the first stream
        // (which is the best available due to sorting).
        return if (defaultStreamIndex == -1) 0 else defaultStreamIndex
    }

    /**
     * Join the two lists of video streams (video_only and normal videos),
     * and sort them according to the default format chosen by the user.
     *
     * @param defaultFormat          format to give preference
     * @param showHigherResolutions  show >1080p resolutions
     * @param videoStreams           normal videos list
     * @param videoOnlyStreams       video only stream list
     * @param ascendingOrder         true -> smallest to greatest | false -> greatest to smallest
     * @param preferVideoOnlyStreams if video-only streams should be preferred when both video-only
     *                               streams and normal video streams are available
     * @return the sorted list
     */
    @JvmStatic
    fun getSortedStreamVideosList(
        defaultFormat: MediaFormat?,
        showHigherResolutions: Boolean,
        videoStreams: List<VideoStream>?,
        videoOnlyStreams: List<VideoStream>?,
        ascendingOrder: Boolean,
        preferVideoOnlyStreams: Boolean
    ): MutableList<VideoStream> {
        // Determine order of streams
        // The last added list is preferred
        val videoStreamListsInPreferredOrder = if (preferVideoOnlyStreams) {
            mutableListOf(videoStreams, videoOnlyStreams)
        } else {
            mutableListOf(videoOnlyStreams, videoStreams)
        }

        val allInitialStreams = videoStreamListsInPreferredOrder
            // Ignore lists that are null
            .filterNotNull()
            .flatten()
            .wrapWithQuality()
            // Filter out higher resolutions (or not if high resolutions should always be shown)
            .filter { stream ->
                showHigherResolutions || !HIGH_RESOLUTION_LIST.contains(
                    stream.quality.resolution
                )
            }
            .toMutableList()

        val streamsWithDefaultFormatPreferred = mutableMapOf<String, VideoStreamWithQuality>()

        // add all streams based on key [ListHelper.qualityKeyOf] to [streamsWithDefaultFormatPreferred]
        allInitialStreams
            .forEach { streamsWithDefaultFormatPreferred[qualityKeyOf(it)] = it }

        // Ensure that streams with 'defaultFormat' are included in streamMap as they are
        // preferred. They might have been overridden if allInitialStreams has more than one stream
        // for the same resolution key but a none 'defaultFormat' stream was added later.
        // See 'qualityKeyOf'.
        defaultFormat?.let { defaultFormat ->
            allInitialStreams.filter { it.stream.format == defaultFormat }
                .forEach { streamsWithDefaultFormatPreferred[qualityKeyOf(it)] = it }
        }

        return sortStreamList(
            streamsWithDefaultFormatPreferred.values.toMutableList(),
            ascendingOrder
        )
            .map { it.stream }
            .toMutableList()
    }

    /**
     * Here we create a key based on resolution, frame rate and bitrate
     */
    private fun qualityKeyOf(item: VideoStreamWithQuality) = "${item.quality.resolution}p${item.quality.fps}@${item.quality.bitrate}"

    /**
     * Sort the streams list depending on the parameter ascendingOrder;
     *
     * It uses [ListHelper.getVideoStreamQualityComparator] and produces results like this:
     * ```
     * ascendingOrder  ? 360p < 720p < 720p60 < 1080p < 1080p@60
     * !ascendingOrder ? 1080p60 < 1080p < 720p60 < 720p < 360p
     * ```
     *
     * @param videoStreams   list that the sorting will be applied
     * @param ascendingOrder true -> smallest to greatest | false -> greatest to smallest
     * @return The sorted list (same reference as parameter videoStreams)
     */
    private fun sortStreamList(
        videoStreams: MutableList<VideoStreamWithQuality>,
        ascendingOrder: Boolean
    ): MutableList<VideoStreamWithQuality> {
        val comparator = getVideoStreamQualityComparator()
        videoStreams.sortWith(if (ascendingOrder) comparator else comparator.reversed())
        return videoStreams
    }

    private fun getVideoStreamQualityComparator() = compareBy<VideoStreamWithQuality>
        { it.quality.resolution }
        .thenBy { it.quality.fps }
        .thenBy { it.quality.formatRank }
        .thenBy { it.quality.bitrate }

    /**
     * Get the audio-stream from the list with the highest rank, depending on the comparator.
     * Format will be ignored if it yields no results.
     *
     * @param audioStreams List of audio streams
     * @param comparator   The comparator used for determining the max/best/highest ranked value
     * @return Index of audio stream that produces the highest ranked result or -1 if not found
     */
    @JvmStatic
    fun getAudioIndexByHighestRank(
        audioStreams: List<AudioStream>?,
        comparator: Comparator<AudioStream>
    ): Int {
        if (audioStreams.isNullOrEmpty()) {
            return -1
        }
        val highestRankedAudioStream = audioStreams.maxWithOrNull(comparator)
        return audioStreams.indexOf(highestRankedAudioStream)
    }

    /**
     * Locate the best matching video stream for a requested resolution and format in the provided list.
     *
     * The algorithm iterates over all available streams and assigns each one
     * a "priority class". Lower numbers represent a better match.
     *
     * Matching priority (best → worst):
     *
     * 1. Format + resolution + fps + exact bitrate
     * 2. Format + resolution + fps
     * 3. Format + resolution
     * 4. Resolution + fps + the closest bitrate
     * 5. Resolution + fps
     * 6. Resolution
     * 7. Next lower resolution
     * 8. Give up
     *
     * If multiple streams fall into the same priority class,
     * the stream with the closest bitrate to the requested one is preferred.
     *
     * The list of streams is expected to already be sorted by [ListHelper.sortStreamList]
     *
     * @param targetResolution the resolution to look for. E.g.: "720p", "720p60", "720p60@123k", or "720p@2m"
     * @param targetFormat     the format to look for
     * @param videoStreams     the available video streams
     * @return the index of the preferred video stream
     */
    @JvmStatic
    fun getVideoStreamIndex(
        targetResolution: String,
        targetFormat: MediaFormat?,
        videoStreams: List<VideoStream>
    ): Int {
        return internalGetVideoStreamIndex(
            targetResolution,
            targetFormat,
            videoStreams.wrapWithQuality()
        )
    }

    private fun internalGetVideoStreamIndex(
        targetResolution: String,
        targetFormat: MediaFormat?,
        videoStreams: List<VideoStreamWithQuality>
    ): Int {
        val target = parseQuality(targetResolution)

        /**
         * Internal helper representing a candidate stream.
         *
         * index       -> index in the original stream list
         * priority    -> matching class (lower = better)
         * bitrateDiff -> distance to the requested bitrate
         */
        data class Candidate(
            val index: Int,
            val priority: Int,
            val bitrateDiff: Long
        )

        val candidateComparator =
            compareBy<Candidate> { it.priority }
                .thenBy { it.bitrateDiff }
                // use lower bitrate to save bandwidth
                .thenBy { videoStreams[it.index].quality.bitrate }

        var best: Candidate? = null

        for ((index, item) in videoStreams.withIndex()) {
            val (stream, quality) = item

            // Check individual match criteria
            val isFormatMatch = targetFormat != null && stream.format == targetFormat
            val isResMatch = quality.resolution == target.resolution
            val isFpsMatch = quality.fps == target.fps

            // Compute bitrate difference only if both bitrates exist
            val bitrateDiff = if (target.bitrate > 0 && quality.bitrate > 0) {
                kotlin.math.abs(quality.bitrate - target.bitrate)
            } else {
                Long.MAX_VALUE
            }

            /**
             * Determine the matching priority of this stream.
             *
             * The "when" block classifies the stream into a priority group.
             * Lower numbers mean better matches.
             */
            val priority = when {
                // Perfect match
                isFormatMatch && isResMatch && isFpsMatch && bitrateDiff == 0L -> 1

                isFormatMatch && isResMatch && isFpsMatch -> 2

                isFormatMatch && isResMatch -> 3

                isResMatch && isFpsMatch -> 4

                isResMatch -> 5

                // Accept lower resolutions as fallback
                quality.resolution < target.resolution -> 6

                // If none of the matching conditions apply,
                // this stream is not considered a valid candidate.
                else -> continue
            }

            val candidate = Candidate(index, priority, bitrateDiff)

            if (best == null || candidateComparator.compare(candidate, best) < 0) {
                best = candidate
                if (best.priority == 1) {
                    // perfect match, stop searching
                    break
                }
            }
        }

        // Return the index of the best matching stream, or -1 if none was found
        return best?.index ?: -1
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
    private fun getDefaultResolutionWithDefaultFormat(
        context: Context,
        defaultResolution: String,
        videoStreams: MutableList<VideoStream>
    ): Int {
        val defaultFormat = getDefaultFormat(
            context,
            R.string.default_video_format_key,
            R.string.default_video_format_value
        )
        return getDefaultResolutionIndex(
            defaultResolution,
            context.getString(R.string.best_resolution_key),
            defaultFormat,
            videoStreams
        )
    }

    private fun getDefaultFormat(
        context: Context,
        @StringRes defaultFormatKey: Int,
        @StringRes defaultFormatValueKey: Int
    ): MediaFormat? {
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(context)
        val defaultFormat = context.getString(defaultFormatValueKey)
        val defaultFormatString = preferences.getString(
            context.getString(defaultFormatKey),
            defaultFormat
        )
        return getMediaFormatFromKey(context, defaultFormatString!!)
    }

    private fun getMediaFormatFromKey(
        context: Context,
        formatKey: String
    ): MediaFormat? {
        var format: MediaFormat? = null
        when (formatKey) {
            context.getString(R.string.video_webm_key) -> {
                format = MediaFormat.WEBM
            }

            context.getString(R.string.video_mp4_key) -> {
                format = MediaFormat.MPEG_4
            }

            context.getString(R.string.video_3gp_key) -> {
                format = MediaFormat.v3GPP
            }

            context.getString(R.string.audio_webm_key) -> {
                format = MediaFormat.WEBMA
            }

            context.getString(R.string.audio_m4a_key) -> {
                format = MediaFormat.M4A
            }
        }
        return format
    }

    private fun compareVideoStreamResolution(
        r1: String,
        r2: String
    ): Int {
        val r1Quality = parseQuality(r1)
        val r2Quality = parseQuality(r2)

        val comparator =
            compareBy<VideoQuality>
                { it.resolution }
                .thenBy { it.fps }
        return comparator.compare(r2Quality, r1Quality)
    }

    @JvmStatic
    fun isLimitingDataUsage(context: Context): Boolean {
        return getResolutionLimit(context) != null
    }

    /**
     * The maximum resolution allowed.
     *
     * @param context App context
     * @return maximum resolution allowed or null if there is no maximum
     */
    fun getResolutionLimit(context: Context): String? {
        var resolutionLimit: String? = null
        if (isMeteredNetwork(context)) {
            val preferences =
                PreferenceManager.getDefaultSharedPreferences(context)
            val defValue = context.getString(R.string.limit_data_usage_none_key)
            val value = preferences.getString(
                context.getString(R.string.limit_mobile_data_usage_key),
                defValue
            )
            resolutionLimit = if (defValue == value) null else value
        }
        return resolutionLimit
    }

    /**
     * The current network is metered (like mobile data)?
     *
     * @param context App context
     * @return {@code true} if connected to a metered network
     */
    @JvmStatic
    fun isMeteredNetwork(context: Context): Boolean {
        val manager =
            ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        if (manager == null || manager.activeNetworkInfo == null) {
            return false
        }
        return manager.isActiveNetworkMetered
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their format and bitrate.
     *
     * The preferred stream will be ordered last.
     *
     * @param context app context
     * @return Comparator
     */
    private fun getAudioFormatComparator(
        context: Context
    ): Comparator<AudioStream> {
        val defaultFormat = getDefaultFormat(
            context,
            R.string.default_audio_format_key,
            R.string.default_audio_format_value
        )
        return getAudioFormatComparator(defaultFormat, isLimitingDataUsage(context))
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their format and bitrate.
     *
     * The preferred stream will be ordered last.
     *
     * @param defaultFormat  the default format to look for
     * @param limitDataUsage choose low bitrate audio stream
     * @return Comparator
     */
    @JvmStatic
    fun getAudioFormatComparator(
        defaultFormat: MediaFormat?,
        limitDataUsage: Boolean
    ): Comparator<AudioStream> {
        val formatRanking = if (limitDataUsage) {
            AUDIO_FORMAT_EFFICIENCY_RANKING
        } else {
            AUDIO_FORMAT_QUALITY_RANKING
        }
        var bitrateComparator =
            Comparator.comparingInt { stream: AudioStream -> stream.averageBitrate }
        if (limitDataUsage) {
            bitrateComparator = bitrateComparator.reversed()
        }
        return Comparator.comparing<AudioStream, MediaFormat?>(
            { it.format },
            Comparator { o1: MediaFormat?, o2: MediaFormat? ->
                if (defaultFormat != null) {
                    java.lang.Boolean.compare(o1 == defaultFormat, o2 == defaultFormat)
                } else {
                    0
                }
            }
        ).thenComparing(bitrateComparator).thenComparingInt { stream ->
            formatRanking.indexOf(
                stream.format
            )
        }
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their tracks.
     *
     * Tracks will be compared this order:
     * 1. If [preferOriginalAudio]: use original audio
     * 2. Language matches [preferredLanguage]
     * 3. Track type ranks highest in this order:
     *    *Original* > *Dubbed* > *Descriptive*
     *    If [preferDescriptiveAudio]:
     *    *Descriptive* > *Dubbed* > *Original*
     * 4. Language is English
     *
     * The preferred track will be ordered last.
     *
     * @param context App context
     * @return Comparator
     */
    private fun getAudioTrackComparator(
        context: Context
    ): Comparator<AudioStream> {
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(context)
        val preferredLanguage = Localization.getPreferredLocale(context)
        val preferOriginalAudio =
            preferences.getBoolean(
                context.getString(R.string.prefer_original_audio_key),
                true
            )
        val preferDescriptiveAudio =
            preferences.getBoolean(
                context.getString(R.string.prefer_descriptive_audio_key),
                false
            )
        return getAudioTrackComparator(
            preferredLanguage,
            preferOriginalAudio,
            preferDescriptiveAudio
        )
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their tracks.
     *
     * Tracks will be compared this order:
     * 1. If [preferOriginalAudio]: use original audio
     * 2. Language matches [preferredLanguage]
     * 3. Track type ranks highest in this order:
     *    *Original* > *Dubbed* > *Descriptive*
     *    If [preferDescriptiveAudio]:
     *    *Descriptive* > *Dubbed* > *Original*
     * 4. Language is English
     *
     * The preferred track will be ordered last.
     *
     * @param preferredLanguage Preferred audio stream language
     * @param preferOriginalAudio Get the original audio track regardless of its language
     * @param preferDescriptiveAudio Prefer the descriptive audio track if available
     * @return Comparator
     */
    @JvmStatic
    fun getAudioTrackComparator(
        preferredLanguage: Locale,
        preferOriginalAudio: Boolean,
        preferDescriptiveAudio: Boolean
    ): Comparator<AudioStream> {
        val langCode = preferredLanguage.isO3Language
        val trackTypeRanking = if (preferDescriptiveAudio) {
            AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE
        } else {
            AUDIO_TRACK_TYPE_RANKING
        }
        return Comparator.comparing<AudioStream, AudioTrackType>(
            { it.audioTrackType },
            Comparator { o1: AudioTrackType?, o2: AudioTrackType? ->
                if (preferOriginalAudio) {
                    java.lang.Boolean.compare(
                        o1 == AudioTrackType.ORIGINAL,
                        o2 == AudioTrackType.ORIGINAL
                    )
                } else {
                    0
                }
            }
        ).thenComparing(
            { it.audioLocale },
            Comparator.nullsFirst(
                Comparator.comparing { locale: Locale? -> locale?.isO3Language == langCode }
            )
        )
            .thenComparing(
                { it.audioTrackType },
                Comparator.nullsFirst(Comparator.comparingInt { trackTypeRanking.indexOf(it) })
            )
            .thenComparing(
                { it.audioLocale },
                Comparator.nullsFirst(
                    Comparator.comparing { locale: Locale? -> locale?.isO3Language == Locale.ENGLISH.isO3Language }
                )
            )
    }

    /**
     * Get a [Comparator] to compare [AudioStream]s by their languages and track types
     * for alphabetical sorting.
     *
     * @return Comparator
     */
    private fun getAudioTrackNameComparator(): Comparator<AudioStream> {
        val appLoc = Localization.getAppLocale()
        return Comparator.comparing<AudioStream, Locale>(
            { it.audioLocale },
            Comparator.nullsLast(Comparator.comparing { locale: Locale? -> locale?.getDisplayName(appLoc) ?: "" })
        )
            .thenComparing(
                { it.audioTrackType },
                Comparator.nullsLast(
                    Comparator.naturalOrder<AudioTrackType>()
                )
            )
    }

    // ------Extensions--------
    fun VideoStream.toQuality(): VideoQuality {
        return parseQuality(getResolution(), format)
    }

    /**
     * Extension on Iterable<VideoStream> that maps each VideoStream
     * to a [VideoStreamWithQuality] using its toQuality() function.
     * This avoids repeatedly parsing the quality string during matching.
     */
    fun Iterable<VideoStream>.wrapWithQuality(): List<VideoStreamWithQuality> {
        return this.map { stream -> VideoStreamWithQuality(stream, stream.toQuality()) }
    }

    // --------data classes----------

    /**
     *  containing the stream and its parsed [VideoQuality] information
     */
    data class VideoStreamWithQuality(
        val stream: VideoStream,
        val quality: VideoQuality
    )

    data class VideoQuality(
        val resolution: Int,
        val fps: Int,
        val bitrate: Long,
        val formatRank: Int
    )

    // -------- helper ------------

    val QUALITY_REGEX = Regex("""^(\d+)p(\d+)?(?:@(\d+)([km])?)?$""", RegexOption.IGNORE_CASE)

    /**
     * Parses a video quality string into a [VideoQuality] object.
     *
     * Supports strings like: `"720p"`, `"720p60"`, `"720p60@1500k"` or `"1080p@2m"`.
     * The components represent resolution, optional fps, and optional bitrate.
     * Bitrate units `k` and `m` are interpreted as ×1000 and ×1_000_000 respectively.
     *
     * @param resFpsBitrate string to parse for quality information (e.g. `"720p60@1500k"`), may be null
     * @param format        optional media format used to determine the format rank
     * @return the parsed [VideoQuality] or a zero-quality fallback if parsing fails
     *         or [resFpsBitrate] was null
     */
    private fun parseQuality(
        resFpsBitrate: String?,
        format: MediaFormat? = null
    ): VideoQuality {
        val resFpsBitrateStr = resFpsBitrate?.trim() ?: return VideoQuality(0, 0, 0L, -1)

        val match = QUALITY_REGEX.matchEntire(resFpsBitrateStr)
            ?: run {
                if (MainActivity.DEBUG) println("QualityParser" + "Cannot parse: \"$resFpsBitrateStr\"")
                return VideoQuality(0, 0, 0L, -1)
            }

        val resolution = match.groupValues[1].toInt()
        val fps = match.groupValues[2].toIntOrNull() ?: 0

        var bitrate = match.groupValues[3].toLongOrNull() ?: 0L
        val bitrateUnit = match.groupValues[4].lowercase()
        if (bitrate > 0 && bitrateUnit.isNotEmpty()) {
            try {
                bitrate = when (bitrateUnit) {
                    "k" -> Math.multiplyExact(bitrate, 1000L)
                    "m" -> Math.multiplyExact(bitrate, 1_000_000L)
                    else -> bitrate
                }
            } catch (e: ArithmeticException) {
                if (MainActivity.DEBUG) println("QualityParser" + "Bitrate overflow in \"$resFpsBitrateStr\"")
                bitrate = 0L
            }
        }

        return VideoQuality(
            resolution,
            fps,
            bitrate,
            format?.let { VIDEO_FORMAT_QUALITY_RANKING.indexOf(it) } ?: -1
        )
    }
}
