/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import co.touchlab.kermit.Logger

internal inline fun <reified T : Any> loadHost(context: Context, className: String): T? = runCatching {
    Class.forName(className)
        .getDeclaredConstructor(Context::class.java)
        .newInstance(context) as T
}.onFailure { error ->
    Logger.w(messageString = "No host ${T::class.simpleName} found, skipping host-side changes", throwable = error)
}.getOrNull()
