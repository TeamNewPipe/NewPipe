/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.preference.PreferenceManager

/** A global appearance choice, shared by the list and grid group layouts. */
object SubscriptionGroupRows {
    const val KEY = "subscription_group_visible_rows"
    const val UNLIMITED = 0

    fun get(context: Context): Int = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(KEY, "0")?.toIntOrNull()?.takeIf { it in 0..10 } ?: UNLIMITED
}
