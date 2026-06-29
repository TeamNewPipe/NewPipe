/*
   * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
   * SPDX-License-Identifier: GPL-3.0-or-later
   */

package net.newpipe.app.di.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import org.koin.core.annotation.Singleton
import platform.Foundation.NSUserDefaults

@Singleton(binds = [ObservableSettings::class, Settings::class])
fun provideSettings(): ObservableSettings =
    NSUserDefaultsSettings(NSUserDefaults())