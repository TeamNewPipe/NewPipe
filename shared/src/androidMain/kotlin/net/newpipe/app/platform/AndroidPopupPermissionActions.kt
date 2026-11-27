/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [PopupPermissionActions::class])
class AndroidPopupPermissionActions(private val context: Context) : PopupPermissionActions {

    override val needsPermissionGuide: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override val appLabel: String
        get() = context.applicationInfo.loadLabel(context.packageManager).toString()

    override fun canDrawOverlays(): Boolean = AndroidSettings.canDrawOverlays(context)

    override fun openOverlayPermissionSettings() {
        val intent = if (needsPermissionGuide) {
            Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION)
        } else {
            Intent(
                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts(SCHEME_PACKAGE, context.packageName, null)
            )
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (exception: ActivityNotFoundException) {
            Logger.e(messageString = "No activity found for overlay permission settings", throwable = exception)
        }
    }

    private companion object {
        const val SCHEME_PACKAGE = "package"
    }
}
