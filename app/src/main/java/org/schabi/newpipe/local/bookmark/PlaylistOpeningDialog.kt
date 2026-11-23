/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.bookmark

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.function.IntConsumer
import org.schabi.newpipe.R
import org.schabi.newpipe.learning.LearningMode

object PlaylistOpeningDialog {
    /** Put the opening action in the scrollable list instead of crowding the dialog button bar. */
    @JvmStatic
    fun categoryMenu(
        context: Context,
        profileId: String,
        categories: PlaylistCategories,
        labels: List<String>,
        includeOpeningAction: Boolean,
        onCategory: IntConsumer
    ): MaterialAlertDialogBuilder {
        val menuLabels = if (includeOpeningAction) {
            listOf(context.getString(R.string.playlist_opening_title)) + labels
        } else {
            labels
        }
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.playlist_categories)
            .setItems(menuLabels.toTypedArray()) { _, which ->
                if (includeOpeningAction && which == 0) {
                    show(context, profileId, categories)
                } else {
                    onCategory.accept(which - if (includeOpeningAction) 1 else 0)
                }
            }
    }

    @JvmStatic
    fun show(context: Context, profileId: String, categories: PlaylistCategories) {
        val ids = mutableListOf<String?>(null, PlaylistCategories.ALL)
        val labels = mutableListOf(
            context.getString(R.string.playlist_opening_last_used),
            context.getString(R.string.playlist_categories_all)
        )
        if (LearningMode.isEnabled(context, profileId)) {
            ids.add(PlaylistOpeningPreferences.LEARNING_CATEGORY)
            labels.add(context.getString(R.string.learning_for_learning))
        }
        ids.add(PlaylistCategories.UNCATEGORIZED)
        labels.add(context.getString(R.string.playlist_uncategorized))
        categories.ids().forEach { id ->
            ids.add(id)
            labels.add(categories.name(id))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.playlist_opening_title)
            .setSingleChoiceItems(
                labels.toTypedArray(),
                ids.indexOf(PlaylistOpeningPreferences.getOpeningCategory(context, profileId))
            ) { dialog, which ->
                PlaylistOpeningPreferences.setOpeningCategory(context, profileId, ids[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
