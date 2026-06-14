/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens.settings.debug

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.R
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.settings.DebugSettingsBVDLeakCanaryAPI
import org.schabi.newpipe.ui.screens.settings.BooleanPreference

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    preferenceManager: SharedPreferences
) : ViewModel() {

    private val application = context.applicationContext as Application

    private val bvdLeakCanaryApi: DebugSettingsBVDLeakCanaryAPI? = runCatching {
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
    private val showOriginalTimeAgoPref = BooleanPreference(
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

    val isLeakCanaryAvailable = _isLeakCanaryAvailable.asStateFlow()
    val allowHeapDumping = allowHeapDumpingPref.state
    val allowDisposedExceptions = allowDisposedExceptionsPref.state
    val showOriginalTimeAgo = showOriginalTimeAgoPref.state
    val showCrashThePlayer = showCrashThePlayerPref.state

    fun getLeakDisplayActivityIntent(): Intent? {
        return bvdLeakCanaryApi?.getNewLeakDisplayActivityIntent()
    }

    fun toggleAllowHeapDumping(newValue: Boolean) = allowHeapDumpingPref.toggle(newValue)
    fun toggleAllowDisposedExceptions(newValue: Boolean) = allowDisposedExceptionsPref.toggle(newValue)
    fun toggleShowOriginalTimeAgo(newValue: Boolean) = showOriginalTimeAgoPref.toggle(newValue)
    fun toggleShowCrashThePlayer(newValue: Boolean) = showCrashThePlayerPref.toggle(newValue)

    fun checkNewStreams() {
        NotificationWorker.runNow(application)
    }
}
