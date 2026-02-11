package org.schabi.newpipe.ui.components.menu

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.AddToPlaylist
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Background
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.BackgroundFromHere
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.BackgroundShuffled
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Delete
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Download
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Enqueue
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.EnqueueNext
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.MarkAsWatched
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.OpenInBrowser
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.PlayWithKodi
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Popup
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Remove
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Rename
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.SetAsPlaylistThumbnail
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Share
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.ShowDetails
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Subscribe
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.UnsetPlaylistThumbnail
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Unsubscribe

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

// ShowChannelDetails is not enabled by default, since navigating to channel details can
// also be done by clicking on the uploader name in the long press menu header.
// PlayWithKodi is only added by default if it is enabled in settings.
private val DefaultEnabledActions: List<LongPressAction.Type> = listOf(
    ShowDetails, Enqueue, EnqueueNext, Background, Popup, BackgroundFromHere, BackgroundShuffled,
    Download, AddToPlaylist, Share, OpenInBrowser, MarkAsWatched, Rename, SetAsPlaylistThumbnail,
    UnsetPlaylistThumbnail, Subscribe, Unsubscribe, Delete, Remove
)

private fun getShowPlayWithKodi(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(context.getString(R.string.show_play_with_kodi_key), false)
}

/**
 * Returns the default arrangement of actions in the long press menu. Includes [PlayWithKodi] only
 * if the user enabled Kodi in settings. Note however that this does not prevent the user from
 * adding/removing [PlayWithKodi] anyway, via the long press menu editor.
 */
fun getDefaultLongPressActionArrangement(context: Context): List<LongPressAction.Type> {
    return if (getShowPlayWithKodi(context)) {
        // only include Kodi in the default actions if it is enabled in settings
        DefaultEnabledActions + listOf(PlayWithKodi)
    } else {
        DefaultEnabledActions
    }
}

/**
 * Loads the arrangement of actions in the long press menu from settings, and handles corner cases
 * by returning [getDefaultLongPressActionArrangement]`()`. The returned list is distinct.
 */
fun loadLongPressActionArrangementFromSettings(context: Context): List<LongPressAction.Type> {
    val key = context.getString(R.string.long_press_menu_action_arrangement_key)
    val ids = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(key, null)
    if (ids == null) {
        return getDefaultLongPressActionArrangement(context)
    } else if (ids.isEmpty()) {
        return emptyList() // apparently the user has disabled all buttons
    }

    try {
        val actions = ids.split(',')
            .map { id ->
                LongPressAction.Type.entries.first { entry ->
                    entry.id.toString() == id
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
        return getDefaultLongPressActionArrangement(context)
    }
}

/**
 * Stores the arrangement of actions in the long press menu to settings, as a comma-separated string
 * of [LongPressAction.Type.id]s.
 */
fun storeLongPressActionArrangementToSettings(context: Context, actions: List<LongPressAction.Type>) {
    val items = actions.joinToString(separator = ",") { it.id.toString() }
    val key = context.getString(R.string.long_press_menu_action_arrangement_key)
    PreferenceManager.getDefaultSharedPreferences(context).edit {
        putString(key, items)
    }
}

/**
 * Adds or removes the kodi action from the long press menu. Note however that this does not prevent
 * the user from adding/removing [PlayWithKodi] anyway, via the long press menu editor.
 */
fun addOrRemoveKodiLongPressAction(context: Context) {
    val actions = loadLongPressActionArrangementFromSettings(context).toMutableList()
    if (getShowPlayWithKodi(context)) {
        if (!actions.contains(PlayWithKodi)) {
            actions.add(PlayWithKodi)
        }
    } else {
        actions.remove(PlayWithKodi)
    }
    storeLongPressActionArrangementToSettings(context, actions)
}
