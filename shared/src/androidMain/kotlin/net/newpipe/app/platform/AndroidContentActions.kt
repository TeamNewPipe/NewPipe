/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import co.touchlab.kermit.Logger
import net.newpipe.app.preferences.ContentPreferences
import org.koin.core.annotation.Singleton

@Singleton(binds = [ContentActions::class])
class AndroidContentActions(private val context: Context) : ContentActions {

    private val host: ContentHost? = loadHost(context)

    // Android 13 moved per-app languages into the system settings
    override val usesSystemAppLanguageSettings: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun systemAppLanguageName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].displayName
    }

    override fun openSystemAppLanguageSettings() {
        try {
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.fromParts(SCHEME_PACKAGE, context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (exception: ActivityNotFoundException) {
            Logger.e(messageString = "No activity found for app language settings", throwable = exception)
        }
    }

    override fun applyAppLanguage(languageCode: String) = AppCompatDelegate.setApplicationLocales(
        when (languageCode) {
            ContentPreferences.DEFAULT_LOCALIZATION -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(languageCode)
        }
    )

    override fun applyImageQuality(quality: String) {
        host?.applyImageQuality(quality)
    }

    override fun applyYoutubeRestrictedMode(enabled: Boolean) {
        host?.applyYoutubeRestrictedMode(enabled)
    }

    override fun applyContentLocalization() {
        host?.applyContentLocalization()
    }

    private fun loadHost(context: Context): ContentHost? = runCatching {
        Class.forName(HOST_CLASS)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as ContentHost
    }.onFailure { error ->
        Logger.w(messageString = "No host content actions found, skipping host-side changes", throwable = error)
    }.getOrNull()

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeContentHost"
        const val SCHEME_PACKAGE = "package"
    }
}
