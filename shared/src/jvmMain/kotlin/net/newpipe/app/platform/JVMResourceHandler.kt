/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

/**
 * Handles reading of resources on JVM
 */
@Singleton(binds = [ResourceHandler::class])
class JVMResourceHandler : ResourceHandler {

    override fun readResourceToString(path: String): String {
        return Thread.currentThread().contextClassLoader.getResourceAsStream(path)!!.use { stream ->
            stream.bufferedReader().readText()
        }
    }
}
