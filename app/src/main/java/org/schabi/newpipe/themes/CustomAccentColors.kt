/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.themes

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.appearance.CustomThemeColor
import org.schabi.newpipe.util.ThemeHelper

/** Applies the same seed palette to activities, service-owned players and theme previews. */
object CustomAccentColors {
    private const val TAG = "CustomAccentColors"
    private val fallbackAccents = listOf("wizestream", "neutral", "green", "blue", "cyan", "purple", "yellow", "orange", "pink", "red")

    fun value(context: Context): String? {
        val accent = ThemeHelper.getThemeColorPreference(context)
        val value = if (accent?.trim().equals("custom", ignoreCase = true)) {
            PreferenceManager.getDefaultSharedPreferences(context).getString(
                context.getString(R.string.custom_theme_color_key),
                context.getString(R.string.custom_theme_color_default)
            )
        } else {
            accent
        }
        return CustomThemeColor.normalize(value)
    }

    @JvmStatic
    fun seed(context: Context): Int? = value(context)?.let(CustomThemeColor::parse)

    @JvmStatic
    fun summary(context: Context, value: String?): String {
        val normalized = CustomThemeColor.normalize(value) ?: return context.getString(R.string.custom_theme_color_summary)
        return context.getString(
            if (DynamicColors.isDynamicColorAvailable()) R.string.custom_theme_color_seed_summary else R.string.custom_theme_color_fallback_summary,
            normalized
        )
    }

    /** Material's callback distinguishes a successful resource override from its silent no-op. */
    @JvmStatic
    fun apply(context: Context, seed: Int) {
        var applied = false
        if (context is Activity && DynamicColors.isDynamicColorAvailable()) {
            try {
                DynamicColors.applyToActivityIfAvailable(
                    context,
                    options(seed, ThemeHelper.isLightThemeSelected(context))
                        .setOnAppliedCallback { applied = true }
                        .build()
                )
            } catch (error: RuntimeException) {
                Log.w(TAG, "Using a native accent because the custom palette could not be applied", error)
            }
        }
        if (!applied) applyFallback(context, seed)
        keepBlackSurfaces(context, ThemeHelper.isBlackThemeSelected(context))
    }

    /** Does not mutate the service or activity supplying the inflater's base context. */
    @JvmStatic
    fun wrap(context: Context, seed: Int, light: Boolean, black: Boolean): Context {
        // A separate theme also keeps the older-Android fallback local to this player or preview.
        val base = ContextThemeWrapper(context, 0).apply { theme.setTo(context.theme) }
        val palette = if (DynamicColors.isDynamicColorAvailable()) {
            try {
                DynamicColors.wrapContextIfAvailable(base, options(seed, light).build())
            } catch (error: RuntimeException) {
                Log.w(TAG, "Using a native accent because the custom palette could not be wrapped", error)
                base
            }
        } else {
            base
        }
        if (palette === base) applyFallback(palette, seed)
        // Material's resource wrapper can lose inherited AppCompat attributes when copying the
        // base theme. Fill missing widget styles without replacing the generated color roles.
        val baseTheme = when {
            light -> R.style.LightTheme
            black -> R.style.BlackTheme
            else -> R.style.DarkTheme
        }
        palette.theme.applyStyle(baseTheme, false)
        keepBlackSurfaces(palette, black)
        return palette
    }

    private fun options(seed: Int, light: Boolean) = DynamicColorsOptions.Builder()
        .setContentBasedSource(seed)
        .setThemeOverlay(
            if (light) {
                com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light
            } else {
                com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark
            }
        )

    private fun applyFallback(context: Context, seed: Int) {
        context.theme.applyStyle(ThemeHelper.getThemeColorOverlay(fallbackAccent(context, seed)), true)
    }

    /** Match chromaticity, leaving light/dark contrast to the existing native palettes. */
    internal fun fallbackAccent(context: Context, seed: Int): String {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        val lightContext = context.createConfigurationContext(configuration)
        val source = DoubleArray(3).also { ColorUtils.colorToLAB(seed, it) }
        return fallbackAccents.minBy { accent ->
            val palette = ContextThemeWrapper(lightContext, R.style.LightTheme)
            palette.theme.applyStyle(ThemeHelper.getThemeColorOverlay(accent), true)
            val candidate = DoubleArray(3).also {
                ColorUtils.colorToLAB(ThemeHelper.resolveColorFromAttr(palette, R.attr.colorPrimary), it)
            }
            val a = source[1] - candidate[1]
            val b = source[2] - candidate[2]
            a * a + b * b
        }
    }

    private fun keepBlackSurfaces(context: Context, black: Boolean) {
        if (black) context.theme.applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true)
    }
}
