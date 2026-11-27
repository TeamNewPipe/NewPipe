/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import net.newpipe.app.screen.settings.model.PanicApp
import org.koin.core.annotation.Singleton

@Singleton(binds = [PanicActions::class])
class AndroidPanicActions(val context: Context) : PanicActions {
    val pm: PackageManager? = context.packageManager

    override fun getPanicApp(packageName: String?): PanicApp? {
        if (packageName == null) return null
        return try {
            pm?.getPackageInfo(packageName, 0)?.applicationInfo?.toPanicApp()
        } catch (e: Exception) {
            Log.e("AndroidPanicActions", "Failed to get package info for $packageName", e)
            null
        }
    }

    private fun ApplicationInfo.toPanicApp() = PanicApp(
        packageName = packageName,
        name = loadLabel(pm!!).toString(),
        signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, 0).signingInfo?.apkContentsSigners
                    ?.map { it.toCharsString() } ?: emptyList()
            } else {
                pm.getPackageInfo(packageName, 0).signatures
                    ?.map { it.toCharsString() } ?: emptyList()
            }
    )
}
