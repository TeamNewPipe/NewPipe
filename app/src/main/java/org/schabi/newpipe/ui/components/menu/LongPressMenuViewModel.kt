package org.schabi.newpipe.ui.components.menu

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.App
import org.schabi.newpipe.R

/**
 * Since view models can't have access to the UI's Context, we use [App.instance] instead to fetch
 * shared preferences. This is not the best but won't be needed anyway once we will have a Hilt
 * injected repository that provides access to a modern alternative to shared preferences. The whole
 * thing with the shared preference listener will not be necessary with the modern alternative.
 */
class LongPressMenuViewModel : ViewModel() {
    private val _isHeaderEnabled = MutableStateFlow(
        loadIsHeaderEnabledFromSettings(App.instance)
    )
    val isHeaderEnabled: StateFlow<Boolean> = _isHeaderEnabled.asStateFlow()

    private val _actionArrangement = MutableStateFlow(
        loadLongPressActionArrangementFromSettings(App.instance)
    )
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
