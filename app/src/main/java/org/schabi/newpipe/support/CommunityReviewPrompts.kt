/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.support

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.schabi.newpipe.R
import org.schabi.newpipe.util.external_communication.ShareUtils

/** Installation-local prompt state. No counters, identifiers or review results are sent anywhere. */
class CommunityReviewPromptState(private val preferences: SharedPreferences) {
    init {
        if (!preferences.contains(OPTED_OUT)) {
            // The old flag mixed page openings with explicit opt-outs. Before 15 openings,
            // the automatic dialog could not have offered Don't ask again, so restore reminders.
            // At or above the threshold the old reason is unknown; preserve that choice.
            val preserveStop = preferences.getBoolean(LEGACY_STOPPED, false) &&
                preferences.getInt(OPENINGS, 0) >= 15
            preferences.edit().putBoolean(OPTED_OUT, preserveStop).remove(LEGACY_STOPPED).apply()
        }
    }

    fun recordOpening() {
        if (preferences.getBoolean(OPTED_OUT, false)) return
        preferences.edit().putInt(OPENINGS, (preferences.getInt(OPENINGS, 0).coerceIn(0, 15) + 1).coerceAtMost(15)).apply()
    }

    fun shouldPrompt(now: Long): Boolean = !preferences.getBoolean(OPTED_OUT, false) &&
        preferences.getInt(OPENINGS, 0) >= 15 && now >= reminderAt

    val reminderAt: Long
        get() = preferences.getLong(REMINDER_AT, 0L)

    fun remindLater(now: Long) {
        val deadline = now.coerceIn(0, Long.MAX_VALUE - FIVE_DAYS_MS) + FIVE_DAYS_MS
        preferences.edit().putLong(REMINDER_AT, deadline).apply()
    }

    fun stopAsking() {
        preferences.edit().putBoolean(OPTED_OUT, true).apply()
    }

    companion object {
        const val FIVE_DAYS_MS = 5L * 24 * 60 * 60 * 1_000
        private const val OPENINGS = "openings"
        private const val REMINDER_AT = "reminder_at"
        private const val OPTED_OUT = "opted_out"
        private const val LEGACY_STOPPED = "stopped"
    }
}

/** Process lifecycle ignores rotations and navigation between WizeStream activities. */
class CommunityReviewSessions(val state: CommunityReviewPromptState) : DefaultLifecycleObserver {
    private var foreground = false
    private var opening = 0L
    private var attemptedOpening = -1L

    override fun onStart(owner: LifecycleOwner) {
        if (foreground) return
        foreground = true
        opening++
        state.recordOpening()
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
    }

    fun claimAttempt(): Boolean {
        if (!foreground || attemptedOpening == opening) return false
        attemptedOpening = opening
        return true
    }
}

object CommunityReviews {
    private var sessions: CommunityReviewSessions? = null

    fun state(context: Context): CommunityReviewPromptState = CommunityReviewPromptState(
        context.applicationContext.getSharedPreferences("community_review_prompt", Context.MODE_PRIVATE)
    )

    fun initialize(context: Context) {
        if (sessions != null) return
        val created = CommunityReviewSessions(state(context))
        sessions = created
        ProcessLifecycleOwner.get().lifecycle.addObserver(created)
    }

    fun sessions(context: Context): CommunityReviewSessions {
        initialize(context)
        return checkNotNull(sessions)
    }

    fun destination(context: Context): String? = CommunityReviewLink.configuredUrl(context.getString(R.string.community_reviews_url))

    fun openPage(context: Context) {
        val url = destination(context) ?: return
        // Opening a browser does not confirm a submitted review or change reminder preferences.
        ShareUtils.openUrlInBrowser(context, url)
    }
}
