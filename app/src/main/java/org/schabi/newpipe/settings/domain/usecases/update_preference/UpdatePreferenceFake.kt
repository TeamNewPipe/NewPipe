package org.schabi.newpipe.settings.domain.usecases.update_preference

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class UpdatePreferenceFake<T>(
    private val preferences: MutableStateFlow<MutableMap<Int, T>>,
) : UpdatePreference<T> {
    override suspend fun invoke(key: Int, value: T) {
        preferences.update {
            it.apply {
                put(key, value)
            }
        }
    }
}
