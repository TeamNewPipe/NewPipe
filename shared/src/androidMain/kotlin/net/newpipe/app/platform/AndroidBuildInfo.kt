/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [BuildInfo::class])
class AndroidBuildInfo(private val context: Context) : BuildInfo {

    override val isDebug: Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @get:SuppressLint("NewApi")
    @OptIn(ExperimentalStdlibApi::class)
    override val isReleaseApk: Boolean by lazy {
        val certificates = mapOf(
            RELEASE_CERT_PUBLIC_KEY_SHA256.hexToByteArray() to PackageManager.CERT_INPUT_SHA256
        )
        try {
            PackageInfoCompat.hasSignatures(
                context.packageManager,
                context.packageName,
                certificates,
                false
            )
        } catch (exception: PackageManager.NameNotFoundException) {
            Logger.e(messageString = "Could not find package info", throwable = exception)
            false
        }
    }

    companion object {
        private const val RELEASE_CERT_PUBLIC_KEY_SHA256 =
            "cb84069bd68116bafae5ee4ee5b08a567aa6d898404e7cb12f9e756df5cf5cab"
    }
}
