/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.util.Localization

/**
 * Encapsulates the state and update logic for a boolean preference.
 *
 * Registers a [SharedPreferences.OnSharedPreferenceChangeListener] so the
 * exposed [state] stays in sync even when the preference is changed externally
 * (e.g. by another screen, a background service, or a data migration).
 *
 * @param keyResId The string resource ID for the preference key.
 * @param defaultValue The default value of the preference.
 * @param context The application context.
 * @param preferenceManager The [SharedPreferences] manager.
 */
internal class BooleanPreference(
    @StringRes keyResId: Int,
    private val defaultValue: Boolean,
    context: Context,
    private val preferenceManager: SharedPreferences
) {
    private val key = Localization.compatGetString(context, keyResId)
    private val _state = MutableStateFlow(preferenceManager.getBoolean(key, defaultValue))
    val state: StateFlow<Boolean> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
        if (changedKey == key) {
            _state.value = prefs.getBoolean(key, defaultValue)
        }
    }

    init {
        preferenceManager.registerOnSharedPreferenceChangeListener(listener)
    }

    fun toggle(newValue: Boolean) {
        preferenceManager.edit { putBoolean(key, newValue) }
    }
}
