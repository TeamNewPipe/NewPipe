/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.themes

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.ui.PlayerUiTheme
import org.schabi.newpipe.settings.AppearanceSettingsFragment
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.KEY_THEME_CHANGE
import org.schabi.newpipe.util.ThemeHelper

@RunWith(AndroidJUnit4::class)
class CustomAccentColorsDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var original: Map<String, String?>
    private var originalNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    private var originalThemeChange: Boolean? = null

    @Before
    fun savePreferences() {
        original = listOf("theme", "night_theme", "theme_color", "custom_theme_color", ThemePacks.KEY).associateWith {
            preferences.getString(it, null)
        }
        originalNightMode = AppCompatDelegate.getDefaultNightMode()
        originalThemeChange = if (preferences.contains(KEY_THEME_CHANGE)) preferences.getBoolean(KEY_THEME_CHANGE, false) else null
    }

    @After
    fun restorePreferences() {
        preferences.edit().apply {
            original.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            originalThemeChange?.let { putBoolean(KEY_THEME_CHANGE, it) } ?: remove(KEY_THEME_CHANGE)
        }.commit()
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(originalNightMode) }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun settingsAndServicePlayerUseTheSameCustomPaletteAfterRecreation() {
        for (mode in listOf("light_theme", "dark_theme", "black_theme")) {
            select(mode, "#0061A4")
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                var previousPrimary = 0
                scenario.onActivity { activity ->
                    previousPrimary = assertActivityAndPlayerPalette(activity, mode, "#0061A4", "blue")
                }
                preferences.edit().putString("custom_theme_color", "#9B4300").commit()
                scenario.recreate()
                scenario.onActivity { activity ->
                    val primary = assertActivityAndPlayerPalette(activity, mode, "#9B4300", "orange")
                    assertNotEquals("Changing the seed must change the palette", previousPrimary, primary)
                }
            }
        }
    }

    @Test
    fun editingOnlyTheHexRefreshesExistingPlayerControlsAndClosingStopsUpdates() {
        select("dark_theme", "#0061A4")
        lateinit var binding: PlayerBinding
        lateinit var controller: PlayerUiTheme
        instrumentation.runOnMainSync {
            val service = ContextThemeWrapper(context, R.style.DarkTheme)
            binding = PlayerBinding.inflate(LayoutInflater.from(PlayerUiTheme.createContext(service)))
            binding.playbackSeekBar.apply {
                max = 1000
                progress = 321
                secondaryProgress = 654
            }
            controller = PlayerUiTheme(service, binding.playbackSeekBar)
        }
        try {
            val initial = binding.playbackSeekBar.thumbTintList!!.defaultColor
            preferences.edit().putString("custom_theme_color", "#9B4300").commit()
            instrumentation.waitForIdleSync()
            var updated = 0
            instrumentation.runOnMainSync {
                updated = binding.playbackSeekBar.thumbTintList!!.defaultColor
                assertNotEquals(initial, updated)
                assertEquals(primary(expected("dark_theme", "#9B4300", "orange")), updated)
                assertEquals(321, binding.playbackSeekBar.progress)
                assertEquals(654, binding.playbackSeekBar.secondaryProgress)
                controller.close()
            }
            preferences.edit().putString("custom_theme_color", "#0061A4").commit()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertEquals(updated, binding.playbackSeekBar.thumbTintList!!.defaultColor) }
        } finally {
            instrumentation.runOnMainSync { controller.close() }
        }
    }

    @Test
    fun appearanceExplainsSeedTonesOrTheUnsupportedDeviceFallback() {
        select("black_theme", "#0061A4")
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = AppearanceSettingsFragment()
                activity.supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, fragment).commitNow()
                val summary = fragment.findPreference<Preference>("custom_theme_color")!!.summary.toString()
                val message = if (DynamicColors.isDynamicColorAvailable()) {
                    R.string.custom_theme_color_seed_summary
                } else {
                    R.string.custom_theme_color_fallback_summary
                }
                assertEquals(activity.getString(message, "#0061A4"), summary)
            }
        }
    }

    @Test
    fun importedSeedsFollowDayAndNightWithoutReplacingSavedAppearance() {
        select("light_theme", "#6750A4")
        val pack = ThemePack("Imported", "Creator", "system", "black", "#0061A4", "#9B4300", version = 3)
        ThemePacks.apply(context, ThemePack.parse(pack.encode()))
        instrumentation.runOnMainSync {
            for ((mode, seed, preset) in listOf(Triple("light_theme", "#0061A4", "blue"), Triple("black_theme", "#9B4300", "orange"))) {
                val service = base(mode)
                val player = PlayerUiTheme.createContext(service)
                assertEquals(primary(expected(mode, seed, preset)), primary(player))
                if (mode == "black_theme") assertBlackSurfaces(player)
            }
        }
        assertEquals("#6750A4", preferences.getString("custom_theme_color", null))
        assertEquals(pack, ThemePacks.export(context))
        ThemePacks.restore(context)
        assertEquals(Color.parseColor("#6750A4"), ThemeHelper.getCustomThemeColor(context))
    }

    private fun assertActivityAndPlayerPalette(activity: SettingsActivity, mode: String, seed: String, preset: String): Int {
        val expected = expected(mode, seed, preset)
        val service = ContextThemeWrapper(context, R.style.DarkTheme)
        val serviceColor = primary(service)
        val player = PlayerUiTheme.createContext(service)
        val attributes = listOf(
            R.attr.colorPrimary,
            com.google.android.material.R.attr.colorOnPrimary,
            com.google.android.material.R.attr.colorPrimaryContainer,
            com.google.android.material.R.attr.colorSecondary
        )
        for (attribute in attributes) {
            val expectedColor = ThemeHelper.resolveColorFromAttr(expected, attribute)
            assertEquals("Activity role $attribute in $mode", expectedColor, ThemeHelper.resolveColorFromAttr(activity, attribute))
            assertEquals("Player role $attribute in $mode", expectedColor, ThemeHelper.resolveColorFromAttr(player, attribute))
        }
        // Exercise the production inflater too: resolving palette colors alone does not detect
        // missing inherited AppCompat attributes in a resource-wrapped service context.
        val binding = PlayerBinding.inflate(LayoutInflater.from(player))
        assertEquals("Inflated player controls retain the seed in $mode", primary(expected), primary(binding.playbackSeekBar.context))
        assertEquals("The service's base theme must remain intact", serviceColor, primary(service))
        if (mode == "black_theme") {
            assertBlackSurfaces(activity)
            assertBlackSurfaces(player)
        }
        assertEquals("A saved seed must stay available for export", seed, ThemePacks.export(context).lightAccent)
        return primary(activity)
    }

    private fun expected(mode: String, seed: String, preset: String): Context {
        val original = base(mode)
        val expected = if (DynamicColors.isDynamicColorAvailable()) {
            val overlay = if (mode == "light_theme") {
                com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light
            } else {
                com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark
            }
            DynamicColors.wrapContextIfAvailable(
                original,
                DynamicColorsOptions.Builder().setContentBasedSource(Color.parseColor(seed)).setThemeOverlay(overlay).build()
            ).also { assertNotSame("Supported devices must install the requested palette", original, it) }
        } else {
            original.apply { theme.applyStyle(ThemeHelper.getThemeColorOverlay(preset), true) }
        }
        if (mode == "black_theme") expected.theme.applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true)
        return expected
    }

    private fun base(mode: String): ContextThemeWrapper {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (mode == "light_theme") Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
        }
        val style = when (mode) {
            "light_theme" -> R.style.LightTheme
            "black_theme" -> R.style.BlackTheme
            else -> R.style.DarkTheme
        }
        return ContextThemeWrapper(context.createConfigurationContext(config), style)
    }

    private fun select(mode: String, seed: String) {
        preferences.edit().remove(ThemePacks.KEY).putString("theme", mode)
            .putString("theme_color", "custom").putString("custom_theme_color", seed).commit()
        instrumentation.runOnMainSync { ThemeHelper.setDayNightMode(context) }
    }

    private fun primary(context: Context) = ThemeHelper.resolveColorFromAttr(context, R.attr.colorPrimary)

    private fun assertBlackSurfaces(context: Context) {
        assertEquals(Color.BLACK, ThemeHelper.resolveColorFromAttr(context, com.google.android.material.R.attr.colorSurface))
    }
}
