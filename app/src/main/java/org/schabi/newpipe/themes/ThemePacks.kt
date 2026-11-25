package org.schabi.newpipe.themes

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.util.KEY_THEME_CHANGE
import org.schabi.newpipe.util.ThemeHelper

object ThemePacks {
    const val KEY = "active_theme_pack_v1"

    private var cachedText: String? = null
    private var cachedPack: ThemePack? = null

    @JvmStatic
    @Synchronized
    fun active(context: Context): ThemePack? {
        val text = try {
            PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, null)
        } catch (_: ClassCastException) {
            null
        }
        if (text != cachedText) {
            cachedText = text
            cachedPack = try {
                text?.let(ThemePack::parse)
            } catch (_: Exception) {
                // Invalid restored preferences must never prevent opening Appearance to recover.
                null
            }
        }
        return cachedPack
    }

    @JvmStatic
    fun themeKey(context: Context): String? = active(context)?.let { modeKey(it.mode) }

    @JvmStatic
    fun nightKey(context: Context): String? = active(context)?.let { modeKey(it.night) }

    @JvmStatic
    fun accent(context: Context): String? = active(context)?.let {
        if (ThemeHelper.isLightThemeSelected(context)) it.lightAccent else it.darkAccent
    }

    fun modeKey(mode: String): String = when (mode) {
        "system" -> "auto_device_theme"
        "eye_comfort" -> "eye_comfort"
        else -> "${mode}_theme"
    }

    fun apply(context: Context, pack: ThemePack) {
        // Validate again at the persistence boundary, including built-in recipes.
        val encoded = ThemePack.parse(pack.encode()).encode()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY, encoded).putBoolean(KEY_THEME_CHANGE, true).apply()
    }

    fun restore(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(KEY).putBoolean(KEY_THEME_CHANGE, true).apply()
    }

    fun export(context: Context): ThemePack {
        active(context)?.let { return it }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mode = prefs.getString(context.getString(R.string.theme_key), "auto_device_theme")!!
        val night = prefs.getString(context.getString(R.string.night_theme_key), "dark_theme")!!
        val accent = ThemeHelper.getThemeColorPreference(context)
        val customSeed = CustomAccentColors.value(context)
        return ThemePack(
            "My WizeStream theme",
            "Theme creator",
            if (mode == "auto_device_theme") "system" else mode.removeSuffix("_theme"),
            night.removeSuffix("_theme"),
            customSeed ?: accent,
            customSeed ?: accent,
            version = if (customSeed == null) 1 else 3
        ).let { ThemePack.parse(it.encode()) }
    }
}
