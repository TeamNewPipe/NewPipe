/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.bookmark

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.learning.LearningMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaylistOpeningPreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val categories = PlaylistCategories().apply {
        rename("courses", "Courses")
        rename("music", "Music")
    }

    @Test
    fun lastUsedIsTheDefaultButAnExplicitOpeningChoiceDoesNotFollowFilterChanges() {
        assertNull(PlaylistOpeningPreferences.getOpeningCategory(context, FIRST))
        assertEquals(PlaylistCategories.ALL, open(FIRST))
        PlaylistOpeningPreferences.rememberCategory(context, FIRST, "courses")
        assertEquals("courses", open(FIRST))

        PlaylistOpeningPreferences.setOpeningCategory(context, FIRST, "music")
        PlaylistOpeningPreferences.rememberCategory(context, FIRST, "courses")
        assertEquals("music", open(FIRST))
        // Resolving a fragment that is not yet visible must not replace the last viewed category.
        assertEquals("courses", PlaylistOpeningPreferences.getLastCategory(context, FIRST))
        PlaylistOpeningPreferences.setOpeningCategory(context, FIRST, PlaylistCategories.ALL)
        PlaylistOpeningPreferences.rememberCategory(context, FIRST, "courses")
        assertEquals(PlaylistCategories.ALL, open(FIRST))
    }

    @Test
    fun validSameProfileStateWinsButAnotherProfilesStateCannotOverrideTheOpeningChoice() {
        PlaylistOpeningPreferences.setOpeningCategory(context, FIRST, "music")
        PlaylistOpeningPreferences.setOpeningCategory(context, SECOND, PlaylistCategories.UNCATEGORIZED)

        assertEquals("courses", open(FIRST, FIRST, "courses"))
        assertEquals(PlaylistCategories.UNCATEGORIZED, open(SECOND, FIRST, "courses"))
        assertEquals("music", open(FIRST, SECOND, "courses"))
        assertEquals(PlaylistCategories.UNCATEGORIZED, open(SECOND))
    }

    @Test
    fun renamingKeepsStableIdsAndDeletingRepairsBothSavedChoicesWithoutResurrectingThem() {
        PlaylistOpeningPreferences.setOpeningCategory(context, FIRST, "courses")
        PlaylistOpeningPreferences.rememberCategory(context, FIRST, "courses")
        categories.rename("courses", "Renamed courses")
        assertEquals("courses", open(FIRST))

        categories.delete("courses")
        assertEquals(PlaylistCategories.ALL, open(FIRST, FIRST, "courses"))
        assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getOpeningCategory(context, FIRST))
        assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getLastCategory(context, FIRST))
        categories.rename("courses", "Imported category with the old ID")
        assertEquals(PlaylistCategories.ALL, open(FIRST))
    }

    @Test
    fun learningIsValidOnlyWhenEnabledForTheCapturedProfileAndSurvivesStateRestoration() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().putBoolean(context.getString(R.string.learning_mode_key), true)
            .putBoolean(LearningMode.profilePreferenceKey(FIRST), true)
            .putBoolean(LearningMode.profilePreferenceKey(SECOND), false).commit()
        val learning = PlaylistOpeningPreferences.LEARNING_CATEGORY
        PlaylistOpeningPreferences.setOpeningCategory(context, FIRST, learning)
        PlaylistOpeningPreferences.setOpeningCategory(context, SECOND, learning)

        assertEquals(learning, open(FIRST))
        assertEquals(learning, open(FIRST, FIRST, learning))
        assertEquals(PlaylistCategories.ALL, open(SECOND))
        preferences.edit().putBoolean(LearningMode.profilePreferenceKey(FIRST), false).commit()
        assertEquals(PlaylistCategories.ALL, open(FIRST, FIRST, learning))
        assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getOpeningCategory(context, FIRST))
    }

    @Test
    fun changingCategoryMembershipDoesNotChangeOpeningOrRememberedFilters() {
        PlaylistOpeningPreferences.setOpeningCategory(context, FIRST, "music")
        PlaylistOpeningPreferences.rememberCategory(context, FIRST, "courses")
        categories.assign("local:7", "music")

        assertEquals("courses", PlaylistOpeningPreferences.validateSelection(context, FIRST, categories, "courses"))
        assertEquals("music", PlaylistOpeningPreferences.getOpeningCategory(context, FIRST))
        assertEquals("courses", PlaylistOpeningPreferences.getLastCategory(context, FIRST))
    }

    @Test
    fun profileCleanupRemovesOnlyItsOwnOpeningAndRememberedFilters() {
        for (profile in listOf(FIRST, SECOND)) {
            PlaylistOpeningPreferences.setOpeningCategory(context, profile, "music")
            PlaylistOpeningPreferences.rememberCategory(context, profile, "courses")
        }
        PlaylistOpeningPreferences.clearProfile(context, FIRST)

        assertNull(PlaylistOpeningPreferences.getOpeningCategory(context, FIRST))
        assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getLastCategory(context, FIRST))
        assertEquals("music", PlaylistOpeningPreferences.getOpeningCategory(context, SECOND))
        assertEquals("courses", PlaylistOpeningPreferences.getLastCategory(context, SECOND))
    }

    private fun open(profile: String, restoredProfile: String? = null, restoredCategory: String? = null) = PlaylistOpeningPreferences.resolveInitialCategory(context, profile, categories, restoredProfile, restoredCategory)

    companion object {
        private const val FIRST = "00000000-0000-0000-0000-000000000001"
        private const val SECOND = "00000000-0000-0000-0000-000000000002"
    }
}
