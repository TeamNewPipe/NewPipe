/*
 * SPDX-FileCopyrightText: 2015-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.error

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.preference.PreferenceManager
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.settings.WizeStreamDefaultPreferences

/**
 * Keeps a recoverable copy of the user's preferences while the app starts with safe defaults.
 * The snapshot lives outside the default preference file so resetting preferences cannot erase it.
 */
object CrashRecoveryManager {
    private const val STATE_PREFERENCES = "crash_recovery_state"
    private const val ENTER_PENDING = "enter_pending"
    private const val ACTIVE = "active"
    private const val SNAPSHOT_PRESENT = "snapshot_present"
    private const val SNAPSHOT_PREFIX = "preference:"

    /** Requests a safe-mode start during the next process launch. */
    fun requestSafeMode(context: Context): Boolean = statePreferences(context)
        .edit()
        .putBoolean(ENTER_PENDING, true)
        .commit()

    /**
     * Applies or restores recovery preferences before normal application initialization.
     *
     * Safe mode lasts for one process. If that process ends without an explicit choice, the next
     * launch restores the original preferences automatically.
     *
     * @return true when this process should run with the recovery defaults.
     */
    fun prepareStartup(context: Context): Boolean {
        val state = statePreferences(context)

        if (state.getBoolean(ENTER_PENDING, false)) {
            return enterSafeMode(context, state)
        }

        if (state.getBoolean(ACTIVE, false)) {
            return !restoreOriginalPreferences(context, state)
        }

        return false
    }

    fun isSafeModeActive(context: Context): Boolean = statePreferences(context)
        .getBoolean(ACTIVE, false)

    /** Keeps the current recovery preferences and discards the original snapshot. */
    fun keepCurrentPreferences(context: Context): Boolean = statePreferences(context)
        .edit()
        .clear()
        .commit()

    private fun enterSafeMode(context: Context, state: SharedPreferences): Boolean {
        if (!state.getBoolean(SNAPSHOT_PRESENT, false) && !savePreferenceSnapshot(context, state)) {
            state.edit().remove(ENTER_PENDING).apply()
            Log.e(TAG, "Could not save preferences before entering safe mode")
            return false
        }

        return runCatching {
            WizeStreamDefaultPreferences.applyBundledDefaults(context, clearFirst = true)
            check(
                state.edit()
                    .putBoolean(ACTIVE, true)
                    .remove(ENTER_PENDING)
                    .commit()
            ) { "Could not persist the safe-mode state" }
            true
        }.getOrElse { error ->
            Log.e(TAG, "Could not enter safe mode", error)
            restoreOriginalPreferences(context, state)
            false
        }
    }

    private fun savePreferenceSnapshot(
        context: Context,
        state: SharedPreferences
    ): Boolean {
        val editor = state.edit()
        state.all.keys
            .filter { it.startsWith(SNAPSHOT_PREFIX) }
            .forEach(editor::remove)

        PreferenceManager.getDefaultSharedPreferences(context).all.forEach { (key, value) ->
            putPreference(editor, snapshotKey(key), value)
        }

        return editor.putBoolean(SNAPSHOT_PRESENT, true).commit()
    }

    private fun restoreOriginalPreferences(
        context: Context,
        state: SharedPreferences
    ): Boolean {
        if (!state.getBoolean(SNAPSHOT_PRESENT, false)) {
            state.edit().clear().apply()
            return true
        }

        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
        state.all.forEach { (key, value) ->
            if (key.startsWith(SNAPSHOT_PREFIX)) {
                putPreference(editor, originalKey(key), value)
            }
        }

        if (editor.commit()) {
            state.edit().clear().commit()
            return true
        } else {
            Log.e(TAG, "Could not restore preferences after safe mode")
            return false
        }
    }

    private fun putPreference(
        editor: SharedPreferences.Editor,
        key: String,
        value: Any?
    ) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            null -> editor.remove(key)
            else -> Log.w(TAG, "Ignoring unsupported preference type for $key")
        }
    }

    private fun snapshotKey(key: String): String = SNAPSHOT_PREFIX + Base64.encodeToString(
        key.toByteArray(StandardCharsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE
    )

    private fun originalKey(snapshotKey: String): String = String(
        Base64.decode(
            snapshotKey.removePrefix(SNAPSHOT_PREFIX),
            Base64.NO_WRAP or Base64.URL_SAFE
        ),
        StandardCharsets.UTF_8
    )

    private fun statePreferences(context: Context): SharedPreferences = context
        .getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)

    private val TAG = CrashRecoveryManager::class.java.simpleName
}
