/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.R
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.settings.DebugSettingsBVDLeakCanaryAPI
import org.schabi.newpipe.util.Localization
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    preferenceManager: SharedPreferences
) : AndroidViewModel(context.applicationContext as Application) {

    private val bvdLeakCanaryApi: DebugSettingsBVDLeakCanaryAPI? = runCatching {
        // Try to find the implementation of the LeakCanary API
        Class.forName(DebugSettingsBVDLeakCanaryAPI.IMPL_CLASS)
            .getDeclaredConstructor()
            .newInstance() as DebugSettingsBVDLeakCanaryAPI
    }.getOrNull()
    private val _isLeakCanaryAvailable = MutableStateFlow(bvdLeakCanaryApi != null)

    private val allowHeapDumpingPref = BooleanPreference(
        R.string.allow_heap_dumping_key,
        false,
        context.applicationContext,
        preferenceManager
    )
    private val allowDisposedExceptionsPref = BooleanPreference(
        R.string.allow_disposed_exceptions_key,
        false,
        context.applicationContext,
        preferenceManager
    )

    private val showOriginalTimeAgoPref =
        BooleanPreference(
            R.string.show_original_time_ago_key,
            false,
            context.applicationContext,
            preferenceManager
        )

    private val showCrashThePlayerPref = BooleanPreference(
        R.string.show_crash_the_player_key,
        false,
        context.applicationContext,
        preferenceManager
    )

    private val settingsLayoutRedesignPref =
        BooleanPreference(
            R.string.settings_layout_redesign_key,
            false,
            context.applicationContext,
            preferenceManager
        )

    val isLeakCanaryAvailable = _isLeakCanaryAvailable.asStateFlow()

    val allowHeapDumping = allowHeapDumpingPref.state
    val allowDisposedExceptions = allowDisposedExceptionsPref.state
    val showOriginalTimeAgo = showOriginalTimeAgoPref.state
    val showCrashThePlayer = showCrashThePlayerPref.state

    val settingsLayoutRedesign = settingsLayoutRedesignPref.state

    fun getLeakDisplayActivityIntent(): Intent? {
        return bvdLeakCanaryApi?.getNewLeakDisplayActivityIntent()
    }
    fun toggleAllowHeapDumping(newValue: Boolean) = allowHeapDumpingPref.toggle(newValue)
    fun toggleAllowDisposedExceptions(newValue: Boolean) =
        allowDisposedExceptionsPref.toggle(newValue)
    fun toggleShowOriginalTimeAgo(newValue: Boolean) = showOriginalTimeAgoPref.toggle(newValue)
    fun toggleShowCrashThePlayer(newValue: Boolean) = showCrashThePlayerPref.toggle(newValue)
    fun checkNewStreams() {
        NotificationWorker.runNow(getApplication())
    }
    fun toggleSettingsLayoutRedesign(newValue: Boolean) =
        settingsLayoutRedesignPref.toggle(newValue)
}

/**
 * Encapsulates the state and update logic for a boolean preference.
 *
 * @param keyResId The string resource ID for the preference key.
 * @param defaultValue The default value of the preference.
 * @param context The application context.
 * @param preferenceManager The [SharedPreferences] manager.
 */
private class BooleanPreference(
    @StringRes keyResId: Int,
    defaultValue: Boolean,
    context: Context,
    private val preferenceManager: SharedPreferences
) {
    private val key = Localization.compatGetString(context, keyResId)
    private val _state = MutableStateFlow(preferenceManager.getBoolean(key, defaultValue))
    val state: StateFlow<Boolean> = _state.asStateFlow()

    fun toggle(newValue: Boolean) {
        preferenceManager.edit { putBoolean(key, newValue) }
        _state.value = newValue
    }
}
