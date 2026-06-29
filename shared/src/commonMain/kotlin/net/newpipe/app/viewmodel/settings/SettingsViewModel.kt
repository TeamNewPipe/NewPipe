/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import org.koin.core.annotation.KoinViewModel

private const val SETTINGS_LAYOUT_REDESIGN_KEY = "settings_layout_redesign_key"

@KoinViewModel
class SettingsViewModel(
    settings: ObservableSettings
) : ViewModel() {

    private val settingsLayoutRedesignPref =
        BooleanPreference(
            key = SETTINGS_LAYOUT_REDESIGN_KEY,
            defaultValue = false,
            settings = settings,
            scope = viewModelScope
        )

    val settingsLayoutRedesign = settingsLayoutRedesignPref.state

    fun toggleSettingsLayoutRedesign(newValue: Boolean) =
        settingsLayoutRedesignPref.toggle(newValue)
}
