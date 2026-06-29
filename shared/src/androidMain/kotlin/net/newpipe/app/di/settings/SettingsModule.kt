/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.annotation.Singleton

/**
 * Shared key-value store for Android, backed by the default
 * [PreferenceManager] so Compose Multiplatform code and the legacy Views
 * fragments share the same `SharedPreferences`.
 *
 * Registered under both [ObservableSettings] (for flow-based callers like
 * [net.newpipe.app.viewmodel.settings.BooleanPreference]) and [Settings]
 * (for the original theme-layer callers).
 */
@Singleton(binds = [ObservableSettings::class, Settings::class])
fun provideSettings(context: Context): ObservableSettings =
    SharedPreferencesSettings(
        PreferenceManager.getDefaultSharedPreferences(context)
    )