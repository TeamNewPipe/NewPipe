package org.schabi.newpipe.settings.domain.usecases.update_preference

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class UpdatePreferenceImpl<T>(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val setter: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor,
) : UpdatePreference<T> {
    override suspend operator fun invoke(key: Int, value: T) {
        val stringKey = context.getString(key)
        sharedPreferences.edit {
            setter(stringKey, value)
        }
    }
}
