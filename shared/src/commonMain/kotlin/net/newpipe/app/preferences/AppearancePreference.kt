/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.preferences

/**
 * Preference keys, values, and defaults for the Appearance settings screen.
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