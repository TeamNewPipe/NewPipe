/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel

/**
 * Preference keys and values for the Appearance screen.
 */
object AppearancePreferences {
    const val KEY_THEME = "theme"
    const val KEY_NIGHT_THEME = "night_theme"
    const val KEY_HOLD_TO_APPEND = "show_hold_to_append"
    const val KEY_TABLET_MODE = "tablet_mode"
    const val KEY_LIST_VIEW_MODE = "list_view_mode"
    const val KEY_MAIN_TABS_POSITION = "main_tabs_position"

    const val THEME_LIGHT = "light_theme"
    const val THEME_DARK = "dark_theme"
    const val THEME_BLACK = "black_theme"
    const val THEME_AUTO_DEVICE = "auto_device_theme"

    const val TABLET_MODE_AUTO = "auto"
    const val TABLET_MODE_ON = "on"
    const val TABLET_MODE_OFF = "off"

    const val LIST_VIEW_AUTO = "auto"
    const val LIST_VIEW_LIST = "list"
    const val LIST_VIEW_GRID = "grid"
    const val LIST_VIEW_CARD = "card"

    const val DEFAULT_THEME = THEME_AUTO_DEVICE
    const val DEFAULT_NIGHT_THEME = THEME_DARK
    const val DEFAULT_TABLET_MODE = TABLET_MODE_AUTO
    const val DEFAULT_LIST_VIEW_MODE = LIST_VIEW_AUTO
    const val DEFAULT_HOLD_TO_APPEND = true
    const val DEFAULT_MAIN_TABS_POSITION = false
}

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
