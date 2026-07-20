/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

/**
 * Handles sharing of data and information on Android
 * @property context Context on Android, injected automatically by Koin
 */
@Singleton(binds = [ShareHandler::class])
class AndroidShareHandler(private val context: Context) : ShareHandler {

    override fun openUrlInBrowser(url: String) {
        try {
            CustomTabsIntent.Builder()
                .build()
                .also { customIntent ->
                    customIntent.intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
                .launchUrl(context, url.toUri())
        } catch (exception: Exception) {
            Logger.e(messageString = "Failed to share URL", throwable = exception)
        }
    }
}
