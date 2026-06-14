/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication

/**
 * Handles sharing of data and information on iOS
 */
@Singleton(binds = [ShareHandler::class])
class IOSShareHandler : ShareHandler {

    override fun openUrlInBrowser(url: String) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            Logger.e(messageString = "Unable to open malformatted URL, bailing out!")
        } else {
            val safariVC = SFSafariViewController(uRL = nsUrl)
            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootVC?.presentViewController(safariVC, animated = true, completion = null)
        }
    }
}
