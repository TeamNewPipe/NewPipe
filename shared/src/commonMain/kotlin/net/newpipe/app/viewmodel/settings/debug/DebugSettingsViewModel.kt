/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import net.newpipe.app.platform.DebugActions
import net.newpipe.app.viewmodel.settings.BooleanPreference
import org.koin.core.annotation.KoinViewModel

private const val ALLOW_HEAP_DUMPING_KEY = "allow_heap_dumping_key"
private const val ALLOW_DISPOSED_EXCEPTIONS_KEY = "allow_disposed_exceptions_key"
private const val SHOW_ORIGINAL_TIME_AGO_KEY = "show_original_time_ago_key"
private const val SHOW_CRASH_THE_PLAYER_KEY = "show_crash_the_player_key"

@KoinViewModel
class DebugSettingsViewModel(
    settings: ObservableSettings,
    private val debugActions: DebugActions
) : ViewModel() {

    private val allowHeapDumpingPref = BooleanPreference(
        ALLOW_HEAP_DUMPING_KEY, false, settings, viewModelScope
    )
    private val allowDisposedExceptionsPref = BooleanPreference(
        ALLOW_DISPOSED_EXCEPTIONS_KEY, false, settings, viewModelScope
    )
    private val showOriginalTimeAgoPref = BooleanPreference(
        SHOW_ORIGINAL_TIME_AGO_KEY, false, settings, viewModelScope
    )
    private val showCrashThePlayerPref = BooleanPreference(
        SHOW_CRASH_THE_PLAYER_KEY, false, settings, viewModelScope
    )

    val isLeakCanaryAvailable: Boolean = debugActions.isLeakCanaryAvailable()
    val allowHeapDumping = allowHeapDumpingPref.state
    val allowDisposedExceptions = allowDisposedExceptionsPref.state
    val showOriginalTimeAgo = showOriginalTimeAgoPref.state
    val showCrashThePlayer = showCrashThePlayerPref.state

    fun toggleAllowHeapDumping(newValue: Boolean) = allowHeapDumpingPref.toggle(newValue)
    fun toggleAllowDisposedExceptions(newValue: Boolean) = allowDisposedExceptionsPref.toggle(newValue)
    fun toggleShowOriginalTimeAgo(newValue: Boolean) = showOriginalTimeAgoPref.toggle(newValue)
    fun toggleShowCrashThePlayer(newValue: Boolean) = showCrashThePlayerPref.toggle(newValue)

    fun crashTheApp() = debugActions.crashTheApp()
    fun showErrorSnackbar() = debugActions.showErrorSnackbar()

    fun createErrorNotification() = debugActions.createErrorNotification()
    fun checkNewStreams() = debugActions.checkNewStreams()
    fun showMemoryLeaks() = debugActions.showMemoryLeaks()
}