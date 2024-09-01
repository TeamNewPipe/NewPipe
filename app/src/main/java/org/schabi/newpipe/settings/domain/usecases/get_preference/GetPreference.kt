package org.schabi.newpipe.settings.domain.usecases.get_preference

import kotlinx.coroutines.flow.Flow

fun interface GetPreference<T> {
    operator fun invoke(key: Int, defaultValue: T): Flow<T>
}
