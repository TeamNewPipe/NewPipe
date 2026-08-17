package org.schabi.newpipe.util

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Collections
import java.util.Locale
import java.util.Objects
import java.util.function.Predicate

object ListHelper {
    // Video format in order of quality. 0=lowest quality, n=highest quality
    private val VIDEO_FORMAT_QUALITY_RANKING = listOf(
        MediaFormat.v3GPP, MediaFormat.WEBM, MediaFormat.MPEG_4
    )

    // Audio format in order of quality. 0=lowest quality, n=highest quality
    private val AUDIO_FORMAT_QUALITY_RANKING = listOf(
        MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A
    )

    // Audio format in order of efficiency. 0=least efficient, n=most efficient
    private val AUDIO_FORMAT_EFFICIENCY_RANKING = listOf(
        MediaFormat.MP3, MediaFormat.M4A, MediaFormat.WEBMA
    )

    // Use a Set for better performance
    private val HIGH_RESOLUTION_LIST = setOf("1440p", "2160p")

    // Audio track types in order of priority. 0=lowest, n=highest
    private val AUDIO_TRACK_TYPE_RANKING = listOf(
        AudioTrackType.DESCRIPTIVE, AudioTrackType.SECONDARY, AudioTrackType.DUBBED,
        AudioTrackType.ORIGINAL
    )

    // Audio track types in order of priority when descriptive audio is preferred.
    private val AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE = listOf(
        AudioTrackType.SECONDARY, AudioTrackType.DUBBED, AudioTrackType.ORIGINAL,
        AudioTrackType.DESCRIPTIVE
    )

    /**
     * List of supported YouTube Itag ids.
     * The original order is kept.
     * @see org.schabi.newpipe.extractor.services.youtube.ItagItem
     */
    private val SUPPORTED_ITAG_IDS = listOf(
        17, 36, // video v3GPP
        18, 34, 35, 59, 78, 22, 37, 38, // video MPEG4
        43, 44, 45, 46, // video webm
        171, 172, 139, 140, 141, 249, 250, 251, // audio
        160, 133, 134, 135, 212, 136, 298, 137, 299, 266, // video only
        278, 242, 243, 244, 245, 246, 247, 248, 271, 272, 302, 303, 308, 313, 315
    )

    @JvmStatic
    fun getDefaultResolutionIndex(context: Context, videoStreams: List<VideoStream>): Int {
        val defaultResolution = computeDefaultResolution(
            context,
            R.string.default_resolution_key, R.string.default_resolution_value
        )
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    @JvmStatic
    fun getResolutionIndex(context: Context, videoStreams: List<VideoStream>, defaultResolution: String): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    @JvmStatic
    fun getPopupDefaultResolutionIndex(context: Context, videoStreams: List<VideoStream>): Int {
        val defaultResolution = computeDefaultResolution(
            context,
            R.string.default_popup_resolution_key, R.string.default_popup_resolution_value
        )
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    @JvmStatic
    fun getPopupResolutionIndex(context: Context, videoStreams: List<VideoStream>, defaultResolution: String): Int {
        return getDefaultResolutionWithDefaultFormat(context, defaultResolution, videoStreams)
    }

    @JvmStatic
    fun getDefaultAudioFormat(context: Context, audioStreams: List<AudioStream>): Int {
        return getAudioIndexByHighestRank(
            audioStreams,
            getAudioTrackComparator(context).thenComparing(getAudioFormatComparator(context))
        )
    }

    @JvmStatic
    fun getDefaultAudioTrackGroup(context: Context, groupedAudioStreams: List<List<AudioStream>>?): Int {
        if (groupedAudioStreams.isNullOrEmpty()) {
            return -1
        }

        val cmp = getAudioTrackComparator(context)
        val highestRanked = groupedAudioStreams.maxWithOrNull { o1, o2 ->
            cmp.compare(o1[0], o2[0])
        }
        return groupedAudioStreams!!.indexOf(highestRanked)
    }

    @JvmStatic
    fun getAudioFormatIndex(context: Context, audioStreams: List<AudioStream>, trackId: String?): Int {
        if (trackId != null) {
            for (i in audioStreams.indices) {
                val s = audioStreams[i]
                if (s.audioTrackId != null && s.audioTrackId == trackId) {
                    return i
                }
            }
        }
        return getDefaultAudioFormat(context, audioStreams)
    }

    @JvmStatic
    fun <S : Stream> getStreamsOfSpecifiedDelivery(
        streamList: List<S>?,
        deliveryMethod: DeliveryMethod
    ): List<S> {
        return getFilteredStreamList(streamList) { it.deliveryMethod == deliveryMethod }
    }

    @JvmStatic
    fun <S : Stream> getUrlAndNonTorrentStreams(streamList: List<S>?): List<S> {
        return getFilteredStreamList(streamList) { it.isUrl && it.deliveryMethod != DeliveryMethod.TORRENT }
    }

    @JvmStatic
    fun <S : Stream> getPlayableStreams(streamList: List<S>?, serviceId: Int): List<S> {
        val youtubeServiceId = YouTube.serviceId
        return getFilteredStreamList(streamList) { stream ->
            stream.deliveryMethod != DeliveryMethod.TORRENT &&
                (stream.deliveryMethod != DeliveryMethod.HLS || stream.format != MediaFormat.OPUS) &&
                (serviceId != youtubeServiceId || stream.itagItem == null || SUPPORTED_ITAG_IDS.contains(stream.itagItem!!.id))
        }
    }

    @JvmStatic
    fun getSortedStreamVideosList(
        context: Context,
        videoStreams: List<VideoStream>?,
        videoOnlyStreams: List<VideoStream>?,
        ascendingOrder: Boolean,
        preferVideoOnlyStreams: Boolean
    ): List<VideoStream> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val showHigherResolutions = preferences.getBoolean(
            context.getString(R.string.show_higher_resolutions_key), false
        )
        val defaultFormat = getDefaultFormat(
            context,
            R.string.default_video_format_key, R.string.default_video_format_value
        )

        return getSortedStreamVideosList(
            defaultFormat, showHigherResolutions, videoStreams,
            videoOnlyStreams, ascendingOrder, preferVideoOnlyStreams
        )
    }

    @JvmStatic
    fun getSortedResolutionList(
        resources: Resources,
        defaultResolutionKey: Int,
        additionalResolutionKey: Int,
        showHigherResolutions: Boolean
    ): List<String> {
        val resolutions = resources.getStringArray(defaultResolutionKey).toMutableList()
        if (!showHigherResolutions) {
            return resolutions
        }
        val additionalResolutions = resources.getStringArray(additionalResolutionKey)
        // keep "best resolution" at the top
        resolutions.addAll(1, additionalResolutions.toList())
        return resolutions
    }

    @JvmStatic
    fun isHighResolutionSelected(
        selectedResolution: String,
        additionalResolutionKey: Int,
        resources: Resources
    ): Boolean {
        return resources.getStringArray(additionalResolutionKey).contains(selectedResolution)
    }

    @JvmStatic
    fun getFilteredAudioStreams(context: Context, audioStreams: List<AudioStream>?): List<AudioStream> {
        if (audioStreams == null) {
            return emptyList()
        }

        val collectedStreams = HashMap<String, AudioStream>()
        val cmp = getAudioFormatComparator(context)

        for (stream in audioStreams) {
            if (stream.deliveryMethod == DeliveryMethod.TORRENT ||
                (stream.deliveryMethod == DeliveryMethod.HLS && stream.format == MediaFormat.OPUS)
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

    @JvmStatic
    fun getGroupedAudioStreams(context: Context, audioStreams: List<AudioStream>?): List<List<AudioStream>> {
        if (audioStreams == null) {
            return emptyList()
        }

        val collectedStreams = HashMap<String, MutableList<AudioStream>>()

        for (stream in audioStreams) {
            val trackId = Objects.toString(stream.audioTrackId, "")
            collectedStreams.getOrPut(trackId) { mutableListOf() }.add(stream)
        }

        // Filter unknown audio tracks if there are multiple tracks
        if (collectedStreams.size > 1) {
            collectedStreams.remove("")
        }

        // Sort tracks alphabetically, sort track streams by quality
        val nameCmp = getAudioTrackNameComparator()
        val formatCmp = getAudioFormatComparator(context)

        return collectedStreams.values.asSequence()
            .sortedWith { o1, o2 -> nameCmp.compare(o1[0], o2[0]) }
            .map { streams -> streams.sortedWith(formatCmp) }
            .toList()
    }

    private fun <S : Stream> getFilteredStreamList(
        streamList: List<S>?,
        streamListPredicate: (S) -> Boolean
    ): List<S> {
        return streamList?.filter(streamListPredicate) ?: emptyList()
    }

    private fun computeDefaultResolution(context: Context, key: Int, value: Int): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var resolution = preferences.getString(
            context.getString(key),
            context.getString(value)
        ) ?: context.getString(R.string.best_resolution_key)

        val maxResolution = getResolutionLimit(context)
        if (maxResolution != null && (resolution == context.getString(R.string.best_resolution_key) ||
            compareVideoStreamResolution(maxResolution, resolution) < 1)
        ) {
            resolution = maxResolution
        }
        return resolution
    }

    @JvmStatic
    fun getDefaultResolutionIndex(
        defaultResolution: String,
        bestResolutionKey: String,
        defaultFormat: MediaFormat?,
        videoStreams: MutableList<VideoStream>?
    ): Int {
        if (videoStreams.isNullOrEmpty()) {
            return -1
        }

        sortStreamList(videoStreams, false)
        if (defaultResolution == bestResolutionKey) {
            return 0
        }

        val defaultStreamIndex = getVideoStreamIndex(defaultResolution, defaultFormat, videoStreams)
        return if (defaultStreamIndex == -1) 0 else defaultStreamIndex
    }

    @JvmStatic
    fun getSortedStreamVideosList(
        defaultFormat: MediaFormat?,
        showHigherResolutions: Boolean,
        videoStreams: List<VideoStream>?,
        videoOnlyStreams: List<VideoStream>?,
        ascendingOrder: Boolean,
        preferVideoOnlyStreams: Boolean
    ): List<VideoStream> {
        val videoStreamsOrdered = if (preferVideoOnlyStreams) {
            listOf(videoStreams, videoOnlyStreams)
        } else {
            listOf(videoOnlyStreams, videoStreams)
        }

        val allInitialStreams = videoStreamsOrdered.asSequence()
            .filterNotNull()
            .flatten()
            .filter { stream ->
                showHigherResolutions || !HIGH_RESOLUTION_LIST.contains(
                    stream.resolution.replace("p\\d+$".toRegex(), "p")
                )
            }
            .toList()

        val hashMap = HashMap<String, VideoStream>()
        for (videoStream in allInitialStreams) {
            hashMap[videoStream.resolution] = videoStream
        }

        for (videoStream in allInitialStreams) {
            if (videoStream.format == defaultFormat) {
                hashMap[videoStream.resolution] = videoStream
            }
        }

        return sortStreamList(ArrayList(hashMap.values), ascendingOrder)
    }

    private fun sortStreamList(videoStreams: MutableList<VideoStream>, ascendingOrder: Boolean): List<VideoStream> {
        val comparator = Comparator.nullsLast(
            compareBy<VideoStream, String>(ListHelper::compareVideoStreamResolution) { it.resolution }
                .thenBy { VIDEO_FORMAT_QUALITY_RANKING.indexOf(it.format) }
        )
        if (ascendingOrder) {
            videoStreams.sortWith(comparator)
        } else {
            videoStreams.sortWith(comparator.reversed())
        }
        return videoStreams
    }

    @JvmStatic
    fun getAudioIndexByHighestRank(audioStreams: List<AudioStream>?, comparator: Comparator<AudioStream>): Int {
        if (audioStreams.isNullOrEmpty()) {
            return -1
        }

        val highestRankedAudioStream = audioStreams.maxWithOrNull(comparator)
        return audioStreams.indexOf(highestRankedAudioStream)
    }

    @JvmStatic
    fun getVideoStreamIndex(
        targetResolution: String,
        targetFormat: MediaFormat?,
        videoStreams: List<VideoStream>
    ): Int {
        var fullMatchIndex = -1
        var fullMatchNoRefreshIndex = -1
        var resMatchOnlyIndex = -1
        var resMatchOnlyNoRefreshIndex = -1
        var lowerResMatchNoRefreshIndex = -1
        val targetResolutionNoRefresh = targetResolution.replace("p\\d+$".toRegex(), "p")

        for (idx in videoStreams.indices) {
            val format = if (targetFormat == null) null else videoStreams[idx].format
            val resolution = videoStreams[idx].resolution
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
            if (lowerResMatchNoRefreshIndex == -1 && compareVideoStreamResolution(resolutionNoRefresh, targetResolutionNoRefresh) < 0) {
                lowerResMatchNoRefreshIndex = idx
            }
        }

        if (fullMatchIndex != -1) return fullMatchIndex
        if (fullMatchNoRefreshIndex != -1) return fullMatchNoRefreshIndex
        if (resMatchOnlyIndex != -1) return resMatchOnlyIndex
        if (resMatchOnlyNoRefreshIndex != -1) return resMatchOnlyNoRefreshIndex
        return lowerResMatchNoRefreshIndex
    }

    private fun getDefaultResolutionWithDefaultFormat(
        context: Context,
        defaultResolution: String,
        videoStreams: List<VideoStream>
    ): Int {
        val defaultFormat = getDefaultFormat(context, R.string.default_video_format_key, R.string.default_video_format_value)
        return getDefaultResolutionIndex(
            defaultResolution,
            context.getString(R.string.best_resolution_key),
            defaultFormat,
            ArrayList(videoStreams)
        )
    }

    private fun getDefaultFormat(context: Context, defaultFormatKey: Int, defaultFormatValueKey: Int): MediaFormat? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultFormat = context.getString(defaultFormatValueKey)
        val defaultFormatString = preferences.getString(context.getString(defaultFormatKey), defaultFormat) ?: defaultFormat
        return getMediaFormatFromKey(context, defaultFormatString)
    }

    private fun getMediaFormatFromKey(context: Context, formatKey: String): MediaFormat? {
        return when (formatKey) {
            context.getString(R.string.video_webm_key) -> MediaFormat.WEBM
            context.getString(R.string.video_mp4_key) -> MediaFormat.MPEG_4
            context.getString(R.string.video_3gp_key) -> MediaFormat.v3GPP
            context.getString(R.string.audio_webm_key) -> MediaFormat.WEBMA
            context.getString(R.string.audio_m4a_key) -> MediaFormat.M4A
            else -> null
        }
    }

    @JvmStatic
    fun compareVideoStreamResolution(r1: String, r2: String): Int {
        return try {
            val res1 = r1.replace("0p\\d+$".toRegex(), "1")
                .replace("[^\\d.]".toRegex(), "").toInt()
            val res2 = r2.replace("0p\\d+$".toRegex(), "1")
                .replace("[^\\d.]".toRegex(), "").toInt()
            res1 - res2
        } catch (e: NumberFormatException) {
            1
        }
    }

    @JvmStatic
    fun isLimitingDataUsage(context: Context): Boolean {
        return getResolutionLimit(context) != null
    }

    private fun getResolutionLimit(context: Context): String? {
        if (isMeteredNetwork(context)) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val defValue = context.getString(R.string.limit_data_usage_none_key)
            val value = preferences.getString(context.getString(R.string.limit_mobile_data_usage_key), defValue)
            return if (defValue == value) null else value
        }
        return null
    }

    @JvmStatic
    fun isMeteredNetwork(context: Context): Boolean {
        val manager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        return manager?.isActiveNetworkMetered == true
    }

    @JvmStatic
    fun getAudioFormatComparator(context: Context): Comparator<AudioStream> {
        val defaultFormat = getDefaultFormat(context, R.string.default_audio_format_key, R.string.default_audio_format_value)
        return getAudioFormatComparator(defaultFormat, isLimitingDataUsage(context))
    }

    @JvmStatic
    fun getAudioFormatComparator(defaultFormat: MediaFormat?, limitDataUsage: Boolean): Comparator<AudioStream> {
        val formatRanking = if (limitDataUsage) AUDIO_FORMAT_EFFICIENCY_RANKING else AUDIO_FORMAT_QUALITY_RANKING
        var bitrateComparator = compareBy<AudioStream> { it.averageBitrate }
        if (limitDataUsage) {
            bitrateComparator = bitrateComparator.reversed()
        }

        return compareBy<AudioStream> { it.format == defaultFormat }
            .thenComparing(bitrateComparator)
            .thenComparingInt { formatRanking.indexOf(it.format) }
    }

    @JvmStatic
    fun getAudioTrackComparator(context: Context): Comparator<AudioStream> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val preferredLanguage = Localization.getPreferredLocale(context)
        val preferOriginalAudio = preferences.getBoolean(context.getString(R.string.prefer_original_audio_key), true)
        val preferDescriptiveAudio = preferences.getBoolean(context.getString(R.string.prefer_descriptive_audio_key), false)
        return getAudioTrackComparator(preferredLanguage, preferOriginalAudio, preferDescriptiveAudio)
    }

    @JvmStatic
    fun getAudioTrackComparator(
        preferredLanguage: Locale,
        preferOriginalAudio: Boolean,
        preferDescriptiveAudio: Boolean
    ): Comparator<AudioStream> {
        val langCode = preferredLanguage.isO3Language
        val trackTypeRanking = if (preferDescriptiveAudio) AUDIO_TRACK_TYPE_RANKING_DESCRIPTIVE else AUDIO_TRACK_TYPE_RANKING

        return Comparator.comparing<AudioStream, Boolean>({
            if (preferOriginalAudio) it.audioTrackType == AudioTrackType.ORIGINAL else false
        }).thenComparing(
            { it.audioLocale },
            Comparator.nullsFirst(compareBy { it?.isO3Language == langCode })
        ).thenComparing(
            { it.audioTrackType },
            Comparator.nullsFirst(compareBy { trackTypeRanking.indexOf(it) })
        ).thenComparing(
            { it.audioLocale },
            Comparator.nullsFirst(compareBy { it?.isO3Language == Locale.ENGLISH.isO3Language })
        )
    }

    private fun getAudioTrackNameComparator(): Comparator<AudioStream> {
        val appLoc = Localization.getAppLocale()
        return compareBy<AudioStream, String?>(nullsLast()) { it.audioLocale?.getDisplayName(appLoc) }
            .thenComparing(compareBy<AudioStream, AudioTrackType?>(nullsLast()) { it.audioTrackType })
    }
}
