/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [LookFeelActions::class])
class IOSLookFeelActions : LookFeelActions {
    override fun applyTheme(newThemeKey: String) {
        Logger.i(messageString = "applyTheme not implemented on iOS")
    }
    override fun applyNightTheme(newNightThemeKey: String) {
        Logger.i(messageString = "applyNightTheme not implemented on iOS")
    }
    override fun showSelectNightThemeToast() {
        Logger.i(messageString = "showSelectNightThemeToast not implemented on iOS")
    }
    override fun openCaptionSettings() {
        Logger.i(messageString = "openCaptionSettings not implemented on iOS")
    }
}