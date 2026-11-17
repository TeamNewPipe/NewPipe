package org.schabi.newpipe.recommendations

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.json.JSONObject
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper

/**
 * Imports and exports only aggregated recommendation signals.
 *
 * Candidate URLs, watched video identifiers, local-like identifiers, exclusions, and settings are
 * deliberately excluded from the portable format.
 */
object RecommendationTasteTransfer {
    const val MIME_TYPE = "application/json"

    private const val DATA_TYPE = "wize_recommendation_taste"
    private const val FORMAT_VERSION = 1
    private const val MAX_FILE_BYTES = 262_144
    private const val MAX_INTERESTS = 200
    private const val MAX_CHANNELS = 100
    private const val MAX_KEY_LENGTH = 2_048
    private const val MAX_LABEL_LENGTH = 200
    private const val MAX_WEIGHT = 100.0

    data class Summary(
        val interests: Int,
        val channels: Int
    )

    class Preview internal constructor(
        val interests: Int,
        val channels: Int,
        internal val payload: String
    )

    private data class Taste(
        val interests: LinkedHashMap<String, Double>,
        val channels: LinkedHashMap<String, Double>,
        val channelLabels: LinkedHashMap<String, String>
    )

    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun exportTo(context: Context, file: StoredFileHelper): Summary {
        val taste = portableTaste(context)
        if (taste.interests.isEmpty() && taste.channels.isEmpty()) {
            throw IOException("There is no learned recommendation taste to export yet")
        }
        val payload = JSONObject()
            .put("appName", "WizeStream")
            .put("dataType", DATA_TYPE)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdTimestamp", System.currentTimeMillis())
            .put("interests", JSONObject(taste.interests as Map<*, *>))
            .put("channels", JSONObject(taste.channels as Map<*, *>))
            .put("channelLabels", JSONObject(taste.channelLabels as Map<*, *>))
            .toString()

        SharpOutputStream(file.openAndTruncateStream()).bufferedWriter(Charsets.UTF_8).use {
            it.write(payload)
        }
        return Summary(taste.interests.size, taste.channels.size)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun inspect(file: StoredFileHelper): Preview {
        val payload = readBounded(file)
        val taste = parsePortable(payload)
        return Preview(taste.interests.size, taste.channels.size, payload)
    }

    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun apply(context: Context, preview: Preview, replace: Boolean): Summary {
        val imported = parsePortable(preview.payload)
        val profileId = ProfileManager.getActiveProfileId(context)
        val preferences = context.getSharedPreferences(
            LocalRecommendations.PREFERENCES,
            Context.MODE_PRIVATE
        )
        val modelKey = "$profileId:model"
        val model = try {
            JSONObject(preferences.getString(modelKey, "{}").orEmpty())
        } catch (_: Exception) {
            JSONObject()
        }

        val ignored = LocalRecommendations.ignoredInterests(context, profileId)
        val importedInterests = imported.interests.filterKeys { it.substringAfter(':') !in ignored }
        val interests = if (replace) {
            importedInterests
        } else {
            mergeWeights(
                readWeights(model.optJSONObject("weights"), MAX_INTERESTS)
                    .filterKeys { it.substringAfter(':') !in ignored },
                importedInterests,
                MAX_INTERESTS
            )
        }
        val channels = if (replace) {
            imported.channels
        } else {
            mergeWeights(
                readWeights(model.optJSONObject("channels"), MAX_CHANNELS),
                imported.channels,
                MAX_CHANNELS
            )
        }
        val labelKeys = if (replace) {
            channels.keys
        } else {
            channels.keys + readWeights(
                model.optJSONObject("likeChannels"),
                MAX_CHANNELS
            ).keys
        }
        val labels = if (replace) {
            imported.channelLabels
        } else {
            readLabels(model.optJSONObject("channelLabels"), labelKeys).apply {
                putAll(imported.channelLabels)
            }
        }.filterKeys { it in labelKeys }.entries.take(MAX_CHANNELS).associate { it.toPair() }

        model.put("weights", JSONObject(interests as Map<*, *>))
        model.put("channels", JSONObject(channels as Map<*, *>))
        model.put("channelLabels", JSONObject(labels as Map<*, *>))
        if (replace) {
            model.remove("likeWeights")
            model.remove("likeChannels")
        }

        val committed = preferences.edit()
            .putString(modelKey, model.toString())
            .putLong("$profileId:generation", System.nanoTime())
            .commit()
        if (!committed) {
            throw IOException("Unable to save imported recommendation taste")
        }
        return Summary(importedInterests.size, imported.channels.size)
    }

    private fun portableTaste(context: Context): Taste {
        val profileId = ProfileManager.getActiveProfileId(context)
        val preferences = context.getSharedPreferences(
            LocalRecommendations.PREFERENCES,
            Context.MODE_PRIVATE
        )
        val model = try {
            JSONObject(preferences.getString("$profileId:model", "{}").orEmpty())
        } catch (_: Exception) {
            JSONObject()
        }

        val ignored = LocalRecommendations.ignoredInterests(context, profileId)
        val interests = mergeWeights(
            readWeights(model.optJSONObject("weights"), MAX_INTERESTS)
                .filterKeys { it.substringAfter(':') !in ignored },
            readWeights(model.optJSONObject("likeWeights"), MAX_INTERESTS)
                .filterKeys { it.substringAfter(':') !in ignored },
            MAX_INTERESTS
        )
        val channels = mergeWeights(
            readWeights(model.optJSONObject("channels"), MAX_CHANNELS),
            readWeights(model.optJSONObject("likeChannels"), MAX_CHANNELS),
            MAX_CHANNELS
        )
        return Taste(
            interests,
            channels,
            readLabels(model.optJSONObject("channelLabels"), channels.keys)
        )
    }

    private fun parsePortable(payload: String): Taste {
        val json = try {
            JSONObject(payload)
        } catch (error: Exception) {
            throw IOException("This is not a valid WizeStream taste file", error)
        }
        if (json.optString("appName") != "WizeStream" ||
            json.optString("dataType") != DATA_TYPE ||
            json.optInt("formatVersion", -1) != FORMAT_VERSION
        ) {
            throw IOException("This taste file is unsupported or belongs to another app")
        }

        val interests = readWeights(json.optJSONObject("interests"), MAX_INTERESTS)
        val channels = readWeights(json.optJSONObject("channels"), MAX_CHANNELS)
        if (interests.isEmpty() && channels.isEmpty()) {
            throw IOException("This taste file does not contain transferable signals")
        }
        return Taste(
            interests,
            channels,
            readLabels(json.optJSONObject("channelLabels"), channels.keys)
        )
    }

    private fun readWeights(json: JSONObject?, limit: Int): LinkedHashMap<String, Double> {
        if (json == null) return linkedMapOf()
        return json.keys().asSequence()
            .mapNotNull { key ->
                val value = (json.opt(key) as? Number)?.toDouble()
                if (key.isNotBlank() && key.length <= MAX_KEY_LENGTH &&
                    key.substringBefore(':').toIntOrNull() != null &&
                    value != null && value.isFinite() && value > 0.0
                ) {
                    key to value.coerceAtMost(MAX_WEIGHT)
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .take(limit)
            .toMap(LinkedHashMap())
    }

    private fun readLabels(
        json: JSONObject?,
        channelKeys: Set<String>
    ): LinkedHashMap<String, String> {
        if (json == null) return linkedMapOf()
        return channelKeys.asSequence().mapNotNull { key ->
            json.optString(key).takeIf { it.isNotBlank() }?.take(MAX_LABEL_LENGTH)?.let {
                key to it
            }
        }.take(MAX_CHANNELS).toMap(LinkedHashMap())
    }

    private fun mergeWeights(
        current: Map<String, Double>,
        imported: Map<String, Double>,
        limit: Int
    ): LinkedHashMap<String, Double> = (current.keys + imported.keys).asSequence()
        .distinct()
        .map { key ->
            key to ((current[key] ?: 0.0) + (imported[key] ?: 0.0)).coerceAtMost(MAX_WEIGHT)
        }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }
        .take(limit)
        .toMap(LinkedHashMap())

    @Throws(IOException::class)
    private fun readBounded(file: StoredFileHelper): String {
        if (file.length() > MAX_FILE_BYTES) {
            throw IOException("The taste file is too large")
        }
        return SharpInputStream(file.stream).buffered().use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_FILE_BYTES) {
                        throw IOException("The taste file is too large")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray().decodeToString()
            }
        }
    }
}
