package org.schabi.newpipe.ui.components.menu

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

private const val TAG: String = "LongPressMenuSettings"

fun loadIsHeaderEnabledFromSettings(context: Context): Boolean {
    val key = context.getString(R.string.long_press_menu_is_header_enabled_key)
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, true)
}

fun storeIsHeaderEnabledToSettings(context: Context, enabled: Boolean) {
    val key = context.getString(R.string.long_press_menu_is_header_enabled_key)
    return PreferenceManager.getDefaultSharedPreferences(context).edit {
        putBoolean(key, enabled)
    }
}

fun loadLongPressActionArrangementFromSettings(context: Context): List<LongPressAction.Type> {
    val key = context.getString(R.string.long_press_menu_action_arrangement_key)
    val items = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(key, null)
    if (items == null) {
        return LongPressAction.Type.DefaultEnabledActions
    }

    try {
        val actions = items.split(',')
            .map { item ->
                LongPressAction.Type.entries.first { entry ->
                    entry.id.toString() == item
                }
            }

        // In case there is some bug in the stored data, make sure we don't return duplicate items,
        // as that would break/crash the UI and also not make any sense.
        val actionsDistinct = actions.distinct()
        if (actionsDistinct.size != actions.size) {
            Log.w(TAG, "Actions in settings were not distinct: $actions != $actionsDistinct")
        }
        return actionsDistinct
    } catch (e: NoSuchElementException) {
        Log.e(TAG, "Invalid action in settings", e)
        return LongPressAction.Type.DefaultEnabledActions
    }
}

fun storeLongPressActionArrangementToSettings(context: Context, actions: List<LongPressAction.Type>) {
    val items = actions.joinToString(separator = ",") { it.id.toString() }
    val key = context.getString(R.string.long_press_menu_action_arrangement_key)
    PreferenceManager.getDefaultSharedPreferences(context).edit {
        putString(key, items)
    }
}
