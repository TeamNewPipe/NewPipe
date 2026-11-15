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
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ContentBlockingHelper

/** Bounded, device-local model. No history import, network requests, or sync serialization. */
object LocalRecommendations {
    const val PREFERENCES = "local_recommendations"
    private const val MAX_CANDIDATES = 200
    private const val MAX_TERMS = 200
    private const val MAX_CHANNELS = 100
    private const val ACTIVE_CHANNEL_DAYS = 30L

    data class TasteSummary(val interests: List<String>, val channels: List<String>)

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

    private fun learns(context: Context): Boolean = enabled(context) && !setting(context, "paused")

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
            .remove(key(context, "topics")).putLong(key(context, "generation"), System.nanoTime()).apply()
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
    @Synchronized
    fun observe(context: Context, items: List<InfoItem>) {
        if (!learns(context)) return
        val model = read(context)
        val isEligible = eligibility(context)
        items.filterIsInstance<StreamInfoItem>().take(MAX_CANDIDATES).forEach { item ->
            if (isEligible(item) && id(item) !in model.watched) {
                model.candidates.remove(id(item))
                model.candidates[id(item)] = Candidate.from(item)
            }
        }
        write(context, model)
    }

    @JvmStatic
    @Synchronized
    fun searched(context: Context, serviceId: Int, query: String, profileId: String) {
        if (profileId != profile(context) || !learns(context) ||
            !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.enable_search_history_key), true)
        ) {
            return
        }
        val model = read(context, profileId)
        addTerms(model, serviceId, query, 2.0)
        write(context, model, profileId)
    }

    @JvmStatic
    @Synchronized
    fun watched(context: Context, info: StreamInfo, seconds: Long) {
        if (!learns(context) || !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_watch_history_key), true)
        ) {
            return
        }
        val model = read(context)
        val item = StreamInfoItem(info.serviceId, info.url, info.name, info.streamType).apply {
            uploaderName = info.uploaderName
            uploaderUrl = info.uploaderUrl
        }
        if (!eligibility(context)(item)) return
        val streamId = id(item)
        model.watched.remove(streamId)
        model.watched.add(streamId)
        model.candidates.remove(streamId)
        val engagement = if (seconds == 0L) 1.0 else seconds.coerceIn(0, 60) / 30.0
        val amount = engagement * (1.0 + qualityBoost(info.likeCount, info.viewCount))
        addTerms(model, info.serviceId, "${info.name} ${info.uploaderName}", amount)
        addChannel(model, info.serviceId, info.uploaderUrl, info.uploaderName, amount * 0.75)
        write(context, model)
    }

    @JvmStatic
    @Synchronized
    fun liked(context: Context, info: StreamInfo, liked: Boolean) {
        if (!learns(context)) return
        val model = read(context)
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
        write(context, model)
    }

    /** Returns a fresh list; extractor/cache results are never reordered in place. */
    @JvmStatic
    @Synchronized
    fun rank(context: Context, items: List<StreamInfoItem>): List<StreamInfoItem> {
        if (!enabled(context)) return items.toList()
        val model = read(context)
        val isEligible = eligibility(context)
        return items.filter { isEligible(it) && id(it) !in model.watched }
            .distinctBy(::id).sortedByDescending { recommendationScore(model, it) }
    }

    @JvmStatic
    @Synchronized
    fun feed(context: Context, serviceId: Int): List<StreamInfoItem> {
        if (!enabled(context)) return emptyList()
        return rank(context, read(context).candidates.values.filter { it.service == serviceId }.map { it.item() })
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
    @Synchronized
    fun explanation(context: Context, item: StreamInfoItem): String {
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

            frequencyScore(model, item) > 0.0 && !item.uploaderName.isNullOrBlank() ->
                context.getString(R.string.local_recommendations_reason_recent, item.uploaderName)

            else -> context.getString(R.string.local_recommendations_reason_local)
        }
    }

    @JvmStatic
    @Synchronized
    fun tasteSummary(context: Context): TasteSummary {
        val model = read(context)
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

    private fun eligibility(context: Context): (StreamInfoItem) -> Boolean {
        val excluded = prefs(context).getStringSet(key(context, "excluded"), emptySet()).orEmpty()
        val hidden = topics(context).split(',', '\n').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
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

    private fun recommendationScore(model: Model, item: StreamInfoItem): Double {
        val text = "${item.name} ${item.uploaderName}"
        val terms = score(model.weights, item.serviceId, text) +
            score(model.likeWeights, item.serviceId, text)
        val channel = channelId(item.serviceId, item.uploaderUrl, item.uploaderName)
            ?.let { (model.channels[it] ?: 0.0) + (model.likeChannels[it] ?: 0.0) } ?: 0.0
        val popularity = if (item.viewCount > 0) (ln(item.viewCount + 1.0) / 100.0).coerceAtMost(0.20) else 0.0
        return terms + channel + frequencyScore(model, item) + popularity
    }

    private fun frequencyScore(model: Model, item: StreamInfoItem): Double {
        val key = channelId(item.serviceId, item.uploaderUrl, item.uploaderName) ?: return 0.0
        if ((model.channels[key] ?: 0.0) <= 0.0) return 0.0
        val cutoff = System.currentTimeMillis() - ACTIVE_CHANNEL_DAYS * 24 * 60 * 60 * 1000
        val recent = model.candidates.values.count { it.channelKey == key && it.uploaded >= cutoff }
        return ((recent - 1).coerceAtLeast(0) * 0.05).coerceAtMost(0.25)
    }

    private fun addTerms(model: Model, service: Int, text: String, amount: Double) {
        model.weights.replaceAll { _, value -> value * 0.995 }
        tokens(text).forEach { token ->
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
        tokens(text).forEach { token ->
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
        val watched: MutableSet<String> = linkedSetOf()
    )

    private data class Candidate(val service: Int, val url: String, val name: String, val uploader: String, val channel: String, val thumbnail: String, val duration: Long, val short: Boolean, val audio: Boolean, val views: Long, val uploaded: Long) {
        val channelKey: String? get() = LocalRecommendations.channelId(service, channel, uploader)
        fun item() = StreamInfoItem(service, url, name, if (audio) StreamType.AUDIO_STREAM else StreamType.VIDEO_STREAM).also {
            it.uploaderName = uploader
            it.uploaderUrl = channel
            it.thumbnailUrl = thumbnail
            it.duration = duration
            it.isShortFormContent = short
            it.viewCount = views
            if (uploaded > 0) {
                it.uploadDate = DateWrapper(OffsetDateTime.ofInstant(Instant.ofEpochMilli(uploaded), ZoneOffset.UTC))
            }
        }
        fun json() = JSONObject().put("service", service).put("url", url).put("name", name).put("uploader", uploader)
            .put("channel", channel).put("thumbnail", thumbnail).put("duration", duration).put("short", short).put("audio", audio)
            .put("views", views).put("uploaded", uploaded)
        companion object {
            fun from(item: StreamInfoItem) = Candidate(item.serviceId, item.url.take(2048), item.name.take(300), item.uploaderName.orEmpty().take(200), item.uploaderUrl.orEmpty().take(2048), item.thumbnailUrl.orEmpty().take(2048), item.duration, item.isShortFormContent, item.streamType == StreamType.AUDIO_STREAM, item.viewCount, item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: -1)
            fun parse(json: JSONObject) = Candidate(json.getInt("service"), json.getString("url"), json.getString("name"), json.optString("uploader"), json.optString("channel"), json.optString("thumbnail"), json.optLong("duration", -1), json.optBoolean("short"), json.optBoolean("audio"), json.optLong("views", -1), json.optLong("uploaded", -1))
        }
    }

    private fun read(context: Context, profileId: String = profile(context)): Model = try {
        val json = JSONObject(prefs(context).getString("$profileId:model", "{}").orEmpty())
        val model = Model()
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
        model
    } catch (_: Exception) {
        Model()
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
            key(context, "model"),
            JSONObject().put("candidates", candidates)
                .put("weights", JSONObject(weights)).put("channels", JSONObject(channels))
                .put("likeWeights", JSONObject(likeWeights)).put("likeChannels", JSONObject(likeChannels))
                .put("channelLabels", JSONObject(labels))
                .put("liked", JSONArray(model.liked.toList().takeLast(MAX_CANDIDATES)))
                .put("watched", JSONArray(model.watched.toList().takeLast(MAX_CANDIDATES))).toString()
        ).apply()
    }
}
