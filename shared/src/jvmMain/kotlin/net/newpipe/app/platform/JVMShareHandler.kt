/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import java.awt.Desktop
import java.net.URI
import org.koin.core.annotation.Singleton

/**
 * Handles sharing of data and information on JVM
 */
@Singleton(binds = [ShareHandler::class])
class JVMShareHandler : ShareHandler {

    override fun openUrlInBrowser(url: String) {
        when {
            Desktop.isDesktopSupported() -> Desktop.getDesktop().browse(URI(url))
            else -> Logger.e(messageString = "Unsupported platform! Cannot open URL in browser")
        }
    }
}
