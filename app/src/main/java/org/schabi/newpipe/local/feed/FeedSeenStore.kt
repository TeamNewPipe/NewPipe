/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.security.MessageDigest
import org.schabi.newpipe.local.history.IncognitoMode

/**
 * A bounded, ordered ledger of feed-only markers. IDs are stable video identities, not stream
 * database row IDs, so clearing/rebuilding cached feed rows cannot assign markers to other videos.
 */
internal class FeedSeenLedger private constructor() {
    private val identities = LinkedHashSet<String>()

    fun contains(identity: String): Boolean = identities.contains(identity)

    fun update(identity: String, seen: Boolean): Boolean {
        if (identity.isEmpty()) return false
        val changed = if (seen) identities.add(identity) else identities.remove(identity)
        if (identities.size > MAX_ENTRIES) {
            val iterator = identities.iterator()
            iterator.next()
            iterator.remove()
        }
        return changed
    }

    fun serialize(): String = identities.joinToString("\n")

    companion object {
        const val MAX_ENTRIES = 5_000
        private const val HEX = "0123456789abcdef"

        fun restore(encoded: String?): FeedSeenLedger = FeedSeenLedger().apply {
            encoded?.split('\n')?.forEach { value ->
                if (value.length == 32 && value.all { it in '0'..'9' || it in 'a'..'f' }) {
                    update(value, true)
                }
            }
        }

        /** Truncated SHA-256 keeps individual video URLs out of the preference file. */
        fun identity(serviceId: Int, url: String): String {
            if (url.isBlank()) return ""
            val hash = MessageDigest.getInstance("SHA-256")
                .digest("$serviceId\u0000$url".toByteArray(Charsets.UTF_8))
            return buildString(32) {
                for (index in 0 until 16) {
                    val value = hash[index].toInt() and 0xff
                    append(HEX[value ushr 4])
                    append(HEX[value and 15])
                }
            }
        }
    }
}

/** Keeps the feed-scrolled state separate from playback progress, history and recommendations. */
internal class FeedSeenStore(context: Context, val profileId: String) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(appContext)
    private val storageKey = "feed_seen_v1_$profileId"
    private val ledger = FeedSeenLedger.restore(preferences.getString(storageKey, null))
    private var dirty = false

    private fun available(): Boolean = !IncognitoMode.isEnabled(appContext)

    @Synchronized
    fun isSeen(identity: String): Boolean = available() && ledger.contains(identity)

    @Synchronized
    fun update(identity: String, seen: Boolean): Boolean {
        if (!available()) return false
        val changed = ledger.update(identity, seen)
        if (changed) dirty = true
        return changed
    }

    /** One preference write per scrolling gesture, not one per item. */
    @Synchronized
    fun flush() {
        if (!dirty || !available()) return
        preferences.edit().putString(storageKey, ledger.serialize()).apply()
        dirty = false
    }
}
