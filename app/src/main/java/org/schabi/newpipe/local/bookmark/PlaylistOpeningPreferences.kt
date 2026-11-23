/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.bookmark

import android.content.Context
import org.schabi.newpipe.R
import org.schabi.newpipe.learning.LearningMode

/** Device-local view choices, separate from transferable category names and memberships. */
object PlaylistOpeningPreferences {
    const val LEARNING_CATEGORY = "builtin:learning"
    private const val FILE = "playlist_opening_categories"
    private const val OPENING = "opening"
    private const val LAST = "last"

    private fun preferences(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun key(profileId: String, kind: String) = "$profileId:$kind"

    /** A null opening category means Last used; the empty category ID means Uncategorized. */
    fun getOpeningCategory(context: Context, profileId: String): String? = preferences(context).getString(key(profileId, OPENING), null)

    fun setOpeningCategory(context: Context, profileId: String, categoryId: String?) {
        preferences(context).edit().putString(key(profileId, OPENING), categoryId).apply()
    }

    fun getLastCategory(context: Context, profileId: String): String = preferences(context).getString(key(profileId, LAST), PlaylistCategories.ALL) ?: PlaylistCategories.ALL

    @JvmStatic
    fun rememberCategory(context: Context, profileId: String, categoryId: String) {
        preferences(context).edit().putString(key(profileId, LAST), categoryId).apply()
    }

    /** Restore only the owning profile's screen state. A new screen uses its opening choice. */
    @JvmStatic
    fun resolveInitialCategory(
        context: Context,
        profileId: String,
        categories: PlaylistCategories,
        restoredProfileId: String?,
        restoredCategory: String?
    ): String {
        repairUnavailableCategories(context, profileId, categories)
        val candidate = if (restoredProfileId == profileId && restoredCategory != null) {
            restoredCategory
        } else {
            getOpeningCategory(context, profileId) ?: getLastCategory(context, profileId)
        }
        return availableOrAll(context, profileId, categories, candidate)
    }

    /** Category deletion, replacement imports and Learning availability can invalidate IDs. */
    @JvmStatic
    fun validateSelection(
        context: Context,
        profileId: String,
        categories: PlaylistCategories,
        selectedCategory: String?
    ): String {
        repairUnavailableCategories(context, profileId, categories)
        return availableOrAll(context, profileId, categories, selectedCategory)
    }

    @JvmStatic
    fun affectsCategories(context: Context, profileId: String, changedKey: String?): Boolean = changedKey == null || changedKey == PlaylistCategories.preferenceKey(profileId) ||
        changedKey == context.getString(R.string.learning_mode_key) ||
        changedKey == LearningMode.profilePreferenceKey(profileId)

    @JvmStatic
    fun clearProfile(context: Context, profileId: String) {
        preferences(context).edit().remove(key(profileId, OPENING)).remove(key(profileId, LAST)).apply()
    }

    private fun repairUnavailableCategories(context: Context, profileId: String, categories: PlaylistCategories) {
        val preferences = preferences(context)
        val editor = preferences.edit()
        var changed = false
        for (kind in listOf(OPENING, LAST)) {
            val key = key(profileId, kind)
            val saved = preferences.getString(key, null) ?: continue
            if (availableOrAll(context, profileId, categories, saved) != saved) {
                editor.putString(key, PlaylistCategories.ALL)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    private fun availableOrAll(context: Context, profileId: String, categories: PlaylistCategories, categoryId: String?): String = when (categoryId) {
        PlaylistCategories.ALL, PlaylistCategories.UNCATEGORIZED -> categoryId
        LEARNING_CATEGORY -> if (LearningMode.isEnabled(context, profileId)) categoryId else PlaylistCategories.ALL
        null -> PlaylistCategories.ALL
        else -> if (categories.name(categoryId) != null) categoryId else PlaylistCategories.ALL
    }
}
