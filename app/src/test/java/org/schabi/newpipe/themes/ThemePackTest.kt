package org.schabi.newpipe.themes

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.schabi.newpipe.R
import org.schabi.newpipe.player.ui.PlayerUiTheme
import org.schabi.newpipe.util.ThemeHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 35])
class ThemePackTest {
    private val context: android.content.Context get() = RuntimeEnvironment.getApplication()
    private val minimal = """{"format":"wizestream-theme","version":1,"name":"Test","author":"Creator"}"""

    @Before
    fun clear() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun defaultsAndRoundTrip() {
        val pack = ThemePack.parse(minimal)
        assertEquals("system", pack.mode)
        assertEquals("dark", pack.night)
        assertEquals("wizestream", pack.darkAccent)
        assertEquals(pack, ThemePack.parse(pack.encode()))
    }

    @Test
    fun rejectsUnsupportedUnsafeAndMalformedInput() {
        listOf(
            minimal.replace("\"version\":1", "\"version\":4"),
            minimal.replace("\"version\":1", "\"version\":\"1\""),
            minimal.replace("\"version\":1", "\"version\":1.0"),
            minimal.replace("\"Test\"", "null"),
            minimal.replace("\"Test\"", "\"\""),
            minimal.replace("\"Test\"", "\"${"a".repeat(81)}\""),
            minimal.replace("\"Test\"", "\"a\\nb\""),
            minimal.replace("\"Test\"", "\"a\\u202Eb\""),
            minimal.dropLast(1) + ",\"mode\":\"glass\"}",
            minimal.dropLast(1) + ",\"lightAccent\":\"#123456\"}",
            minimal.dropLast(1) + ",\"css\":\"body {}\"}",
            minimal.dropLast(1) + ",\"name\":\"Duplicate\"}",
            minimal.dropLast(1) + ",}",
            minimal + "{}",
            "//comment\n$minimal",
            "[".repeat(10000),
            " ".repeat(ThemePack.MAX_BYTES + 1),
            minimal.replace("\"author\":\"Creator\"", "\"author\":{}")
        ).forEach { text ->
            assertThrows("Must reject $text", Exception::class.java) { ThemePack.parse(text) }
        }
        assertThrows(Exception::class.java) { ThemePack.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
        assertThrows(Exception::class.java) { ThemePack.read(ByteArrayInputStream(ByteArray(ThemePack.MAX_BYTES + 1))) }
    }

    @Test
    fun packChangesDayNightPaletteAndRestoresPreviousSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("theme", "light_theme").putString("theme_color", "red").commit()
        val previous = ThemePacks.export(context)
        ThemePacks.apply(context, ThemePack("Ocean", "WizeStream", "system", "black", "blue", "cyan"))
        for ((night, accent) in listOf(false to "blue", true to "cyan")) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), 0)
            ThemeHelper.setTheme(themed)
            assertEquals(accent, ThemeHelper.getThemeColorPreference(themed))
            assertEquals(night, ThemeHelper.isBlackThemeSelected(themed))
            assertNotNull(BottomNavigationView(themed))
            val player = PlayerUiTheme.createContext(themed)
            assertEquals(
                ThemeHelper.resolveColorFromAttr(themed, R.attr.colorPrimary),
                ThemeHelper.resolveColorFromAttr(player, R.attr.colorPrimary)
            )
        }
        ThemePacks.restore(context)
        assertEquals(previous, ThemePacks.export(context))
    }

    @Test
    fun malformedStoredPackFallsBackAndEyeComfortOverridesAccent() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(ThemePacks.KEY, "bad data").commit()
        assertNull(ThemePacks.active(context))
        prefs.edit().putInt(ThemePacks.KEY, 12).commit()
        assertNull(ThemePacks.active(context))
        ThemePacks.apply(context, ThemePack("Comfort", "Creator", "eye_comfort", darkAccent = "follow_system"))
        val themed = ContextThemeWrapper(context, 0)
        ThemeHelper.setTheme(themed)
        assertTrue(ThemeHelper.isEyeComfortSelected(themed))
        assertFalse(ThemeHelper.shouldApplyDynamicColors(themed))
        assertFalse(ThemeHelper.isFollowSystemThemeColor(themed))
        assertEquals(
            ThemeHelper.resolveColorFromAttr(themed, R.attr.colorPrimary),
            ThemeHelper.resolveColorFromAttr(PlayerUiTheme.createContext(themed), R.attr.colorPrimary)
        )
        assertNotNull(BottomNavigationView(themed))
    }

    @Test
    @Config(sdk = [28, 35])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun bundledPacksUsePublicFormat() {
        for (file in listOf("forest.json", "ocean.json", "midnight.json", "glass-record.json", "glass.json")) {
            val pack = context.assets.open("themes/$file").use(ThemePack::read)
            ThemePacks.apply(context, pack)
            assertEquals(pack, ThemePacks.export(context))
        }
    }
}
