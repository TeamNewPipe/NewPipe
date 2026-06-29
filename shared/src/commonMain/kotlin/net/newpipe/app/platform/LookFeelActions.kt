/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

/**
 * Platform side-effects triggered from the LookFeel/Appearance settings screen.
 *
 * Android: applies day/night mode and recreates the current Activity so the
 * theme change takes effect immediately. Also opens the system captioning
 * intent.
 *
 */
interface LookFeelActions {
    /**
     * Apply the new app theme (one of the theme values from
     * `app/src/main/res/values/settings_keys.xml`: light, dark, black,
     * auto_device_theme) and recreate the current Activity so the change
     * takes effect immediately.
     */
    fun applyTheme(newThemeKey: String)

    /**
     * Apply the new night theme (only the "dark" / "black" values, used when
     * the main theme is "auto_device_theme"). Same recreate behavior.
     */
    fun applyNightTheme(newNightThemeKey: String)

    /**
     * Show a short toast/snackbar with the localized message
     * "You can select your favorite night theme below". Matches the legacy
     * fragment's behavior when the user picks the auto-device theme.
     */
    fun showSelectNightThemeToast()

    /** Open the system captioning settings screen (Android only; no-op elsewhere). */
    fun openCaptionSettings()
}