/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.themes

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.player.ui.PlayerUiTheme
import org.schabi.newpipe.util.ThemeHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 35])
class CustomAccentThemePackTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun exportCarriesTheActualSeedAndImportDoesNotUseOrOverwriteTheRecipientsSeed() {
        preferences.edit().putString("theme", "light_theme").putString("theme_color", "custom")
            .putString("custom_theme_color", "#0061a4").commit()
        val exported = ThemePacks.export(context)
        assertEquals(3, exported.version)
        assertEquals("#0061A4", exported.lightAccent)
        assertEquals("#0061A4", exported.darkAccent)
        assertEquals(exported, ThemePack.parse(exported.encode()))

        preferences.edit().putString("custom_theme_color", "#9B4300").commit()
        ThemePacks.apply(context, ThemePack.parse(exported.encode()))
        assertEquals(Color.parseColor("#0061A4"), ThemeHelper.getCustomThemeColor(context))
        assertEquals("#9B4300", preferences.getString("custom_theme_color", null))
        assertEquals(exported, ThemePacks.export(context))
        ThemePacks.restore(context)
        assertEquals(Color.parseColor("#9B4300"), ThemeHelper.getCustomThemeColor(context))
        assertEquals("#9B4300", ThemePacks.export(context).lightAccent)
    }

    @Test
    fun versionThreeSelectsItsOwnDayAndNightSeeds() {
        ThemePacks.apply(context, ThemePack("Two seeds", "Creator", "system", "black", "#0061A4", "#9B4300", version = 3))
        for ((night, seed) in listOf(false to "#0061A4", true to "#9B4300")) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val configured = context.createConfigurationContext(config)
            assertEquals(Color.parseColor(seed), ThemeHelper.getCustomThemeColor(configured))
            assertEquals(night, ThemeHelper.isBlackThemeSelected(configured))
        }
    }

    @Test
    fun namedVersionOneAndTwoRecipesKeepTheirVersionAndBehavior() {
        for (version in listOf(1, 2)) {
            val pack = ThemePack("Named", "Creator", lightAccent = "blue", darkAccent = "orange", version = version)
            val imported = ThemePack.parse(pack.encode())
            assertEquals(pack, imported)
            ThemePacks.apply(context, imported)
            assertEquals(pack, ThemePacks.export(context))
            ThemePacks.restore(context)
        }
        preferences.edit().putString("theme_color", "purple").commit()
        assertEquals(1, ThemePacks.export(context).version)
        assertEquals("purple", ThemePacks.export(context).lightAccent)
    }

    @Test
    fun rejectsHexInOldFormatsAndRejectsMalformedOrPreferenceOnlyValues() {
        val valid = ThemePack("Custom", "Creator", lightAccent = "#0061A4", version = 3).encode()
        for (version in listOf(1, 2)) {
            assertThrows(Exception::class.java) {
                ThemePack.parse(JSONObject(valid).put("version", version).toString())
            }
        }
        for (value in listOf("custom", "#123", "#00123456", "123456", "#GGGGGG", " #123456", "#123456\n")) {
            assertThrows("Must reject $value", Exception::class.java) {
                ThemePack.parse(JSONObject(valid).put("lightAccent", value).toString())
            }
        }
    }

    @Test
    fun versionThreeRetainsVersionTwoExtensionsWithoutChangingTheirMeaning() {
        val pack = ThemePack(
            "Custom glass",
            "Creator",
            lightAccent = "#0061A4",
            darkAccent = "#9B4300",
            version = 3,
            glass = ThemeGlass(blur = 4),
            player = ThemePlayer(layout = "vinyl")
        )
        assertEquals(pack, ThemePack.parse(pack.encode()))
    }

    @Test
    @Config(sdk = [23, 28])
    fun unsupportedAndroidUsesTheMatchingNativePaletteInEveryModeWithoutChangingTheSeed() {
        for (mode in listOf("light_theme", "dark_theme", "black_theme")) {
            for ((seed, preset) in listOf("#0061A4" to "blue", "#9B4300" to "orange", "#5F5F5F" to "neutral")) {
                preferences.edit().putString("theme", mode).putString("theme_color", "custom").putString("custom_theme_color", seed).commit()
                val config = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (mode == "light_theme") Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
                }
                val configured = context.createConfigurationContext(config)
                val expected = ContextThemeWrapper(configured, ThemeHelper.getThemeForService(configured, -1)).apply {
                    theme.applyStyle(ThemeHelper.getThemeColorOverlay(preset), true)
                }
                val actual = ContextThemeWrapper(configured, 0).also(ThemeHelper::setTheme)
                val player = PlayerUiTheme.createContext(configured)
                assertEquals(ThemeHelper.resolveColorFromAttr(expected, R.attr.colorPrimary), ThemeHelper.resolveColorFromAttr(actual, R.attr.colorPrimary))
                assertEquals(ThemeHelper.resolveColorFromAttr(expected, R.attr.colorPrimary), ThemeHelper.resolveColorFromAttr(player, R.attr.colorPrimary))
                assertEquals(seed, ThemePacks.export(context).lightAccent)
            }
        }
    }

    @Test
    fun eyeComfortStillOverridesCustomSeeds() {
        preferences.edit().putString("theme", "eye_comfort").putString("theme_color", "custom").putString("custom_theme_color", "#0061A4").commit()
        val expected = ContextThemeWrapper(context, R.style.EyeComfortTheme)
        val player = PlayerUiTheme.createContext(context)
        assertEquals(ThemeHelper.resolveColorFromAttr(expected, R.attr.colorPrimary), ThemeHelper.resolveColorFromAttr(player, R.attr.colorPrimary))
        assertFalse(ThemeHelper.shouldApplyDynamicColors(context))
    }
}
