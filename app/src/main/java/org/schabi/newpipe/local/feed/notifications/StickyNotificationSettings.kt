package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.UUID
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager

/** A generation invalidates both persisted alerts and in-flight fetches after disabling. */
data class StickyNotificationSession(val profileId: String, val generation: String)

internal data class StickyNotificationState(val enabled: Boolean, val generation: String, val activeProfileId: String)

object StickyNotificationSettings {
    const val ENABLED_KEY = "sticky_stream_notifications"
    internal const val GENERATION_KEY = "sticky_stream_notifications_generation"
    private val changeLock = Any()

    fun isEnabled(context: Context): Boolean = read(context).enabled

    fun generation(context: Context): String = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(GENERATION_KEY, "").orEmpty()

    fun setEnabled(context: Context, enabled: Boolean) = set(context, ENABLED_KEY, enabled)

    fun setMasterEnabled(context: Context, enabled: Boolean) = set(context, context.getString(R.string.enable_streams_notifications), enabled)

    private fun set(context: Context, key: String, enabled: Boolean) {
        synchronized(changeLock) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preferences.edit().putBoolean(key, enabled)
            // Persist the switch and invalidation together, before any background cleanup. A quick
            // off/on or a process death must not make an older generation eligible again.
            if (preferences.getBoolean(key, false) != enabled || generation(context).isEmpty()) {
                editor.putString(GENERATION_KEY, UUID.randomUUID().toString())
            }
            editor.apply()
        }
        StickyNotificationCoordinator.requestReconcile(context)
    }

    internal fun ensureGeneration(context: Context) = synchronized(changeLock) {
        if (generation(context).isEmpty()) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(GENERATION_KEY, UUID.randomUUID().toString()).apply()
        }
    }

    internal fun invalidate(context: Context) = synchronized(changeLock) {
        // A queued preference callback may outlive an off/on edit on another thread. Recheck
        // under the same mutation lock instead of invalidating that newly enabled generation.
        if (!isEnabled(context)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(GENERATION_KEY, UUID.randomUUID().toString()).apply()
        }
    }

    fun capture(context: Context, profileId: String): StickyNotificationSession? {
        val state = read(context)
        if (!state.enabled || state.generation.isEmpty() || state.activeProfileId != profileId) return null
        return StickyNotificationSession(profileId, state.generation)
    }

    /** No coordinator or profile monitor: safe to call while the feed Room transaction is held. */
    internal fun isCurrent(context: Context, session: StickyNotificationSession): Boolean {
        val state = read(context)
        return state.enabled && state.generation == session.generation && state.activeProfileId == session.profileId
    }

    internal fun read(context: Context): StickyNotificationState {
        // Read an immutable snapshot: switch, generation and active profile must come from the
        // same atomic preference edit even when a worker races an off/on operation.
        val values = PreferenceManager.getDefaultSharedPreferences(context).all
        return StickyNotificationState(
            enabled = values[ENABLED_KEY] == true && values[context.getString(R.string.enable_streams_notifications)] == true,
            generation = values[GENERATION_KEY] as? String ?: "",
            activeProfileId = values[ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY] as? String ?: ProfileManager.DEFAULT_PROFILE_ID
        )
    }
}
