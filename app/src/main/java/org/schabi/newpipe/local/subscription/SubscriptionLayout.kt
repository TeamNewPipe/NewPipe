package org.schabi.newpipe.local.subscription

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.database.subscription.SubscriptionEntity

/** Presentation only: separators do not create feeds or change subscription membership. */
class SubscriptionLayout(context: Context, profileId: String) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val preferenceKey = "subscription_layout_$profileId"

    data class Entry(val key: String, val separator: String? = null)

    fun read(): List<Entry> = decode(preferences.getString(preferenceKey, null))

    fun save(entries: List<Entry>) {
        preferences.edit().putString(preferenceKey, encode(entries)).apply()
    }

    fun observe(): Flowable<List<Entry>> = Flowable.create<List<Entry>>({ emitter ->
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == preferenceKey || key == null) emitter.onNext(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        emitter.setCancellable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        emitter.onNext(read())
    }, BackpressureStrategy.LATEST).distinctUntilChanged()

    companion object {
        fun key(subscription: SubscriptionEntity): String = "channel:${subscription.serviceId}:${subscription.url}"

        /** Keeps surviving positions, drops removed channels, and leaves new channels ungrouped. */
        fun reconcile(saved: List<Entry>, channelKeys: List<String>): List<Entry> {
            val remaining = channelKeys.toMutableSet()
            val seen = mutableSetOf<String>()
            val result = saved.filter { entry ->
                seen.add(entry.key) && if (entry.separator != null) {
                    entry.separator.isNotBlank()
                } else {
                    remaining.remove(entry.key)
                }
            }
            val newEntries = channelKeys.filter { remaining.remove(it) }.map { Entry(it) }
            val firstSeparatorIndex = result.indexOfFirst { it.separator != null }
            return if (firstSeparatorIndex == -1) {
                result + newEntries
            } else {
                result.toMutableList().apply { addAll(firstSeparatorIndex, newEntries) }
            }
        }

        fun encode(entries: List<Entry>): String = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject().put("key", entry.key).apply {
                        entry.separator?.let { put("separator", it) }
                    }
                )
            }
        }.toString()

        fun decode(value: String?): List<Entry> = try {
            val array = JSONArray(value ?: "[]")
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                Entry(entry.getString("key"), if (entry.has("separator")) entry.getString("separator") else null)
            }.filter { it.key.isNotBlank() }.distinctBy { it.key }
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }
}
