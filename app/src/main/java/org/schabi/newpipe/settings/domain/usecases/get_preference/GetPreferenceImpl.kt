package org.schabi.newpipe.settings.domain.usecases.get_preference

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GetPreferenceImpl<T>(
    private val sharedPreferences: SharedPreferences,
    private val context: Context,
) : GetPreference<T> {
    override fun invoke(key: Int, defaultValue: T): Flow<T> {
        val keyString = context.getString(key)
        return sharedPreferences.getFlowForKey(keyString, defaultValue)
    }

    private fun <T> SharedPreferences.getFlowForKey(key: String, defaultValue: T) = callbackFlow {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    val updated = getPreferenceValue(key, defaultValue)
                    trySend(updated)
                }
            }
        registerOnSharedPreferenceChangeListener(listener)
        println("Current value for $key: ${getPreferenceValue(key, defaultValue)}")
        if (contains(key)) {
            send(getPreferenceValue(key, defaultValue))
        }
        awaitClose {
            unregisterOnSharedPreferenceChangeListener(listener)
            cancel()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> SharedPreferences.getPreferenceValue(key: String, defaultValue: T): T {
        return when (defaultValue) {
            is Boolean -> getBoolean(key, defaultValue) as T
            is Int -> getInt(key, defaultValue) as T
            is Long -> getLong(key, defaultValue) as T
            is Float -> getFloat(key, defaultValue) as T
            is String -> getString(key, defaultValue) as T
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }
}
