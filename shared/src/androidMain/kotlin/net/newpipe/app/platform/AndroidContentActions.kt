/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [ContentActions::class])
class AndroidContentActions(
    private val context: Context,
) : ContentActions {

    override fun openAppLanguageSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // The legacy in-app language picker is not migrated yet; the legacy
            // app screen still handles pre-Android-13 devices.
            Logger.w(messageString = "App language settings only available on Android 13+")
            return
        }
        try {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (exception: ActivityNotFoundException) {
            Logger.e(messageString = "No activity found for app locale settings", throwable = exception)
        }
    }

    override fun wipeImageCaches() {
        // TODO: Wire to the app module's Coil image loader once the shared module
        //  exposes a cache-wiping hook. See ContentSettingsFragment.setupImageQualityPref().
        Logger.w(messageString = "wipeImageCaches() not wired yet")
    }

    override fun updateYoutubeRestrictedModeCookies() {
        // TODO: Wire to the app module's DownloaderImpl once a hook is available.
        //  See ContentSettingsFragment.onPreferenceTreeClick().
        Logger.w(messageString = "updateYoutubeRestrictedModeCookies() not wired yet")
    }

    override fun appLanguageLabel(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        val locale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().getOrNull(0)
        return locale?.displayName
    }
}