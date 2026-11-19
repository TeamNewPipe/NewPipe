package org.schabi.newpipe.recommendations

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

/** Counts elapsed playing time, never seek distance or paused/background idle time. */
class RecommendationWatchTracker(private val context: Context) {
    private var info: StreamInfo? = null
    private var profile: String? = null
    private var lastSample: Long? = null
    private var elapsedMillis = 0L
    private val defaults = PreferenceManager.getDefaultSharedPreferences(context)
    private val local = context.getSharedPreferences(LocalRecommendations.PREFERENCES, Context.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == IncognitoMode.KEY || key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY ||
            key == context.getString(R.string.enable_watch_history_key) ||
            key?.endsWith(":enabled") == true || key?.endsWith(":paused") == true || key?.endsWith(":generation") == true
        ) {
            lastSample = null
            elapsedMillis = 0
        }
    }
    init {
        defaults.registerOnSharedPreferenceChangeListener(listener)
        local.registerOnSharedPreferenceChangeListener(listener)
    }
    fun start(value: StreamInfo) {
        info = value
        profile = ProfileManager.getActiveProfileId(context)
        lastSample = null
        elapsedMillis = 0
        profile?.let { RecommendationWorkDispatcher.watched(context, value, 0, it) }
    }
    fun update(playing: Boolean) {
        val value = info ?: return
        if (profile != ProfileManager.getActiveProfileId(context) || !LocalRecommendations.enabled(context) || LocalRecommendations.setting(context, "paused") ||
            !defaults.getBoolean(context.getString(R.string.enable_watch_history_key), true)
        ) {
            lastSample = null
            elapsedMillis = 0
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (playing) {
            lastSample?.let { elapsedMillis += (now - it).coerceIn(0, 5000) }
            lastSample = now
            if (elapsedMillis >= 15000) {
                val elapsed = elapsedMillis / 1000
                elapsedMillis = 0
                profile?.let { RecommendationWorkDispatcher.watched(context, value, elapsed, it) }
            }
        } else {
            lastSample = null
        }
    }
    fun stop() {
        info = null
        lastSample = null
        elapsedMillis = 0
    }
    fun close() {
        stop()
        defaults.unregisterOnSharedPreferenceChangeListener(listener)
        local.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
