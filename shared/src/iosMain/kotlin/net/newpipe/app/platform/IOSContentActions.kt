/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [ContentActions::class])
class IOSContentActions : ContentActions {
    override fun openMainPageTabsChooser() =
        log("openMainPageTabsChooser")
    override fun openPeertubeInstanceList() =
        log("openPeertubeInstanceList")
    override fun onAppLanguageChanged() = log("onAppLanguageChanged")
    private fun log(name: String) =
        Logger.i(messageString = "$name not implemented on iOS")
}