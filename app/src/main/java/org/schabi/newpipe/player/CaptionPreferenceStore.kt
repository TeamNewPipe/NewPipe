/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.content.SharedPreferences
import org.schabi.newpipe.R

/** A caption choice is a global fallback until a channel has its own explicit override. */
internal object CaptionPreferenceStore {
    fun saveGlobalFallback(context: Context, prefs: SharedPreferences, language: String?) {
        val key = context.getString(R.string.caption_user_set_key)
        prefs.edit().apply {
            if (language == null) remove(key) else putString(key, language)
        }.apply()
    }

    fun resolve(
        context: Context,
        prefs: SharedPreferences,
        hasChannelOverride: Boolean,
        channelLanguage: String?
    ): String? = if (hasChannelOverride) {
        channelLanguage
    } else {
        prefs.getString(context.getString(R.string.caption_user_set_key), null)
    }
}
