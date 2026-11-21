package org.schabi.newpipe.recommendations

import android.content.Context
import androidx.preference.PreferenceManager
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.ln
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeContentDisclosure
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ContentBlockingHelper

/** Bounded, device-local model. No history import, network requests, or Device Sync serialization. */
object LocalRecommendations {
    const val PREFERENCES = "local_recommendations"
    private const val MAX_CANDIDATES = 200
    private const val MAX_TERMS = 200
    private const val MAX_CHANNELS = 100
    private const val ACTIVE_CHANNEL_DAYS = 30L
    private const val SHORT_VIDEO_SECONDS = 5 * 60L
    private const val SHORTS_RANKING_PENALTY = 2.0
    private const val SHORT_VIDEO_RANKING_PENALTY = 1.0
    private const val DISCLOSED_AI_LEARNING_MULTIPLIER = 0.10

    private val englishFillerWords = setOf(
        "and", "are", "for", "from", "had", "has", "have", "its", "that", "the", "their",
        "them", "these", "they", "this", "those", "was", "were", "with", "you", "your"
    )

    data class TasteSummary(val interests: List<String>, val channels: List<String>)
    data class FeedSnapshot(val items: List<StreamInfoItem>, val interests: List<String>)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private fun profile(context: Context) = ProfileManager.getActiveProfileId(context)
    private fun key(context: Context, setting: String) = "${profile(context)}:$setting"

    @JvmStatic
    fun setting(context: Context, name: String): Boolean = prefs(context).getBoolean(key(context, name), false)

    @JvmStatic
    fun enabled(context: Context): Boolean = setting(context, "enabled") && !IncognitoMode.isEnabled(context)

    @JvmStatic
    fun configurationToken(context: Context): String {
        val prefix = "${profile(context)}:"
        return prefix + prefs(context).all.filterKeys { it.startsWith(prefix) && it != prefix + "model" }.hashCode() + IncognitoMode.isEnabled(context)
    }

    /** Queued events use their captured profile's settings, not a later active profile. */
    private fun learns(context: Context, profileId: String): Boolean {
        return prefs(context).getBoolean("$profileId:enabled", false) &&
            !prefs(context).getBoolean("$profileId:paused", false) &&
            !IncognitoMode.isEnabled(context)
    }

    @JvmStatic
    @Synchronized
    fun configure(context: Context, values: Map<String, Boolean>, excludedTopics: String) {
        val editor = prefs(context).edit()
        values.forEach { (name, value) -> editor.putBoolean(key(context, name), value) }
        editor.putString(key(context, "topics"), excludedTopics.take(4000)).apply()
    }

    @JvmStatic
    fun topics(context: Context): String = prefs(context).getString(key(context, "topics"), "").orEmpty()

    @JvmStatic
    @Synchronized
    fun reset(context: Context) {
        prefs(context).edit().remove(key(context, "model")).remove(key(context, "excluded"))
            .remove(key(context, "topics")).remove(key(context, "ignored_interests"))
            .putLong(key(context, "generation"), System.nanoTime()).apply()
    }

    internal fun ignoredInterests(context: Context, profileId: String): Set<String> {
        val ignored = prefs(context).getStringSet("$profileId:ignored_interests", emptySet()).orEmpty()
        return if (NewPipe.getPreferredLocalization().languageCode == "en") {
            ignored + englishFillerWords
        } else {
            ignored
        }
    }

    @Synchronized
    internal fun removeInterests(context: Context, profileId: String, interests: Set<String>) {
        if (interests.isEmpty() || profileId != profile(context)) return
        val preferenceKey = "$profileId:ignored_interests"
        val saved = prefs(context).getStringSet(preferenceKey, emptySet()).orEmpty()
        prefs(context).edit().putStringSet(preferenceKey, saved + interests).apply()
        // Persist the pruned weights so removing an interest discards its accumulated score.
        write(context, read(context, profileId), profileId)
    }

    @JvmStatic
    @Synchronized
    fun exclude(context: Context, item: StreamInfoItem) {
        if (!enabled(context)) return
        val entries = prefs(context).getStringSet(key(context, "excluded"), emptySet()).orEmpty().toMutableSet()
        entries.add(id(item))
        prefs(context).edit().putStringSet(key(context, "excluded"), entries).apply()
        val model = read(context)
        model.candidates.remove(id(item))
        write(context, model)
    }

    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun observe(context: Context, items: List<InfoItem>, profileId: String = profile(context)) {
        if (profileId != profile(context) || !learns(context, profileId)) return
        val model = read(context, profileId)
        val isEligible = eligibility(context, profileId)
        items.filterIsInstance<StreamInfoItem>().take(MAX_CANDIDATES).forEach { item ->
            if (isEligible(item) && id(item) !in model.watched) {
                model.candidates.remove(id(item))
                model.candidates[id(item)] = Candidate.from(item)
            }
        }
        write(context, model, profileId)
    }

    @JvmStatic
    @Synchronized
    fun searched(context: Context, serviceId: Int, query: String, profileId: String) {
        if (profileId != profile(context) || !learns(context, profileId) ||
            !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.enable_search_history_key), true)
        ) {
            return
        }
        val model = read(context, profileId)
        addTerms(model, serviceId, query, 2.0)
        write(context, model, profileId)
    }

    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun watched(context: Context, info: StreamInfo, seconds: Long, profileId: String = profile(context)) {
        if (profileId != profile(context) || !learns(context, profileId) ||
            !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_watch_history_key), true)
        ) {
            return
        }
        val model = read(context, profileId)
        val item = StreamInfoItem(info.serviceId, info.url, info.name, info.streamType).apply {
            uploaderName = info.uploaderName
            uploaderUrl = info.uploaderUrl
        }
        if (!eligibility(context, profileId)(item)) return
        val streamId = id(item)
        model.watched.remove(streamId)
        model.watched.add(streamId)
        model.candidates.remove(streamId)
        val engagement = if (seconds == 0L) 1.0 else seconds.coerceIn(0, 60) / 30.0
        val amount = engagement * (1.0 + qualityBoost(info.likeCount, info.viewCount)) *
            passiveLearningMultiplier(context, info, profileId)
        addTerms(model, info.serviceId, "${info.name} ${info.uploaderName}", amount)
        addChannel(model, info.serviceId, info.uploaderUrl, info.uploaderName, amount * 0.75)
        write(context, model, profileId)
    }

    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun liked(context: Context, info: StreamInfo, liked: Boolean, profileId: String = profile(context)) {
        if (profileId != profile(context) || !learns(context, profileId)) return
        val model = read(context, profileId)
        val streamId = "${info.serviceId}:${info.url}"
        val changed = if (liked) model.liked.add(streamId) else model.liked.remove(streamId)
        if (!changed) return
        val direction = if (liked) 1.0 else -1.0
        addLikeTerms(model, info.serviceId, "${info.name} ${info.uploaderName}", direction * 4.0)
        addLikeChannel(
            model,
            info.serviceId,
            info.uploaderUrl,
            info.uploaderName,
            direction * 3.0
        )
        write(context, model, profileId)
    }

    /** Returns a fresh list; extractor/cache results are never reordered in place. */
    @JvmStatic
    fun rank(context: Context, items: List<StreamInfoItem>): List<StreamInfoItem> {
        if (!enabled(context)) return items.toList()
        return rankFromModel(context, read(context), items)
    }

    private fun rankFromModel(
        context: Context,
        model: Model,
        items: List<StreamInfoItem>
    ): List<StreamInfoItem> {
        val isEligible = eligibility(context)
        val options = rankingOptions(context, model)
        return items.filter { isEligible(it) && id(it) !in model.watched }
            .distinctBy(::id)
            .map { it to recommendationScore(model, it, options) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Stable positions without eligibility or watched-state filtering.
     * Reorders an already-filtered list without changing visibility.
     */
    @JvmStatic
    fun relevanceOrder(context: Context, items: List<StreamInfoItem>): Map<String, Int> {
        if (!enabled(context)) return emptyMap()
        val model = read(context)
        val options = rankingOptions(context, model)
        return items
            .map { id(it) to recommendationScore(model, it, options) }
            .sortedByDescending { it.second }
            .mapIndexed { position, item -> item.first to position }
            .toMap()
    }

    /** One model snapshot supplies both ranked videos and interest chips. */
    @JvmStatic
    fun feedSnapshot(context: Context, serviceId: Int): FeedSnapshot {
        if (!enabled(context)) return FeedSnapshot(emptyList(), emptyList())
        val model = read(context)
        val candidates = model.candidates.values
            .filter { it.service == serviceId }
            .map { it.item() }
        return FeedSnapshot(rankFromModel(context, model, candidates), summarize(model).interests)
    }

    @JvmStatic
    fun feed(context: Context, serviceId: Int): List<StreamInfoItem> {
        return feedSnapshot(context, serviceId).items
    }

    @JvmStatic
    fun shorts(context: Context, items: List<StreamInfoItem>): List<StreamInfoItem> = if (enabled(context) && setting(context, "shorts")) rank(context, items) else items

    @JvmStatic
    fun autoplay(context: Context, items: List<InfoItem>, serviceId: Int, shorts: Boolean, queuedUrls: Set<String>): StreamInfoItem? {
        if (!enabled(context) || !setting(context, "autoplay")) return null
        val rules = ContentBlockingHelper.getRules(context, ContentBlockingHelper.Target.RELATED_ITEMS)
        return rank(context, items.filterIsInstance<StreamInfoItem>().filterNot(rules::isBlocked))
            .firstOrNull { it.serviceId == serviceId && (!shorts || it.isShortFormContent) && it.url !in queuedUrls }
    }

    @JvmStatic
    fun explanation(context: Context, item: StreamInfoItem): String {
        if (setting(context, "reduce_shorts") && item.isShortFormContent) {
            return context.getString(R.string.local_recommendations_reason_reduce_shorts)
        }
        if (setting(context, "reduce_short_videos") && isRegularShortVideo(item)) {
            return context.getString(R.string.local_recommendations_reason_reduce_short_video)
        }
        val model = read(context)
        val channelKey = channelId(item.serviceId, item.uploaderUrl, item.uploaderName)
        val channelWeight = channelKey?.let {
            (model.channels[it] ?: 0.0) + (model.likeChannels[it] ?: 0.0)
        } ?: 0.0
        val bestInterest = tokens("${item.name} ${item.uploaderName}")
            .map {
                val key = "${item.serviceId}:$it"
                it to ((model.weights[key] ?: 0.0) + (model.likeWeights[key] ?: 0.0))
            }
            .maxByOrNull { it.second }
            ?.takeIf { it.second > 0.0 }
        return when {
            channelWeight > (bestInterest?.second ?: 0.0) * 0.5 && !item.uploaderName.isNullOrBlank() ->
                context.getString(R.string.local_recommendations_reason_channel, item.uploaderName)

            bestInterest != null -> context.getString(R.string.local_recommendations_reason_interest, bestInterest.first)

            frequencyScore(model, item, recentChannelCounts(model)) > 0.0 && !item.uploaderName.isNullOrBlank() ->
                context.getString(R.string.local_recommendations_reason_recent, item.uploaderName)

            else -> context.getString(R.string.local_recommendations_reason_local)
        }
    }

    @JvmStatic
    @Synchronized
    fun tasteSummary(context: Context): TasteSummary = summarize(read(context))

    private fun summarize(model: Model): TasteSummary {
        val interests = (model.weights.keys + model.likeWeights.keys).asSequence()
            .distinct()
            .sortedByDescending { (model.weights[it] ?: 0.0) + (model.likeWeights[it] ?: 0.0) }
            .map { it.substringAfter(':') }.distinct().take(12).toList()
        val channels = (model.channels.keys + model.likeChannels.keys).asSequence()
            .distinct()
            .sortedByDescending { (model.channels[it] ?: 0.0) + (model.likeChannels[it] ?: 0.0) }
            .mapNotNull { model.channelLabels[it] }.distinct().take(8).toList()
        return TasteSummary(interests, channels)
    }

    @JvmStatic
    fun matchesInterest(item: StreamInfoItem, interest: String): Boolean = interest
        .lowercase(Locale.ROOT) in tokens("${item.name} ${item.uploaderName}")

    private fun eligibility(context: Context, profileId: String = profile(context)): (StreamInfoItem) -> Boolean {
        val excluded = prefs(context).getStringSet("$profileId:excluded", emptySet()).orEmpty()
        val hidden = prefs(context).getString("$profileId:topics", "").orEmpty().split(',', '\n').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        val rules = ContentBlockingHelper.getRules(context, ContentBlockingHelper.Target.KIOSKS)
        return { item ->
            val text = "${item.name} ${item.uploaderName}".lowercase(Locale.ROOT)
            item.url.length <= 2048 && (item.url.startsWith("https://") || item.url.startsWith("http://")) &&
                !item.requiresMembership() && item.streamType in listOf(StreamType.VIDEO_STREAM, StreamType.AUDIO_STREAM) &&
                id(item) !in excluded && hidden.none(text::contains) && !rules.isBlocked(item)
        }
    }

    internal fun tokens(text: String): Set<String> = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length in 3..40 }.take(40).toSet()

    internal fun score(weights: Map<String, Double>, service: Int, text: String): Double = tokens(text).sumOf { weights["$service:$it"] ?: 0.0 }

    internal fun qualityBoost(likes: Long, views: Long): Double {
        if (likes < 0) return 0.0
        val volume = (ln(likes + 1.0) / ln(100_001.0)).coerceIn(0.0, 1.0)
        val approval = if (views > 0) (likes.toDouble() / views).coerceIn(0.0, 0.20) / 0.20 else 0.0
        return (volume * 0.30 + approval * 0.30).coerceAtMost(0.60)
    }

    internal fun rankingPenalty(context: Context, item: StreamInfoItem): Double = when {
        setting(context, "reduce_shorts") && item.isShortFormContent ->
            SHORTS_RANKING_PENALTY

        setting(context, "reduce_short_videos") && isRegularShortVideo(item) ->
            SHORT_VIDEO_RANKING_PENALTY

        else -> 0.0
    }

    private fun isRegularShortVideo(item: StreamInfoItem): Boolean = !item.isShortFormContent &&
        item.duration in 1 until SHORT_VIDEO_SECONDS

    private fun passiveLearningMultiplier(context: Context, info: StreamInfo, profileId: String): Double {
        val hasProviderDisclosure = info.metaInfo.orEmpty().any {
            it is YoutubeContentDisclosure
        }
        return if (prefs(context).getBoolean("$profileId:reduce_disclosed_ai", false) && hasProviderDisclosure) {
            DISCLOSED_AI_LEARNING_MULTIPLIER
        } else {
            1.0
        }
    }

    private data class RankingOptions(
        val recentChannels: Map<String, Int>,
        val reduceShorts: Boolean,
        val reduceShortVideos: Boolean
    )

    private fun rankingOptions(context: Context, model: Model): RankingOptions {
        return RankingOptions(
            recentChannelCounts(model),
            setting(context, "reduce_shorts"),
            setting(context, "reduce_short_videos")
        )
    }

    private fun recentChannelCounts(model: Model): Map<String, Int> {
        val cutoff = System.currentTimeMillis() - ACTIVE_CHANNEL_DAYS * 24 * 60 * 60 * 1000
        return model.candidates.values.asSequence()
            .filter { it.uploaded >= cutoff }
            .mapNotNull { it.channelKey }
            .groupingBy { it }
            .eachCount()
    }

    private fun recommendationScore(
        model: Model,
        item: StreamInfoItem,
        options: RankingOptions
    ): Double {
        val text = "${item.name} ${item.uploaderName}"
        val terms = score(model.weights, item.serviceId, text) +
            score(model.likeWeights, item.serviceId, text)
        val channel = channelId(item.serviceId, item.uploaderUrl, item.uploaderName)
            ?.let {
                (model.channels[it] ?: 0.0) + (model.likeChannels[it] ?: 0.0)
            } ?: 0.0
        val popularity = if (item.viewCount > 0) {
            (ln(item.viewCount + 1.0) / 100.0).coerceAtMost(0.20)
        } else {
            0.0
        }
        val penalty = when {
            options.reduceShorts && item.isShortFormContent -> SHORTS_RANKING_PENALTY
            options.reduceShortVideos && isRegularShortVideo(item) -> SHORT_VIDEO_RANKING_PENALTY
            else -> 0.0
        }
        return terms + channel + frequencyScore(model, item, options.recentChannels) + popularity - penalty
    }

    private fun frequencyScore(
        model: Model,
        item: StreamInfoItem,
        recentChannels: Map<String, Int>
    ): Double {
        val key = channelId(item.serviceId, item.uploaderUrl, item.uploaderName) ?: return 0.0
        if ((model.channels[key] ?: 0.0) <= 0.0) return 0.0
        val recent = recentChannels[key] ?: 0
        return ((recent - 1).coerceAtLeast(0) * 0.05).coerceAtMost(0.25)
    }

    private fun addTerms(model: Model, service: Int, text: String, amount: Double) {
        model.weights.replaceAll { _, value -> value * 0.995 }
        tokens(text).filterNot { it in model.ignoredInterests }.forEach { token ->
            val key = "$service:$token"
            model.weights[key] = ((model.weights[key] ?: 0.0) + amount).coerceAtMost(100.0)
        }
    }

    private fun addChannel(model: Model, service: Int, url: String?, name: String?, amount: Double) {
        val key = channelId(service, url, name) ?: return
        model.channels.replaceAll { _, value -> value * 0.995 }
        model.channels[key] = ((model.channels[key] ?: 0.0) + amount).coerceAtMost(100.0)
        if (!name.isNullOrBlank()) model.channelLabels[key] = name.take(200)
    }

    private fun addLikeTerms(model: Model, service: Int, text: String, amount: Double) {
        tokens(text).filterNot { it in model.ignoredInterests }.forEach { token ->
            val key = "$service:$token"
            val value = ((model.likeWeights[key] ?: 0.0) + amount).coerceAtLeast(0.0)
            if (value == 0.0) model.likeWeights.remove(key) else model.likeWeights[key] = value
        }
    }

    private fun addLikeChannel(model: Model, service: Int, url: String?, name: String?, amount: Double) {
        val key = channelId(service, url, name) ?: return
        val value = ((model.likeChannels[key] ?: 0.0) + amount).coerceAtLeast(0.0)
        if (value == 0.0) model.likeChannels.remove(key) else model.likeChannels[key] = value
        if (!name.isNullOrBlank()) model.channelLabels[key] = name.take(200)
    }

    private fun channelId(service: Int, url: String?, name: String?): String? {
        val value = url?.takeIf { it.isNotBlank() } ?: name?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        return value?.let { "$service:${it.take(2048)}" }
    }

    private fun id(item: StreamInfoItem) = "${item.serviceId}:${item.url}"
    private class Model(
        val candidates: LinkedHashMap<String, Candidate> = linkedMapOf(),
        val weights: MutableMap<String, Double> = linkedMapOf(),
        val channels: MutableMap<String, Double> = linkedMapOf(),
        val likeWeights: MutableMap<String, Double> = linkedMapOf(),
        val likeChannels: MutableMap<String, Double> = linkedMapOf(),
        val channelLabels: MutableMap<String, String> = linkedMapOf(),
        val liked: MutableSet<String> = linkedSetOf(),
        val watched: MutableSet<String> = linkedSetOf(),
        val ignoredInterests: Set<String> = emptySet()
    )

    private data class Candidate(
        val service: Int,
        val url: String,
        val name: String,
        val uploader: String,
        val channel: String,
        val thumbnail: String,
        val avatar: String,
        val duration: Long,
        val short: Boolean,
        val audio: Boolean,
        val views: Long,
        val uploaded: Long
    ) {
        val channelKey: String? get() = LocalRecommendations.channelId(service, channel, uploader)

        fun item() = StreamInfoItem(
            service,
            url,
            name,
            if (audio) StreamType.AUDIO_STREAM else StreamType.VIDEO_STREAM
        ).also {
            it.uploaderName = uploader
            it.uploaderUrl = channel
            it.uploaderAvatarUrl = avatar
            it.thumbnailUrl = thumbnail
            it.duration = duration
            it.isShortFormContent = short
            it.viewCount = views
            if (uploaded > 0) {
                it.uploadDate = DateWrapper(OffsetDateTime.ofInstant(Instant.ofEpochMilli(uploaded), ZoneOffset.UTC))
            }
        }

        fun json() = JSONObject()
            .put("service", service)
            .put("url", url)
            .put("name", name)
            .put("uploader", uploader)
            .put("channel", channel)
            .put("thumbnail", thumbnail)
            .put("avatar", avatar)
            .put("duration", duration)
            .put("short", short)
            .put("audio", audio)
            .put("views", views)
            .put("uploaded", uploaded)

        companion object {
            fun from(item: StreamInfoItem) = Candidate(
                item.serviceId,
                item.url.take(2048),
                item.name.take(300),
                item.uploaderName.orEmpty().take(200),
                item.uploaderUrl.orEmpty().take(2048),
                item.thumbnailUrl.orEmpty().take(2048),
                item.uploaderAvatarUrl.orEmpty().take(2048),
                item.duration,
                item.isShortFormContent,
                item.streamType == StreamType.AUDIO_STREAM,
                item.viewCount,
                item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: -1
            )

            fun parse(json: JSONObject) = Candidate(
                json.getInt("service"),
                json.getString("url"),
                json.getString("name"),
                json.optString("uploader"),
                json.optString("channel"),
                json.optString("thumbnail"),
                // Older on-device recommendation models have no avatar field.
                json.optString("avatar"),
                json.optLong("duration", -1),
                json.optBoolean("short"),
                json.optBoolean("audio"),
                json.optLong("views", -1),
                json.optLong("uploaded", -1)
            )
        }
    }
    private fun read(context: Context, profileId: String = profile(context)): Model = try {
        val json = JSONObject(prefs(context).getString("$profileId:model", "{}").orEmpty())
        val model = Model(ignoredInterests = ignoredInterests(context, profileId))
        json.optJSONArray("candidates")?.let { array ->
            for (i in 0 until minOf(array.length(), MAX_CANDIDATES)) {
                val candidate = Candidate.parse(array.getJSONObject(i))
                model.candidates["${candidate.service}:${candidate.url}"] = candidate
            }
        }
        json.optJSONObject("weights")?.let { weights ->
            weights.keys().asSequence().take(MAX_TERMS).forEach { model.weights[it] = weights.optDouble(it, 0.0) }
        }
        json.optJSONObject("channels")?.let { channels ->
            channels.keys().asSequence().take(MAX_CHANNELS).forEach { model.channels[it] = channels.optDouble(it, 0.0) }
        }
        json.optJSONObject("likeWeights")?.let { weights ->
            weights.keys().asSequence().take(MAX_TERMS).forEach { model.likeWeights[it] = weights.optDouble(it, 0.0) }
        }
        json.optJSONObject("likeChannels")?.let { channels ->
            channels.keys().asSequence().take(MAX_CHANNELS).forEach { model.likeChannels[it] = channels.optDouble(it, 0.0) }
        }
        json.optJSONObject("channelLabels")?.let { labels ->
            labels.keys().asSequence().take(MAX_CHANNELS).forEach { model.channelLabels[it] = labels.optString(it).take(200) }
        }
        json.optJSONArray("watched")?.let { array ->
            for (i in 0 until minOf(array.length(), MAX_CANDIDATES)) model.watched.add(array.getString(i))
        }
        json.optJSONArray("liked")?.let { array ->
            for (i in 0 until minOf(array.length(), MAX_CANDIDATES)) model.liked.add(array.getString(i))
        }
        model.weights.keys.removeAll { it.substringAfter(':') in model.ignoredInterests }
        model.likeWeights.keys.removeAll { it.substringAfter(':') in model.ignoredInterests }
        model
    } catch (_: Exception) {
        Model(ignoredInterests = ignoredInterests(context, profileId))
    }

    private fun write(context: Context, model: Model, profileId: String = profile(context)) {
        val candidates = JSONArray(model.candidates.values.toList().takeLast(MAX_CANDIDATES).map { it.json() })
        val weights = model.weights.entries.sortedByDescending { it.value }.take(MAX_TERMS).associate { it.key to it.value }
        val channels = model.channels.entries.sortedByDescending { it.value }.take(MAX_CHANNELS).associate { it.key to it.value }
        val likeWeights = model.likeWeights.entries.sortedByDescending { it.value }.take(MAX_TERMS).associate { it.key to it.value }
        val likeChannels = model.likeChannels.entries.sortedByDescending { it.value }.take(MAX_CHANNELS).associate { it.key to it.value }
        val labels = (channels.keys + likeChannels.keys)
            .mapNotNull { key -> model.channelLabels[key]?.let { key to it } }.toMap()
        prefs(context).edit().putString(
            "$profileId:model",
            JSONObject().put("candidates", candidates)
                .put("weights", JSONObject(weights)).put("channels", JSONObject(channels))
                .put("likeWeights", JSONObject(likeWeights)).put("likeChannels", JSONObject(likeChannels))
                .put("channelLabels", JSONObject(labels))
                .put("liked", JSONArray(model.liked.toList().takeLast(MAX_CANDIDATES)))
                .put("watched", JSONArray(model.watched.toList().takeLast(MAX_CANDIDATES))).toString()
        ).apply()
    }
}
