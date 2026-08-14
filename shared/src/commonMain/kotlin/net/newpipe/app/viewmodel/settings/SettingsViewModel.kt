/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.BuildInfo
import net.newpipe.app.screen.settings.model.SettingsCategoryType
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SettingsViewModel(buildInfo: BuildInfo) : ViewModel() {

    val categories: StateFlow<List<SettingsCategoryType>>
        field = MutableStateFlow(computeVisible(buildInfo))

    private fun computeVisible(buildInfo: BuildInfo): List<SettingsCategoryType> =
        SettingsCategoryType.entries.filter { type ->
            when (type) {
                SettingsCategoryType.UPDATES -> buildInfo.isReleaseApk
                SettingsCategoryType.DEBUG -> buildInfo.isDebug
                else -> true
            }
        }
}

