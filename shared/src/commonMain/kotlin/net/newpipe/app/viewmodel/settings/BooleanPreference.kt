package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Encapsulates a boolean preference backed by the multiplatform
[com.russhwolf.settings.ObservableSettings].
 * Exposes a [kotlinx.coroutines.flow.StateFlow] that stays in sync with external writes (other
screens, services,
 * migrations) and a [toggle] function to update it.
 *
 * @param key The preference key (already a String — caller resolves any
string-resource key).
 * @param defaultValue The default value when the key is absent.
 * @param settings The shared, observable key-value store.
 * @param scope Scope to keep the underlying flow alive (typically the
ViewModel scope).
 */
internal class BooleanPreference(
    private val key: String,
    private val defaultValue: Boolean,
    private val settings: ObservableSettings,
    scope: CoroutineScope
) {
    val state: StateFlow<Boolean> = settings
        .getBooleanFlow(key, defaultValue)
        .stateIn(
            scope = scope,
            started = SharingStarted.Companion.Eagerly,
            initialValue = settings.getBoolean(key, defaultValue)
        )

    fun toggle(newValue: Boolean) {
        settings.putBoolean(key, newValue)
    }
}