/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalPlaylistSortPreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun sortingChoiceIsScopedToPlaylistAndProfileAndCanBeCleared() {
        val first = "00000000-0000-0000-0000-000000000001"
        val second = "00000000-0000-0000-0000-000000000002"
        LocalPlaylistSortPreferences.set(context, first, 3L, LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER)

        assertEquals(
            LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER,
            LocalPlaylistSortPreferences.get(context, first, 3L)
        )
        assertEquals(LocalPlaylistSortMode.PLAYLIST_ORDER, LocalPlaylistSortPreferences.get(context, first, 4L))
        assertEquals(LocalPlaylistSortMode.PLAYLIST_ORDER, LocalPlaylistSortPreferences.get(context, second, 3L))

        LocalPlaylistSortPreferences.clear(context, first, 3L)
        assertEquals(LocalPlaylistSortMode.PLAYLIST_ORDER, LocalPlaylistSortPreferences.get(context, first, 3L))
    }

    @Test
    fun deletedProfileDoesNotRetainAnyPlaylistSortPreferences() {
        val removedProfile = "00000000-0000-0000-0000-000000000011"
        val survivingProfile = "00000000-0000-0000-0000-000000000012"
        LocalPlaylistSortPreferences.set(context, removedProfile, 3L, LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER)
        LocalPlaylistSortPreferences.set(context, removedProfile, 4L, LocalPlaylistSortMode.NEWEST_FIRST)
        LocalPlaylistSortPreferences.set(context, survivingProfile, 3L, LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER)

        LocalPlaylistSortPreferences.clearProfile(context, removedProfile)

        assertEquals(LocalPlaylistSortMode.PLAYLIST_ORDER, LocalPlaylistSortPreferences.get(context, removedProfile, 3L))
        assertEquals(LocalPlaylistSortMode.PLAYLIST_ORDER, LocalPlaylistSortPreferences.get(context, removedProfile, 4L))
        assertEquals(
            LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER,
            LocalPlaylistSortPreferences.get(context, survivingProfile, 3L)
        )
    }
}
