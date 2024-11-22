package org.schabi.newpipe.settings.domain.usecases.get_preference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class GetPreferenceFake<T>(
    private val preferences: MutableStateFlow<MutableMap<Int, T>>,
) : GetPreference<T> {
    override fun invoke(key: Int, defaultValue: T): Flow<T> {
        return preferences.asStateFlow().map {
            it[key] ?: defaultValue
        }
    }
}
