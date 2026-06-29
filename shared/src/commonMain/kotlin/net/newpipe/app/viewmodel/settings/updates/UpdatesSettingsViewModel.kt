/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import net.newpipe.app.platform.UpdateActions
import net.newpipe.app.viewmodel.settings.BooleanPreference
import org.koin.core.annotation.KoinViewModel

private const val UPDATE_APP_KEY = "update_app_key"

@KoinViewModel
class UpdatesSettingsViewModel(
    settings: ObservableSettings,
    private val updateActions: UpdateActions
) : ViewModel() {

    private val updateAppPref = BooleanPreference(
        UPDATE_APP_KEY, false, settings, viewModelScope
    )

    val updateApp = updateAppPref.state

    fun toggleUpdateApp(newValue: Boolean) {
        updateAppPref.toggle(newValue)
        if (newValue) {
            updateActions.runManualCheck()
        }
    }

    fun runManualCheck() = updateActions.runManualCheck()
}