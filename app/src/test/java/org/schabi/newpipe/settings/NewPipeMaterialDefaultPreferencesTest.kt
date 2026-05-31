package org.schabi.newpipe.settings

import android.content.SharedPreferences
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings

class NewPipeMaterialDefaultPreferencesTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        editor = Mockito.mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)
    }

    @Test
    fun `default parser writes SharedPreferences values with their JSON types`() {
        NewPipeMaterialDefaultPreferences.applyDefaults(
            """
                {
                  "boolean_value": true,
                  "string_value": "string",
                  "string_set_value": ["one", "two"],
                  "int_value": 994,
                  "long_value": 1780082904574,
                  "float_value": 1.2,
                  "saved_tabs_key": "{\"tabs\":[{\"tab_id\":2}]}"
                }
            """.trimIndent().byteInputStream(),
            preferences,
            clearFirst = true
        )

        verify(editor).clear()
        verify(editor).putBoolean("boolean_value", true)
        verify(editor).putString("string_value", "string")
        verify(editor).putStringSet("string_set_value", setOf("one", "two"))
        verify(editor).putInt("int_value", 994)
        verify(editor).putLong("long_value", 1780082904574L)
        verify(editor).putFloat("float_value", 1.2f)
        verify(editor).putString("saved_tabs_key", "{\"tabs\":[{\"tab_id\":2}]}")
        verify(editor).putBoolean(NewPipeMaterialDefaultPreferences.DEFAULTS_APPLIED_KEY, true)
        verify(editor).commit()
    }

    @Test
    fun `default parser does not clear preferences unless requested`() {
        NewPipeMaterialDefaultPreferences.applyDefaults(
            "{\"theme_color\":\"follow_system\"}".byteInputStream(),
            preferences,
            clearFirst = false
        )

        verify(editor, never()).clear()
        verify(editor).putString("theme_color", "follow_system")
        verify(editor).commit()
    }

    @Test
    fun `default parser rejects non-string arrays`() {
        assertThrows(IllegalArgumentException::class.java) {
            NewPipeMaterialDefaultPreferences.applyDefaults(
                "{\"bad_set\":[\"one\", 2]}".byteInputStream(),
                preferences
            )
        }
    }

    @Test
    fun `bundled NewPipe Material defaults include accepted baseline values`() {
        val defaultsPath = listOf(
            Path.of("src/main/res/raw/newpipe_material_default_preferences.json"),
            Path.of("app/src/main/res/raw/newpipe_material_default_preferences.json")
        ).first { it.exists() }

        NewPipeMaterialDefaultPreferences.applyDefaults(
            ByteArrayInputStream(Files.readAllBytes(defaultsPath)),
            preferences
        )

        verify(editor).putString("theme_color", "follow_system")
        verify(editor).putBoolean("main_tabs_position", true)
        verify(editor).putString("theme", "auto_device_theme")
        verify(editor).putString("night_theme", "dark_theme")
        verify(editor).putString("list_view_mode", "card")
        verify(editor).putFloat("playback_speed_key", 1.2f)
        verify(editor).putString(
            "saved_tabs_key",
            "{\"tabs\":[{\"tab_id\":2},{\"tab_id\":1},{\"tab_id\":7},{\"tab_id\":3},{\"tab_id\":4}]}"
        )
        verify(editor).putStringSet(
            eq("channel_tabs"),
            eq(
                setOf(
                    "show_channel_tabs_livestreams",
                    "show_channel_tabs_likes",
                    "show_channel_tabs_videos",
                    "show_channel_tabs_albums",
                    "show_channel_tabs_channels",
                    "show_channel_tabs_tracks",
                    "show_channel_tabs_about",
                    "show_channel_tabs_shorts",
                    "show_channel_tabs_playlists"
                )
            )
        )
        verify(editor).putLong("kao_last_checked", 1780082904574L)
        verify(editor).putInt("last_used_preferences_version", 8)
        verify(editor).commit()
    }
}
