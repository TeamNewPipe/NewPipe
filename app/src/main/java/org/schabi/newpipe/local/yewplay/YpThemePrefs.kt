/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** YewPlay: the theme preferences, for the Compose screens hosted in the app's fragments. */
object YpThemePrefs {
    fun themeKey(context: Context): String = read(
        context,
        R.string.theme_key,
        R.string.default_theme_value
    )

    fun nightThemeKey(context: Context): String = read(
        context,
        R.string.night_theme_key,
        R.string.default_night_theme_value
    )

    private fun read(context: Context, key: Int, default: Int): String {
        val fallback = context.getString(default)
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(key), fallback) ?: fallback
    }
}
