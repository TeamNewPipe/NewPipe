/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.newpipe.app.model.NotificationAction
import net.newpipe.app.platform.NotificationActions
import net.newpipe.app.preferences.PlayerNotificationPreferences.DEFAULT_COLORIZE
import net.newpipe.app.preferences.PlayerNotificationPreferences.DEFAULT_COMPACT_SLOTS
import net.newpipe.app.preferences.PlayerNotificationPreferences.DEFAULT_SCALE_TO_SQUARE_IMAGE
import net.newpipe.app.preferences.PlayerNotificationPreferences.DEFAULT_SHOW_THUMBNAIL
import net.newpipe.app.preferences.PlayerNotificationPreferences.DEFAULT_SLOT_ACTIONS
import net.newpipe.app.preferences.PlayerNotificationPreferences.KEYS_COMPACT_SLOT
import net.newpipe.app.preferences.PlayerNotificationPreferences.KEYS_SLOT
import net.newpipe.app.preferences.PlayerNotificationPreferences.KEY_COLORIZE
import net.newpipe.app.preferences.PlayerNotificationPreferences.KEY_SCALE_TO_SQUARE_IMAGE
import net.newpipe.app.preferences.PlayerNotificationPreferences.KEY_SHOW_THUMBNAIL
import net.newpipe.app.preferences.PlayerNotificationPreferences.MAX_COMPACT_SLOTS
import net.newpipe.app.preferences.PlayerNotificationPreferences.NO_COMPACT_SLOT
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PlayerNotificationSettingsViewModel(
    private val settings: Settings,
    private val notificationActions: NotificationActions
) : ViewModel() {

    val scaleToSquareImage: StateFlow<Boolean>
        field = booleanFlow(KEY_SCALE_TO_SQUARE_IMAGE, DEFAULT_SCALE_TO_SQUARE_IMAGE)

    val colorize: StateFlow<Boolean>
        field = booleanFlow(KEY_COLORIZE, DEFAULT_COLORIZE)

    val showThumbnail: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_THUMBNAIL, DEFAULT_SHOW_THUMBNAIL)

    val slotActions: StateFlow<List<NotificationAction>>
        field = MutableStateFlow(
            KEYS_SLOT.mapIndexed { slot, key ->
                NotificationAction.fromId(settings.getInt(key, DEFAULT_SLOT_ACTIONS[slot].id))
            }
        )

    val compactSlots: StateFlow<Set<Int>>
        field = MutableStateFlow(storedCompactSlots())

    fun setScaleToSquareImage(value: Boolean) = scaleToSquareImage.persist(KEY_SCALE_TO_SQUARE_IMAGE, value)

    fun setColorize(value: Boolean) = colorize.persist(KEY_COLORIZE, value)

    fun setShowThumbnail(value: Boolean) = showThumbnail.persist(KEY_SHOW_THUMBNAIL, value)

    fun setSlotAction(slot: Int, action: NotificationAction) {
        settings.putInt(KEYS_SLOT[slot], action.id)
        slotActions.update { actions -> actions.toMutableList().apply { set(slot, action) } }
        notificationActions.recreatePlayerNotification()
    }

    fun toggleCompactSlot(slot: Int) {
        val current = compactSlots.value
        val updated = when {
            slot in current -> current - slot
            current.size < MAX_COMPACT_SLOTS -> current + slot
            else -> return
        }
        val sorted = updated.sorted()
        KEYS_COMPACT_SLOT.forEachIndexed { index, key ->
            settings.putInt(key, sorted.getOrElse(index) { NO_COMPACT_SLOT })
        }
        compactSlots.value = updated
        notificationActions.recreatePlayerNotification()
    }

    // The keys only exist once the slots were saved, until then the defaults apply
    private fun storedCompactSlots(): Set<Int> = KEYS_COMPACT_SLOT
        .map { key -> settings.getIntOrNull(key) ?: return DEFAULT_COMPACT_SLOTS }
        .filter { slot -> slot >= 0 }
        .toSet()

    private fun booleanFlow(key: String, defaultValue: Boolean) = MutableStateFlow(
        settings.getBoolean(key, defaultValue)
    )

    private fun MutableStateFlow<Boolean>.persist(key: String, newValue: Boolean) {
        settings.putBoolean(key, newValue)
        value = newValue
        notificationActions.recreatePlayerNotification()
    }
}
