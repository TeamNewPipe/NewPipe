/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings.lookfeel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.LookFeelActions
import net.newpipe.app.viewmodel.settings.BooleanPreference
import net.newpipe.app.viewmodel.settings.StringPreference
import org.koin.core.annotation.KoinViewModel

// Keys mirror app/src/main/res/values/settings_keys.xml verbatim so the Compose
// screen and the legacy fragment share SharedPreferences.
private const val THEME_KEY = "theme"
private const val NIGHT_THEME_KEY = "night_theme"
private const val SHOW_HOLD_TO_APPEND_KEY = "show_hold_to_append"
private const val TABLET_MODE_KEY = "tablet_mode"
private const val LIST_VIEW_MODE_KEY = "list_view_mode"
private const val MAIN_TABS_POSITION_KEY = "main_tabs_position"

// Theme values
internal const val THEME_LIGHT = "light_theme"
internal const val THEME_DARK = "dark_theme"
internal const val THEME_BLACK = "black_theme"
internal const val THEME_AUTO = "auto_device_theme"

// Tablet mode values
internal const val TABLET_AUTO = "auto"
internal const val TABLET_ON = "on"
internal const val TABLET_OFF = "off"

// List view mode values
internal const val LIST_VIEW_AUTO = "auto"
internal const val LIST_VIEW_LIST = "list"
internal const val LIST_VIEW_GRID = "grid"
internal const val LIST_VIEW_CARD = "card"

@KoinViewModel
class LookFeelSettingsViewModel(
    settings: ObservableSettings,
    private val lookFeelActions: LookFeelActions
) : ViewModel() {

    private val themePref = StringPreference(THEME_KEY, THEME_AUTO,
        settings, viewModelScope)
    private val nightThemePref = StringPreference(
        NIGHT_THEME_KEY, THEME_DARK, settings, viewModelScope
    )
    private val showHoldToAppendPref = BooleanPreference(
        SHOW_HOLD_TO_APPEND_KEY, true, settings, viewModelScope
    )
    private val tabletModePref = StringPreference(
        TABLET_MODE_KEY, TABLET_AUTO, settings, viewModelScope
    )
    private val listViewModePref = StringPreference(
        LIST_VIEW_MODE_KEY, LIST_VIEW_AUTO, settings, viewModelScope
    )
    private val mainTabsPositionPref = BooleanPreference(
        MAIN_TABS_POSITION_KEY, false, settings, viewModelScope
    )

    val theme: StateFlow<String> = themePref.state
    val nightTheme: StateFlow<String> = nightThemePref.state
    val showHoldToAppend: StateFlow<Boolean> =
        showHoldToAppendPref.state
    val tabletMode: StateFlow<String> = tabletModePref.state
    val listViewMode: StateFlow<String> = listViewModePref.state
    val mainTabsPosition: StateFlow<Boolean> =
        mainTabsPositionPref.state

    fun setTheme(newValue: String) {
        themePref.set(newValue)
        if (newValue == THEME_AUTO) {
            lookFeelActions.showSelectNightThemeToast()
        }
        lookFeelActions.applyTheme(newValue)
    }

    fun setNightTheme(newValue: String) {
        nightThemePref.set(newValue)
        lookFeelActions.applyNightTheme(newValue)
    }

    fun toggleShowHoldToAppend(value: Boolean) =
        showHoldToAppendPref.toggle(value)
    fun setTabletMode(value: String) = tabletModePref.set(value)
    fun setListViewMode(value: String) = listViewModePref.set(value)
    fun toggleMainTabsPosition(value: Boolean) =
        mainTabsPositionPref.toggle(value)

    fun openCaptionSettings() = lookFeelActions.openCaptionSettings()
}