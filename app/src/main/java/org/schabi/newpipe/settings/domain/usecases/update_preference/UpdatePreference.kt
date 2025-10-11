package org.schabi.newpipe.settings.domain.usecases.update_preference

fun interface UpdatePreference<T> {
    suspend operator fun invoke(key: Int, value: T)
}
