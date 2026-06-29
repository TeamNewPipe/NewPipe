/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

import com.russhwolf.settings.ExperimentalSettingsApi

/**
 * Stores a `Set<String>` as a comma-delimited [String] under [key].
 *
 * Used by multi-select preferences. NOTE: this does **not** share format with
 * Android's `MultiSelectListPreference` (which uses `getStringSet`). Until the
 * legacy XML screens are retired, multi-select prefs migrated to Compose will
 * appear "reset" to default when viewed in the legacy UI and vice-versa.
 *
 * @param defaultValue values applied when the key is missing.
 */
@OptIn(ExperimentalSettingsApi::class)
internal class StringSetPreference(
    private val key: String,
    private val defaultValue: Set<String>,
    private val settings: ObservableSettings,
    scope: CoroutineScope
) {
    val state: StateFlow<Set<String>> = settings
        .getStringFlow(key, defaultValue.joinToString(","))
        .map { it.split(",").filter { piece -> piece.isNotEmpty()
        }.toSet() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Companion.Eagerly,
            initialValue = decode(settings.getString(key,
                defaultValue.joinToString(",")))
        )

    fun set(newValue: Set<String>) {
        settings.putString(key, newValue.joinToString(","))
    }

    private fun decode(raw: String): Set<String> =
        raw.split(",").filter { it.isNotEmpty() }.toSet()
}