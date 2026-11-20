package org.schabi.newpipe.local.feed

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable

/**
 * Profile-scoped, reversible All-feed exclusions. Subscriptions and their cached feed items
 * remain intact and continue to appear in explicitly selected Channel Groups.
 */
object FeedAllExclusions {
    private fun preferenceKey(profileId: String) = "feed_all_exclusions_$profileId"

    fun channelKey(serviceId: Int, url: String): String = "$serviceId:$url"

    private fun read(context: Context, profileId: String): Set<String> {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getStringSet(preferenceKey(profileId), emptySet()).orEmpty().toSet()
    }

    fun isExcluded(context: Context, profileId: String, serviceId: Int, url: String): Boolean {
        return channelKey(serviceId, url) in read(context, profileId)
    }

    fun toggle(context: Context, profileId: String, serviceId: Int, url: String): Boolean {
        val updated = read(context, profileId).toMutableSet()
        val key = channelKey(serviceId, url)
        val excluded = if (key in updated) {
            updated.remove(key)
            false
        } else {
            updated.add(key)
            true
        }
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit()
            .putStringSet(preferenceKey(profileId), updated)
            .apply()
        return excluded
    }

    /** Emits when exclusions change without polling or retaining a Fragment/Activity. */
    fun changes(context: Context, profileId: String): Flowable<Set<String>> {
        return Flowable.create<Set<String>>({ emitter ->
            val app = context.applicationContext
            val preferences = PreferenceManager.getDefaultSharedPreferences(app)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                if (changed == preferenceKey(profileId) && !emitter.isCancelled) {
                    emitter.onNext(read(app, profileId))
                }
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            emitter.setCancellable {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
            emitter.onNext(read(app, profileId))
        }, BackpressureStrategy.LATEST).distinctUntilChanged()
    }
}
