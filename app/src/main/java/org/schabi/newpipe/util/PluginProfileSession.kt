/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.app.Activity
import android.util.Log
import androidx.preference.PreferenceManager
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.profiles.ProfileManager

/** Plugins may cache preferences at construction, so profile changes require a fresh process. */
object PluginProfileSession {
    @JvmStatic
    fun restartIfChanged(activity: Activity): Boolean {
        if (!PluginManager.requiresProfileRestart(activity)) return false
        // Profile creation/switching uses apply(). Finish those writes before ProcessPhoenix
        // terminates this process; an ordinary activity recreation cannot reset plugin caches.
        val saved = PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .putString(
                ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY,
                ProfileManager.getActiveProfileId(activity)
            )
            .commit()
        if (!saved) Log.w("PluginProfileSession", "Could not persist profile selection before restart")
        NavigationHelper.restartApp(activity)
        return true
    }
}
