/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

/**
 * iOS implementation of [DownloadActions].
 *
 */
@Singleton(binds = [DownloadActions::class])
class IOSDownloadActions : DownloadActions {
    override fun pickDirectory(onPicked: (String) -> Unit) {
        Logger.w(messageString = "pickDirectory not yet implemented on iOS")
    }
}