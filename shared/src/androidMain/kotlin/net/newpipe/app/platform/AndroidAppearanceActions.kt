/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.appcompat.app.AppCompatDelegate
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import net.newpipe.app.preferences.AppearancePreferences
import org.koin.core.annotation.Singleton

@Singleton(binds = [AppearanceActions::class])
class AndroidAppearanceActions(
    private val context: Context,
    private val settings: Settings,
) : AppearanceActions {

    override fun isCaptionSettingsAvailable(): Boolean =
        Intent(AndroidSettings.ACTION_CAPTIONING_SETTINGS)
            .resolveActivity(context.packageManager) != null

    override fun openCaptionSettings() {
        try {
            context.startActivity(
                Intent(AndroidSettings.ACTION_CAPTIONING_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (exception: ActivityNotFoundException) {
            Logger.e(messageString = "No activity found for captioning settings", throwable = exception)
        }
    }

    override fun applyThemeChange(theme: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                AppearancePreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO

                AppearancePreferences.THEME_DARK,
                AppearancePreferences.THEME_BLACK -> AppCompatDelegate.MODE_NIGHT_YES

                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        settings.putBoolean(KEY_THEME_CHANGE, true)
    }

    private companion object {
        const val KEY_THEME_CHANGE = "key_theme_change"
    }
}
