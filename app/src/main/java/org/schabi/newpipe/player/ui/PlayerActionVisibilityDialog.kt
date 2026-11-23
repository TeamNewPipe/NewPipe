/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogPlayerActionVisibilityTitleBinding

object PlayerActionVisibilityDialog {
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        fragment.findPreference<Preference>(PlayerActionVisibility.preferenceKey(fragment.requireContext()))
            ?.setOnPreferenceClickListener {
                show(fragment.requireContext())
                true
            }
    }

    fun show(context: Context): AlertDialog {
        val hidden = PlayerActionVisibility.hidden(context).toMutableSet()
        val actions = PlayerActionVisibility.actions
        val builder = MaterialAlertDialogBuilder(context)
        val title = DialogPlayerActionVisibilityTitleBinding.inflate(LayoutInflater.from(builder.context))
        ViewCompat.setAccessibilityHeading(title.visiblePlayerActionsTitle, true)
        val dialog = builder
            .setCustomTitle(title.root)
            .setMultiChoiceItems(
                actions.map { context.getString(it.title) }.toTypedArray(),
                actions.map { it.key !in hidden }.toBooleanArray()
            ) { _, index, checked ->
                val key = actions[index].key
                if (checked) hidden.remove(key) else hidden.add(key)
            }
            .setPositiveButton(R.string.save) { _, _ -> PlayerActionVisibility.save(context, hidden) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setTitle(R.string.visible_player_actions_title)
        title.visiblePlayerActionsReset.setOnClickListener {
            PlayerActionVisibility.save(context, emptySet())
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }
}
