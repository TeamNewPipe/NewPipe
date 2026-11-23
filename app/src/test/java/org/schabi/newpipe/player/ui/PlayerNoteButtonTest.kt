/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import java.util.function.LongConsumer
import java.util.function.LongSupplier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33])
class PlayerNoteButtonTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var key: String
    private lateinit var button: ImageButton
    private var binding: PlayerNoteButton? = null
    private var position = 12_345L
    private var positionReads = 0
    private val added = mutableListOf<Long>()
    private val opened = mutableListOf<Long>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        key = context.getString(R.string.player_note_button_tap_action_key)
        preferences.edit().remove(key).commit()
        button = ImageButton(context).apply {
            contentDescription = context.getString(R.string.playlist_video_notes)
        }
    }

    @After
    fun tearDown() {
        binding?.close()
        preferences.edit().remove(key).commit()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun defaultTapOpensNotesAndHoldAddsAtEachInitiatingPosition() {
        bind()
        assertLabels(R.string.player_note_button_open, R.string.learning_note_add)

        assertTrue(button.performClick())
        position = 67_890
        assertTrue(button.performLongClick())

        assertEquals(listOf(12_345L), opened)
        assertEquals(listOf(67_890L), added)
        assertEquals(2, positionReads)
    }

    @Test
    fun savedAddChoiceSwapsBothActionsAndKeepsTheCapturedPosition() {
        saveChoice("add_note")
        bind()
        assertLabels(R.string.learning_note_add, R.string.player_note_button_open)

        assertTrue(button.performClick())
        position = 67_890
        assertTrue(button.performLongClick())
        position = 90_000

        assertEquals(listOf(12_345L), added)
        assertEquals(listOf(67_890L), opened)
        assertEquals(2, positionReads)
    }

    @Test
    fun changingTheChoiceUpdatesAnExistingButtonAndUnknownValuesUseTheDefault() {
        bind()
        saveChoice("add_note")
        assertLabels(R.string.learning_note_add, R.string.player_note_button_open)
        assertTrue(button.performClick())
        assertEquals(listOf(12_345L), added)
        assertTrue(opened.isEmpty())

        saveChoice("unknown_action")
        assertLabels(R.string.player_note_button_open, R.string.learning_note_add)
        position = 45_000
        assertTrue(button.performClick())
        assertEquals(listOf(45_000L), opened)
        assertTrue(button.performLongClick())
        assertEquals(listOf(12_345L, 45_000L), added)
    }

    @Test
    fun accessibilityActionsUseTheSameComplementaryCallbacksAndLabels() {
        bind()
        assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
        assertEquals(listOf(12_345L), opened)
        assertEquals(listOf(12_345L), added)

        saveChoice("add_note")
        assertLabels(R.string.learning_note_add, R.string.player_note_button_open)
        position = 54_321
        assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
        assertEquals(listOf(12_345L, 54_321L), opened)
        assertEquals(listOf(12_345L, 54_321L), added)
        assertEquals(4, positionReads)
    }

    @Test
    fun existingControlHintsDoNotReplaceTheConsumedHoldAction() {
        bind()
        var contextMenus = 0
        button.setOnCreateContextMenuListener { _, _, _ -> contextMenus++ }
        val root = android.widget.FrameLayout(context).apply { addView(button) }
        PlayerControlHints.install(root)

        assertTrue(button.performLongClick())
        assertEquals(listOf(12_345L), added)
        assertTrue(opened.isEmpty())
        assertEquals(0, contextMenus)
        assertNull(root.findViewById<View>(R.id.player_control_hint))
    }

    @Test
    fun closingRemovesCallbacksAndPreferenceObservationWithoutChangingEligibility() {
        button.visibility = View.GONE
        button.isEnabled = false
        bind()
        assertEquals(View.GONE, button.visibility)
        assertFalse(button.isEnabled)
        assertFalse(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertFalse(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
        assertEquals(0, positionReads)

        binding!!.close()
        binding!!.close()
        saveChoice("add_note")
        assertEquals(context.getString(R.string.playlist_video_notes), button.contentDescription)
        assertFalse(button.hasOnClickListeners())
        assertFalse(button.isLongClickable)
        assertFalse(button.performClick())
        assertNull(actionLabel(AccessibilityNodeInfo.ACTION_CLICK))
        assertNull(actionLabel(AccessibilityNodeInfo.ACTION_LONG_CLICK))
        assertNull(ViewCompat.getAccessibilityDelegate(button))
        assertEquals(0, positionReads)
        assertTrue(added.isEmpty())
        assertTrue(opened.isEmpty())
        assertEquals(View.GONE, button.visibility)
        assertFalse(button.isEnabled)

        button.visibility = View.VISIBLE
        button.isEnabled = true
        bind()
        assertLabels(R.string.learning_note_add, R.string.player_note_button_open)
        assertTrue(button.performClick())
        assertEquals(listOf(12_345L), added)
        assertEquals(1, positionReads)
    }

    @Test
    fun existingAccessibilityDelegateKeepsItsOtherBehaviorAndIsRestoredOnClose() {
        var previousActions = 0
        val previous = object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "Existing note control"
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_FOCUS) {
                    previousActions++
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        ViewCompat.setAccessibilityDelegate(button, previous)
        bind()
        assertLabels(R.string.player_note_button_open, R.string.learning_note_add)
        val info = button.createAccessibilityNodeInfo()
        try {
            assertEquals("Existing note control", info.className)
        } finally {
            info.recycle()
        }
        assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, null))
        assertEquals(1, previousActions)

        button.isEnabled = false
        assertFalse(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertFalse(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
        assertEquals(0, positionReads)
        assertTrue(added.isEmpty())
        assertTrue(opened.isEmpty())

        binding!!.close()
        assertSame(previous, ViewCompat.getAccessibilityDelegate(button))
        assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, null))
        assertEquals(2, previousActions)
    }

    @Test
    fun preferenceSummaryExplainsBothActionsForTheCurrentChoice() {
        val preference = ListPreference(context).apply {
            setEntries(R.array.player_note_button_tap_action_entries)
            setEntryValues(R.array.player_note_button_tap_action_values)
            isPersistent = false
        }
        PlayerNoteButton.bindPreference(preference)
        assertEquals(summary(R.string.player_note_button_open, R.string.learning_note_add), preference.summary)

        preference.value = "add_note"
        assertEquals(context.getString(R.string.learning_note_add), preference.entry)
        assertEquals(summary(R.string.learning_note_add, R.string.player_note_button_open), preference.summary)

        preference.value = "open_notes"
        assertEquals(context.getString(R.string.player_note_button_open), preference.entry)
        assertEquals(summary(R.string.player_note_button_open, R.string.learning_note_add), preference.summary)
    }

    private fun bind() {
        binding?.close()
        binding = PlayerNoteButton(
            button,
            LongSupplier {
                positionReads++
                position
            },
            LongConsumer { added += it },
            LongConsumer { opened += it }
        )
    }

    private fun saveChoice(value: String) {
        preferences.edit().putString(key, value).commit()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertLabels(tap: Int, hold: Int) {
        assertEquals(context.getString(tap), button.contentDescription)
        assertEquals(context.getString(tap), actionLabel(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(context.getString(hold), actionLabel(AccessibilityNodeInfo.ACTION_LONG_CLICK))
    }

    private fun actionLabel(id: Int): String? {
        val info = button.createAccessibilityNodeInfo()
        try {
            return info.actionList.firstOrNull { it.id == id }?.label?.toString()
        } finally {
            info.recycle()
        }
    }

    private fun summary(tap: Int, hold: Int): String = context.getString(
        R.string.player_note_button_tap_action_summary,
        context.getString(tap),
        context.getString(hold)
    )
}
