package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * String-valued analogue of [BooleanPreference]. Backs [ListPreference]
 * composables — anything that stores a small set of opaque string values.
 *
 * @param key The preference key as stored in [com.russhwolf.settings.ObservableSettings].
 * @param defaultValue Used when the key is missing.
 * @param settings The shared, observable key-value store.
 * @param scope Scope that keeps the underlying flow alive (typically viewModelScope).
 */
internal class StringPreference(
    private val key: String,
    private val defaultValue: String,
    private val settings: ObservableSettings,
    scope: CoroutineScope
) {
    val state: StateFlow<String> = settings
        .getStringFlow(key, defaultValue)
        .stateIn(
            scope = scope,
            started = SharingStarted.Companion.Eagerly,
            initialValue = settings.getString(key, defaultValue)
        )

    fun set(newValue: String) {
        settings.putString(key, newValue)
    }
}