/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.preferences.AppearancePreferences
import org.koin.core.annotation.KoinViewModel


@KoinViewModel
class AppearanceSettingsViewModel(private val settings: Settings) : ViewModel() {

    val theme: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(AppearancePreferences.KEY_THEME, AppearancePreferences.DEFAULT_THEME)
        )

    val nightTheme: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                AppearancePreferences.KEY_NIGHT_THEME,
                AppearancePreferences.DEFAULT_NIGHT_THEME
            )
        )

    val holdToAppend: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                AppearancePreferences.KEY_HOLD_TO_APPEND,
                AppearancePreferences.DEFAULT_HOLD_TO_APPEND
            )
        )

    val tabletMode: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                AppearancePreferences.KEY_TABLET_MODE,
                AppearancePreferences.DEFAULT_TABLET_MODE
            )
        )

    val listViewMode: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                AppearancePreferences.KEY_LIST_VIEW_MODE,
                AppearancePreferences.DEFAULT_LIST_VIEW_MODE
            )
        )

    val mainTabsPosition: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                AppearancePreferences.KEY_MAIN_TABS_POSITION,
                AppearancePreferences.DEFAULT_MAIN_TABS_POSITION
            )
        )

    fun setTheme(value: String) {
        settings.putString(AppearancePreferences.KEY_THEME, value)
        theme.value = value
    }

    fun setNightTheme(value: String) {
        settings.putString(AppearancePreferences.KEY_NIGHT_THEME, value)
        nightTheme.value = value
    }

    fun setHoldToAppend(value: Boolean) {
        settings.putBoolean(AppearancePreferences.KEY_HOLD_TO_APPEND, value)
        holdToAppend.value = value
    }

    fun setTabletMode(value: String) {
        settings.putString(AppearancePreferences.KEY_TABLET_MODE, value)
        tabletMode.value = value
    }

    fun setListViewMode(value: String) {
        settings.putString(AppearancePreferences.KEY_LIST_VIEW_MODE, value)
        listViewMode.value = value
    }

    fun setMainTabsPosition(value: Boolean) {
        settings.putBoolean(AppearancePreferences.KEY_MAIN_TABS_POSITION, value)
        mainTabsPosition.value = value
    }
}
