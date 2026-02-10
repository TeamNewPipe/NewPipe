package org.schabi.newpipe.ui.components.menu

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.App
import org.schabi.newpipe.R

/**
 * Just handles loading preferences and listening for preference changes for [isHeaderEnabled] and
 * [actionArrangement].
 *
 * Note: Since view models can't have access to the UI's Context, we use [App.instance] instead to
 * fetch shared preferences. This won't be needed once we will have a Hilt injected repository that
 * provides access to a modern alternative to shared preferences. The whole thing with the shared
 * preference listener will not be necessary with the modern alternative.
 */
class LongPressMenuViewModel : ViewModel() {
    private val _isHeaderEnabled = MutableStateFlow(
        loadIsHeaderEnabledFromSettings(App.instance)
    )

    /**
     * Whether the user wants the header be shown in the long press menu.
     */
    val isHeaderEnabled: StateFlow<Boolean> = _isHeaderEnabled.asStateFlow()

    private val _actionArrangement = MutableStateFlow(
        loadLongPressActionArrangementFromSettings(App.instance)
    )

    /**
     * The actions that the user wants to be shown (if they are applicable), and in which order.
     */
    val actionArrangement: StateFlow<List<LongPressAction.Type>> = _actionArrangement.asStateFlow()

    private val prefs = PreferenceManager.getDefaultSharedPreferences(App.instance)
    private val isHeaderEnabledKey =
        App.instance.getString(R.string.long_press_menu_is_header_enabled_key)
    private val actionArrangementKey =
        App.instance.getString(R.string.long_press_menu_action_arrangement_key)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == isHeaderEnabledKey) {
            _isHeaderEnabled.value = loadIsHeaderEnabledFromSettings(App.instance)
        } else if (key == actionArrangementKey) {
            _actionArrangement.value = loadLongPressActionArrangementFromSettings(App.instance)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
