/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.annotation.Singleton
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * Handles working with resources on iOS
 */
@OptIn(ExperimentalForeignApi::class)
@Singleton(binds = [ResourceHandler::class])
class IOSResourceHandler : ResourceHandler {

    override fun readResourceToString(path: String): String {
        val fileParts = path.substringBeforeLast('.')
        val fileExtension = path.substringAfterLast('.', "")
        val filePath = NSBundle.mainBundle.pathForResource(fileParts, fileExtension)!!
        return NSString.stringWithContentsOfFile(filePath, NSUTF8StringEncoding, null)!!
    }
}
