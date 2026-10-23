/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.extensions

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Builds a Koin module exposing [settings] as the only [Settings] instance.
 *
 * @param settings Settings to expose, empty by default
 */
fun settingsModule(settings: Settings = MapSettings()): Module = module {
    single { settings }
}
