package org.schabi.newpipe.local.feed.quickadd

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class QuickAddPreferenceTest {
    private val profile = "00000000-0000-0000-0000-000000000000"

    @Test
    fun validLongTargetDoesNotRewritePreferences() {
        val key = QuickAddPreference.targetKey(profile)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        `when`(preferences.getLong(key, -1L)).thenReturn(42L)

        assertEquals(42L, QuickAddPreference.readTargetId(preferences, profile))
        verify(preferences, never()).edit()
    }

    @Test
    fun alreadyRestoredIntegerIsConvertedAndPersistedAsLong() {
        val key = QuickAddPreference.targetKey(profile)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        `when`(preferences.getLong(key, -1L)).thenThrow(ClassCastException())
        `when`(preferences.all).thenReturn(mapOf(key to 42))
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.putLong(key, 42L)).thenReturn(editor)

        assertEquals(42L, QuickAddPreference.readTargetId(preferences, profile))
        verify(editor).putLong(key, 42L)
        verify(editor).apply()
    }

    @Test
    fun wrongTypeIsDisabledInsteadOfCrashing() {
        val key = QuickAddPreference.targetKey(profile)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        `when`(preferences.getLong(key, -1L)).thenThrow(ClassCastException())
        `when`(preferences.all).thenReturn(mapOf(key to "not-a-playlist"))
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.putLong(key, -1L)).thenReturn(editor)

        assertEquals(-1L, QuickAddPreference.readTargetId(preferences, profile))
        verify(editor).putLong(key, -1L)
        verify(editor).apply()
    }
}
