package org.schabi.newpipe.settings.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val preferenceManager: SharedPreferences
) : AndroidViewModel(context.applicationContext as Application) {

    private var _settingsLayoutRedesignPref: Boolean
        get() = preferenceManager.getBoolean(
            Localization.compatGetString(getApplication(), R.string.settings_layout_redesign_key),
            false
        )
        set(value) {
            preferenceManager.edit().putBoolean(
                Localization.compatGetString(getApplication(), R.string.settings_layout_redesign_key),
                value
            ).apply()
        }
    private val _settingsLayoutRedesign: MutableStateFlow<Boolean> =
        MutableStateFlow(_settingsLayoutRedesignPref)
    val settingsLayoutRedesign = _settingsLayoutRedesign.asStateFlow()

    fun toggleSettingsLayoutRedesign(newState: Boolean) {
        _settingsLayoutRedesign.value = newState
        _settingsLayoutRedesignPref = newState
    }
}
