/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.annotation.Singleton

/**
 * Settings for Android based on SharedPreferences
 */
@Singleton
fun provideSettings(context: Context): Settings = SharedPreferencesSettings(
    PreferenceManager.getDefaultSharedPreferences(context)
)
