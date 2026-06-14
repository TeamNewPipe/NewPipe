/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.annotation.Singleton
import platform.Foundation.NSUserDefaults

/**
 * Settings for iOS based on UserDefaultsSettings
 */
@Singleton
fun provideSettings(): Settings = NSUserDefaultsSettings(NSUserDefaults())
