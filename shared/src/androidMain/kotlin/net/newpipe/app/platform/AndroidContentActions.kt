/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [ContentActions::class])
class AndroidContentActions(
    private val legacyHooks: AndroidLegacyHooks
) : ContentActions {
    override fun openMainPageTabsChooser() = legacyHooks.openMainPageTabsChooser()
    override fun openPeertubeInstanceList() = legacyHooks.openPeertubeInstanceList()
    override fun onAppLanguageChanged() = legacyHooks.onAppLanguageChanged()
}