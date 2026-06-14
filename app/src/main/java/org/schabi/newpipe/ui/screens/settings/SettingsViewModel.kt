/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.schabi.newpipe.R

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    preferenceManager: SharedPreferences
) : ViewModel() {

    private val settingsLayoutRedesignPref =
        BooleanPreference(
            R.string.settings_layout_redesign_key,
            false,
            context.applicationContext,
            preferenceManager
        )

    val settingsLayoutRedesign = settingsLayoutRedesignPref.state

    fun toggleSettingsLayoutRedesign(newValue: Boolean) = settingsLayoutRedesignPref.toggle(newValue)
}
