/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.export

import android.app.Application
import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.sync.PortableSettingId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SelectiveSettingsTransferTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun roundTripSelectedBooleanStringFloatDoesNotExposeSecrets() {
        val ids = setOf(
            PortableSettingId.SHOW_COMMENTS,
            PortableSettingId.THEME,
            PortableSettingId.PLAYBACK_SPEED
        )
        val comments = context.getString(R.string.show_comments_key)
        val theme = context.getString(R.string.theme_key)
        val speed = context.getString(R.string.playback_speed_key)
        preferences.edit().clear().putBoolean(comments, false)
            .putString(theme, "dark").putFloat(speed, 1.5f)
            .putString("device_sync_secret_key", "private").commit()
        val (document, count) = SelectiveSettingsTransfer.export(context, preferences, ids)
        assertEquals(3, count)
        assertFalse(document.contains("device_sync_secret_key"))
        assertFalse(document.contains("private"))

        preferences.edit().putBoolean(comments, true).putString(theme, "light")
            .putFloat(speed, 1f).commit()
        assertEquals(3, SelectiveSettingsTransfer.importSelected(context, preferences, ids, document))
        assertFalse(preferences.getBoolean(comments, true))
        assertEquals("dark", preferences.getString(theme, ""))
        assertEquals(1.5f, preferences.getFloat(speed, 0f), 0.001f)
        assertEquals("private", preferences.getString("device_sync_secret_key", ""))
        assertEquals(3, SelectiveSettingsTransfer.reset(context, preferences, ids))
        assertFalse(preferences.contains(comments))
        assertFalse(preferences.contains(theme))
        assertFalse(preferences.contains(speed))
        assertTrue(preferences.contains("device_sync_secret_key"))
    }

    @Test
    fun wrongTypeAndUnknownSettingNeverPartiallyApply() {
        val comments = context.getString(R.string.show_comments_key)
        preferences.edit().clear().putBoolean(comments, true).commit()
        val invalid = JSONObject()
            .put("format", "wizestream-selected-settings")
            .put("version", 1)
            .put(
                "settings",
                JSONObject()
                    .put(
                        "SHOW_COMMENTS",
                        JSONObject().put("type", "BOOLEAN").put("value", false)
                    )
                    .put(
                        "PLAYBACK_SPEED",
                        JSONObject().put("type", "FLOAT").put("value", "invalid")
                    )
            ).toString()
        val requested = setOf(PortableSettingId.SHOW_COMMENTS, PortableSettingId.PLAYBACK_SPEED)
        assertTrue(
            runCatching {
                SelectiveSettingsTransfer.importSelected(context, preferences, requested, invalid)
            }.isFailure
        )
        assertTrue(preferences.getBoolean(comments, false))

        val unknown = JSONObject(invalid)
        unknown.getJSONObject("settings").put(
            "DEVICE_TOKEN",
            JSONObject().put("type", "STRING").put("value", "secret")
        )
        assertTrue(
            runCatching {
                SelectiveSettingsTransfer.importSelected(context, preferences, requested, unknown.toString())
            }.isFailure
        )
        assertTrue(preferences.getBoolean(comments, false))
    }
}
