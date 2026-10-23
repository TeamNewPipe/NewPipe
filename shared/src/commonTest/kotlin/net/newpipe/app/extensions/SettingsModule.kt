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
 * Every test goes through this single `single<Settings>` declaration on purpose. The Koin compiler
 * plugin keeps the metadata of a `single` binding in the =
 * names of the synthetic parameters it
 * generates, and parameter names are not part of an IR signature. Declaring `single<Settings>` in
 * more than one place therefore produces colliding signatures that fail klib serialization on the
 * native targets.
 *
 * @param settings Settings to expose, empty by default
 */
fun settingsModule(settings: Settings = MapSettings()): Module = module {
    single { settings }
}
