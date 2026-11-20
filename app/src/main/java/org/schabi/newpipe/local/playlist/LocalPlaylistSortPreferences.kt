/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.content.Context

/** Per-profile and per-playlist view preference; never changes Room playlist join indices. */
object LocalPlaylistSortPreferences {
    private const val FILE = "local_playlist_sort_modes"

    private fun key(profileId: String, playlistId: Long) = "$profileId:$playlistId"

    @JvmStatic
    fun get(context: Context, profileId: String, playlistId: Long): LocalPlaylistSortMode {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(key(profileId, playlistId), null)
        return LocalPlaylistSortMode.fromName(saved)
    }

    @JvmStatic
    fun set(context: Context, profileId: String, playlistId: Long, mode: LocalPlaylistSortMode) {
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (mode == LocalPlaylistSortMode.PLAYLIST_ORDER) {
            editor.remove(key(profileId, playlistId))
        } else {
            editor.putString(key(profileId, playlistId), mode.name)
        }
        editor.apply()
    }

    /** Remove every saved playlist sort when its owning profile is deleted or synchronized away. */
    @JvmStatic
    fun clearProfile(context: Context, profileId: String) {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("$profileId:") }.forEach(editor::remove)
        editor.apply()
    }

    @JvmStatic
    fun clear(context: Context, profileId: String, playlistId: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().remove(key(profileId, playlistId)).apply()
    }
}
