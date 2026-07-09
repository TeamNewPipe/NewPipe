/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [AppearanceActions::class])
class AndroidAppearanceActions(private val context: Context) : AppearanceActions {

    override fun openCaptionSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_CAPTIONING_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (exception: ActivityNotFoundException) {
            Logger.e(messageString = "No activity found for captioning settings", throwable = exception)
        }
    }
}
