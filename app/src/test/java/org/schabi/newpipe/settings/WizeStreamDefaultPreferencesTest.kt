package org.schabi.newpipe.settings

import android.content.SharedPreferences
import com.grack.nanojson.JsonParser
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings

class WizeStreamDefaultPreferencesTest {
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
        WizeStreamDefaultPreferences.applyDefaults(
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
        verify(editor).putBoolean(WizeStreamDefaultPreferences.DEFAULTS_APPLIED_KEY, true)
        verify(editor).commit()
    }

    @Test
    fun `default parser does not clear preferences unless requested`() {
        WizeStreamDefaultPreferences.applyDefaults(
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
            WizeStreamDefaultPreferences.applyDefaults(
                "{\"bad_set\":[\"one\", 2]}".byteInputStream(),
                preferences
            )
        }
    }

    @Test
    fun `resetting settings retains playlist organization`() {
        val key = org.schabi.newpipe.local.bookmark.PlaylistCategories.PREFERENCE_KEY
        val categories = "{\"categories\":[],\"memberships\":{}}"
        `when`(preferences.getString(key, null)).thenReturn(categories)
        WizeStreamDefaultPreferences.applyDefaults(
            "{}".byteInputStream(),
            preferences,
            clearFirst = true
        )
        verify(editor).clear()
        verify(editor).putString(key, categories)
    }

    @Test
    fun `bundled WizeStream defaults include accepted baseline values`() {
        val defaultsPath = listOf(
            Path.of("src/main/res/raw/wizestream_default_preferences.json"),
            Path.of("app/src/main/res/raw/wizestream_default_preferences.json")
        ).first { it.exists() }

        WizeStreamDefaultPreferences.applyDefaults(
            ByteArrayInputStream(Files.readAllBytes(defaultsPath)),
            preferences
        )

        verify(editor).putString("theme_color", "follow_system")
        verify(editor).putBoolean("main_tabs_position", true)
        verify(editor).putString("theme", "auto_device_theme")
        verify(editor).putString("night_theme", "dark_theme")
        verify(editor).putString("list_view_mode", "card")
        verify(editor).putString("default_resolution", "auto_resolution")
        verify(editor).putBoolean("hide_members_only_videos", false)
        verify(editor).putBoolean("show_local_like_controls", true)
        verify(editor).putFloat("playback_speed_key", 1.0f)
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
                    "show_channel_tabs_podcasts",
                    "show_channel_tabs_courses",
                    "show_channel_tabs_channels",
                    "show_channel_tabs_tracks",
                    "show_channel_tabs_about",
                    "show_channel_tabs_shorts",
                    "show_channel_tabs_playlists"
                )
            )
        )
        verify(editor).putStringSet(
            eq("feed_fetch_channel_tabs"),
            eq(
                setOf(
                    "fetch_channel_tabs_shorts",
                    "fetch_channel_tabs_videos",
                    "fetch_channel_tabs_livestreams",
                    "fetch_channel_tabs_tracks",
                    "fetch_channel_tabs_podcasts",
                    "fetch_channel_tabs_courses",
                    "fetch_channel_tabs_likes"
                )
            )
        )
        verify(editor).commit()
    }

    @Test
    fun `bundled WizeStream defaults exclude runtime-owned state`() {
        val defaultsPath = listOf(
            Path.of("src/main/res/raw/wizestream_default_preferences.json"),
            Path.of("app/src/main/res/raw/wizestream_default_preferences.json")
        ).first { it.exists() }
        val defaults = Files.newInputStream(defaultsPath).use { input ->
            JsonParser.`object`().from(input)
        }

        setOf(
            "import_export_data_path",
            "kao_last_checked",
            "is_in_background",
            "last_used_preferences_version"
        ).forEach { key ->
            assertFalse(
                "Bundled defaults must not contain runtime-owned key: $key",
                defaults.containsKey(key)
            )
        }
    }
}
