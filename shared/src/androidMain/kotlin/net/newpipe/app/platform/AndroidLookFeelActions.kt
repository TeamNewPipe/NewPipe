/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [LookFeelActions::class])
class AndroidLookFeelActions(
    private val legacyHooks: AndroidLegacyHooks
) : LookFeelActions {
    override fun applyTheme(newThemeKey: String) =
        legacyHooks.applyTheme(newThemeKey)
    override fun applyNightTheme(newNightThemeKey: String) =
        legacyHooks.applyNightTheme(newNightThemeKey)
    override fun showSelectNightThemeToast() =
        legacyHooks.showSelectNightThemeToast()
    override fun openCaptionSettings() =
        legacyHooks.openCaptionSettings()
}