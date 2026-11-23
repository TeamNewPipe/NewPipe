/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.SharedPreferences
import android.view.View
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** Retains the owner's requested state, so un-hiding never enables an unavailable action. */
internal class PlayerActionVisibilityDelegate(
    private val view: View,
    private val applyVisibility: (Int) -> Unit
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
    private var requestedVisibility = view.visibility
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == PlayerActionVisibility.preferenceKey(view.context)) refresh()
    }

    fun setVisibility(visibility: Int) {
        requestedVisibility = visibility
        refresh()
    }

    fun attach() {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    fun detach() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun refresh() {
        val visibility = PlayerActionVisibility.visibility(
            requestedVisibility,
            PlayerActionVisibility.actionForView(view.id)?.key,
            PlayerActionVisibility.hidden(view.context)
        )
        if (visibility != View.VISIBLE && view.hasFocus()) {
            view.clearFocus()
            view.rootView.findViewById<View>(R.id.playPauseButton)
                ?.takeIf { it.isShown && it.isFocusable }?.requestFocus()
        }
        applyVisibility(visibility)
    }
}
