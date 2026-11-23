/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.StringRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import java.util.function.LongConsumer
import java.util.function.LongSupplier
import org.schabi.newpipe.R

/** Chooses the note action without changing which streams can use the player button. */
internal class PlayerNoteButton(
    private val button: View,
    private val currentPosition: LongSupplier,
    private val addNote: LongConsumer,
    private val openNotes: LongConsumer
) : AutoCloseable {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(button.context)
    private val preferenceKey = button.context.getString(R.string.player_note_button_tap_action_key)
    private val originalDescription = button.contentDescription
    private val originalAccessibilityDelegate = ViewCompat.getAccessibilityDelegate(button)
    private var closed = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!closed && (key == null || key == preferenceKey)) updateLabels()
    }

    init {
        button.setOnClickListener { perform(tapAction()) }
        button.setOnLongClickListener {
            perform(tapAction().other())
            true
        }
        ViewCompat.setAccessibilityDelegate(
            button,
            NoteAccessibilityDelegate(originalAccessibilityDelegate ?: AccessibilityDelegateCompat())
        )
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateLabels()
    }

    private fun tapAction(): Action = Action.fromValue(preferences.getString(preferenceKey, null))

    private fun perform(action: Action) {
        // Capture once at the initiating gesture, before opening a dialog or loading its notes.
        val timestampMillis = currentPosition.asLong
        when (action) {
            Action.ADD_NOTE -> addNote.accept(timestampMillis)
            Action.OPEN_NOTES -> openNotes.accept(timestampMillis)
        }
    }

    private fun updateLabels() {
        val action = tapAction()
        val tapLabel = button.context.getString(action.label)
        val holdLabel = button.context.getString(action.other().label)
        button.contentDescription = tapLabel
        ViewCompat.replaceAccessibilityAction(button, AccessibilityActionCompat.ACTION_CLICK, tapLabel) { view, _ ->
            view.isEnabled && view.performClick()
        }
        ViewCompat.replaceAccessibilityAction(button, AccessibilityActionCompat.ACTION_LONG_CLICK, holdLabel) { view, _ ->
            view.isEnabled && view.performLongClick()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        button.setOnClickListener(null)
        button.setOnLongClickListener(null)
        button.isLongClickable = false
        ViewCompat.removeAccessibilityAction(button, AccessibilityActionCompat.ACTION_CLICK.id)
        ViewCompat.removeAccessibilityAction(button, AccessibilityActionCompat.ACTION_LONG_CLICK.id)
        if (originalAccessibilityDelegate == null) {
            button.setAccessibilityDelegate(null)
        } else {
            ViewCompat.setAccessibilityDelegate(button, originalAccessibilityDelegate)
        }
        button.contentDescription = originalDescription
    }

    private class NoteAccessibilityDelegate(private val original: AccessibilityDelegateCompat) : AccessibilityDelegateCompat() {
        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            if (action == AccessibilityActionCompat.ACTION_CLICK.id ||
                action == AccessibilityActionCompat.ACTION_LONG_CLICK.id
            ) {
                // A false action-command result falls through to the framework delegate, which
                // may still click a disabled view. Reject it before that fallback is reached.
                return host.isEnabled && super.performAccessibilityAction(host, action, args)
            }
            return original.performAccessibilityAction(host, action, args)
        }

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) = original.onInitializeAccessibilityNodeInfo(host, info)

        override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) = original.onInitializeAccessibilityEvent(host, event)

        override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) = original.onPopulateAccessibilityEvent(host, event)

        override fun dispatchPopulateAccessibilityEvent(host: View, event: AccessibilityEvent): Boolean = original.dispatchPopulateAccessibilityEvent(host, event)

        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean = original.onRequestSendAccessibilityEvent(host, child, event)

        override fun sendAccessibilityEvent(host: View, eventType: Int) = original.sendAccessibilityEvent(host, eventType)

        override fun sendAccessibilityEventUnchecked(host: View, event: AccessibilityEvent) = original.sendAccessibilityEventUnchecked(host, event)

        override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProviderCompat? = original.getAccessibilityNodeProvider(host)
    }

    private enum class Action(@StringRes val label: Int) {
        OPEN_NOTES(R.string.player_note_button_open),
        ADD_NOTE(R.string.learning_note_add);

        fun other(): Action = if (this == OPEN_NOTES) ADD_NOTE else OPEN_NOTES

        companion object {
            fun fromValue(value: String?): Action = if (value == "add_note") ADD_NOTE else OPEN_NOTES
        }
    }

    companion object {
        @JvmStatic
        fun bindPreference(preference: ListPreference) {
            preference.summaryProvider = Preference.SummaryProvider<ListPreference> { row ->
                val action = Action.fromValue(row.value)
                row.context.getString(
                    R.string.player_note_button_tap_action_summary,
                    row.context.getString(action.label),
                    row.context.getString(action.other().label)
                )
            }
        }
    }
}
