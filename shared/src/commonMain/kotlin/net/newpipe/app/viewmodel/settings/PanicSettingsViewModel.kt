/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import net.newpipe.app.platform.PanicActions
import net.newpipe.app.preferences.PanicPreferences
import net.newpipe.app.screen.settings.model.PanicApp
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PanicSettingsViewModel(
    private val settings: Settings,
    private val panicActions: PanicActions
) : ViewModel() {

    val triggerApp: StateFlow<PanicApp?>
        field = MutableStateFlow(
            // PanicPreferences.DEFAULT_TRIGGER_APPS is null
            settings.getStringOrNull(PanicPreferences.KEY_TRIGGER_APPS)
                ?.let { Json.decodeFromString<PanicApp>(it) }
        )

    val exitApp: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                PanicPreferences.KEY_EXIT_APP,
                PanicPreferences.DEFAULT_EXIT_APP
            )
        )

    val removeFromRecentApps: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                PanicPreferences.KEY_REMOVE_FROM_RECENT_APPS,
                PanicPreferences.DEFAULT_REMOVE_FROM_RECENT_APPS
            )
        )

    val clearAppDatabase: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                PanicPreferences.KEY_CLEAR_APP_DATABASE,
                PanicPreferences.DEFAULT_CLEAR_APP_DATABASE
            )
        )

    val clearSearchHistory: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                PanicPreferences.KEY_CLEAR_SEARCH_HISTORY,
                PanicPreferences.DEFAULT_CLEAR_SEARCH_HISTORY
            )
        )

    val clearWatchHistory: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                PanicPreferences.KEY_CLEAR_WATCH_HISTORY,
                PanicPreferences.DEFAULT_CLEAR_WATCH_HISTORY
            )
        )

    fun setTriggerPackageName(triggerPackageName: String) {
        val triggerApp = panicActions.getPanicApp(triggerPackageName)
        if (triggerApp != null) {
            setTriggerApp(triggerApp)
        }
    }

    fun setTriggerApp(value: PanicApp) {
        settings.putString(PanicPreferences.KEY_TRIGGER_APPS, Json.encodeToString(value))
        triggerApp.value = value
    }

    fun setExitApp(value: Boolean) {
        settings.putBoolean(PanicPreferences.KEY_EXIT_APP, value)
        exitApp.value = value
    }

    fun setRemoveFromRecentApps(value: Boolean) {
        settings.putBoolean(PanicPreferences.KEY_REMOVE_FROM_RECENT_APPS, value)
        removeFromRecentApps.value = value
    }

    fun setClearAppDatabase(value: Boolean) {
        settings.putBoolean(PanicPreferences.KEY_CLEAR_APP_DATABASE, value)
        clearAppDatabase.value = value
    }

    fun setClearSearchHistory(value: Boolean) {
        settings.putBoolean(PanicPreferences.KEY_CLEAR_SEARCH_HISTORY, value)
        clearSearchHistory.value = value
    }

    fun setClearWatchHistory(value: Boolean) {
        settings.putBoolean(PanicPreferences.KEY_CLEAR_WATCH_HISTORY, value)
        clearWatchHistory.value = value
    }
}
