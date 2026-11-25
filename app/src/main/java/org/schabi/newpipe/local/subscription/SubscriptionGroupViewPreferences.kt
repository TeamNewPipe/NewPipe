/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.preference.PreferenceManager

/** The group Grid/List choice is shared by all profiles and independent of video layouts. */
internal object SubscriptionGroupViewPreferences {
    const val KEY = "subscription_group_list_view_mode"

    /** Null leaves the initial layout to the theme until the user explicitly chooses a mode. */
    fun getListViewMode(context: Context): Boolean? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return if (preferences.contains(KEY)) preferences.getBoolean(KEY, true) else null
    }

    fun setListViewMode(context: Context, listViewMode: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(KEY, listViewMode).apply()
    }
}
