/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.UpdateActions
import net.newpipe.app.preferences.UpdatePreferences.DEFAULT_UPDATE_APP
import net.newpipe.app.preferences.UpdatePreferences.KEY_UPDATE_APP
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class UpdateSettingsViewModel(
    private val settings: Settings,
    private val updateActions: UpdateActions
) : ViewModel() {

    val updateChecksEnabled: StateFlow<Boolean>
        field = MutableStateFlow(settings.getBoolean(KEY_UPDATE_APP, DEFAULT_UPDATE_APP))

    fun setUpdateChecksEnabled(value: Boolean) {
        settings.putBoolean(KEY_UPDATE_APP, value)
        updateChecksEnabled.value = value
        if (value) {
            updateActions.checkForUpdates()
        }
    }
}
