/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.content.Context
import androidx.preference.PreferenceManager

/** Stores a stable tab identity rather than a position that changes on reordering. */
object HomeStartupTab {
    private const val KEY = "home_startup_tab_json"

    @JvmStatic
    fun save(context: Context, tab: Tab?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        if (tab == null) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, TabsJsonHelper.getJsonToSave(listOf(tab)))
        }
        editor.apply()
    }

    @JvmStatic
    fun preferredIndex(context: Context, visibleTabs: List<Tab>): Int {
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY, null) ?: return -1
        val saved = runCatching { TabsJsonHelper.getTabsFromJson(stored).singleOrNull() }
            .getOrNull() ?: return -1
        return visibleTabs.indexOf(saved)
    }

    @JvmStatic
    fun startupIndex(context: Context, visibleTabs: List<Tab>): Int {
        return preferredIndex(context, visibleTabs).takeIf { it >= 0 } ?: 0
    }
}
