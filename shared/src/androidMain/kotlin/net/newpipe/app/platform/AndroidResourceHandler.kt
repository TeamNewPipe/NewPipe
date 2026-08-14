/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import org.koin.core.annotation.Singleton

/**
 * Handles working with resources on Android
 * @property context Context on Android, injected automatically by Koin
 */
@Singleton(binds = [ResourceHandler::class])
class AndroidResourceHandler(private val context: Context) : ResourceHandler {

    override fun readResourceToString(path: String): String {
        return context.assets.open(path).use { inputStream ->
            inputStream.bufferedReader().readText()
        }
    }
}
