/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_ALLOW_DISPOSED_EXCEPTIONS
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_ALLOW_HEAP_DUMPING
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_SHOW_CRASH_THE_PLAYER
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_SHOW_ORIGINAL_TIME_AGO
import net.newpipe.app.preferences.DebugPreferences.KEY_ALLOW_DISPOSED_EXCEPTIONS
import net.newpipe.app.preferences.DebugPreferences.KEY_ALLOW_HEAP_DUMPING
import net.newpipe.app.preferences.DebugPreferences.KEY_SHOW_CRASH_THE_PLAYER
import net.newpipe.app.preferences.DebugPreferences.KEY_SHOW_ORIGINAL_TIME_AGO
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DebugSettingsViewModel(private val settings: Settings) : ViewModel() {

    val allowHeapDumping: StateFlow<Boolean>
        field = booleanFlow(KEY_ALLOW_HEAP_DUMPING, DEFAULT_ALLOW_HEAP_DUMPING)

    val allowDisposedExceptions: StateFlow<Boolean>
        field = booleanFlow(KEY_ALLOW_DISPOSED_EXCEPTIONS, DEFAULT_ALLOW_DISPOSED_EXCEPTIONS)

    val showOriginalTimeAgo: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_ORIGINAL_TIME_AGO, DEFAULT_SHOW_ORIGINAL_TIME_AGO)

    val showCrashThePlayer: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_CRASH_THE_PLAYER, DEFAULT_SHOW_CRASH_THE_PLAYER)

    /**
     * LeakCanary reads this flag once, when the app starts, so the change only takes effect after
     * a restart.
     */
    fun setAllowHeapDumping(value: Boolean) = allowHeapDumping
        .persist(KEY_ALLOW_HEAP_DUMPING, value)

    fun setAllowDisposedExceptions(value: Boolean) = allowDisposedExceptions
        .persist(KEY_ALLOW_DISPOSED_EXCEPTIONS, value)

    fun setShowOriginalTimeAgo(value: Boolean) = showOriginalTimeAgo
        .persist(KEY_SHOW_ORIGINAL_TIME_AGO, value)

    fun setShowCrashThePlayer(value: Boolean) = showCrashThePlayer
        .persist(KEY_SHOW_CRASH_THE_PLAYER, value)

    private fun booleanFlow(key: String, defaultValue: Boolean) =
        MutableStateFlow(settings.getBoolean(key, defaultValue))

    private fun MutableStateFlow<Boolean>.persist(key: String, newValue: Boolean) {
        settings.putBoolean(key, newValue)
        value = newValue
    }
}
