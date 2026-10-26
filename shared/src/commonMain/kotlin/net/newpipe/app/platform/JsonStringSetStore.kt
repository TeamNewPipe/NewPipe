/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * Keeps each set as a JSON array string
 */
internal class JsonStringSetStore(
    private val settings: Settings,
    private val json: Json
) : StringSetStore {

    override fun get(key: String, defaultValue: Set<String>): Set<String> = settings.getStringOrNull(key)?.let { stored -> json.decodeFromString<Set<String>>(stored) }
        ?: defaultValue

    override fun put(key: String, value: Set<String>) = settings.putString(key, json.encodeToString(value))
}
