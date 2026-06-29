/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

/**
 * Platform side-effects triggered from the Content settings screen.
 *
 * Android: opens the legacy ChooseTabsFragment / PeertubeInstanceListFragment
 * via [SettingsActivity], and restarts the app when the app-language changes
 * (so locale takes effect).
 *
 */
interface ContentActions {
    fun openMainPageTabsChooser()
    fun openPeertubeInstanceList()
    fun onAppLanguageChanged()
}