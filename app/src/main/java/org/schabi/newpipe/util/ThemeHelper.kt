/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * ThemeHelper.kt is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException

object ThemeHelper {

    @JvmStatic
    fun setTheme(context: Context) {
        setTheme(context, -1)
    }

    @JvmStatic
    fun setTheme(context: Context, serviceId: Int) {
        context.setTheme(getThemeForService(context, serviceId))
    }

    @JvmStatic
    fun isLightThemeSelected(context: Context): Boolean {
        val selectedThemeKey = getSelectedThemeKey(context)
        val res = context.resources

        return selectedThemeKey == res.getString(R.string.light_theme_key) ||
                (selectedThemeKey == res.getString(R.string.auto_device_theme_key) &&
                        !isDeviceDarkThemeEnabled(context))
    }

    @StyleRes
    @JvmStatic
    fun getDialogTheme(context: Context): Int {
        return if (isLightThemeSelected(context)) R.style.LightDialogTheme else R.style.DarkDialogTheme
    }

    @StyleRes
    @JvmStatic
    fun getMinWidthDialogTheme(context: Context): Int {
        return if (isLightThemeSelected(context)) R.style.LightDialogMinWidthTheme else R.style.DarkDialogMinWidthTheme
    }

    @StyleRes
    @JvmStatic
    fun getThemeForService(context: Context, serviceId: Int): Int {
        val res = context.resources
        val lightThemeKey = res.getString(R.string.light_theme_key)
        val blackThemeKey = res.getString(R.string.black_theme_key)
        val automaticDeviceThemeKey = res.getString(R.string.auto_device_theme_key)

        val selectedThemeKey = getSelectedThemeKey(context)

        var baseTheme = R.style.DarkTheme // default to dark theme
        if (selectedThemeKey == lightThemeKey) {
            baseTheme = R.style.LightTheme
        } else if (selectedThemeKey == blackThemeKey) {
            baseTheme = R.style.BlackTheme
        } else if (selectedThemeKey == automaticDeviceThemeKey) {
            if (isDeviceDarkThemeEnabled(context)) {
                // use the dark theme variant preferred by the user
                val selectedNightThemeKey = getSelectedNightThemeKey(context)
                baseTheme = if (selectedNightThemeKey == blackThemeKey) {
                    R.style.BlackTheme
                } else {
                    R.style.DarkTheme
                }
            } else {
                // there is only one day theme
                baseTheme = R.style.LightTheme
            }
        }

        if (serviceId <= -1) {
            return baseTheme
        }

        val service = try {
            NewPipe.getService(serviceId)
        } catch (ignored: ExtractionException) {
            return baseTheme
        }

        var themeName = when (baseTheme) {
            R.style.LightTheme -> "LightTheme"
            R.style.BlackTheme -> "BlackTheme"
            else -> "DarkTheme"
        }

        themeName += "." + service.serviceInfo.name
        val resourceId = getThemeOrDefault(themeName, baseTheme)

        return if (resourceId > 0) resourceId else baseTheme
    }

    @StyleRes
    @JvmStatic
    fun getSettingsThemeStyle(context: Context): Int {
        val res = context.resources
        val lightTheme = res.getString(R.string.light_theme_key)
        val blackTheme = res.getString(R.string.black_theme_key)
        val automaticDeviceTheme = res.getString(R.string.auto_device_theme_key)

        val selectedTheme = getSelectedThemeKey(context)

        return when (selectedTheme) {
            lightTheme -> R.style.LightSettingsTheme
            blackTheme -> R.style.BlackSettingsTheme
            automaticDeviceTheme -> {
                if (isDeviceDarkThemeEnabled(context)) {
                    val selectedNightTheme = getSelectedNightThemeKey(context)
                    if (selectedNightTheme == blackTheme) {
                        R.style.BlackSettingsTheme
                    } else {
                        R.style.DarkSettingsTheme
                    }
                } else {
                    R.style.LightSettingsTheme
                }
            }
            else -> R.style.DarkSettingsTheme
        }
    }

    @JvmStatic
    fun resolveColorFromAttr(context: Context, @AttrRes attrColor: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attrColor, value, true)

        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else {
            value.data
        }
    }

    @JvmStatic
    fun resolveDrawable(context: Context, @AttrRes attrResId: Int): Drawable? {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrResId, typedValue, true)
        return AppCompatResources.getDrawable(context, typedValue.resourceId)
    }

    @JvmStatic
    fun getAndroidDimenPx(context: Context, name: String): Int {
        val resId = context.resources.getIdentifier(name, "dimen", "android")
        return if (resId <= 0) {
            0
        } else {
            context.resources.getDimensionPixelSize(resId)
        }
    }

    private fun getSelectedThemeKey(context: Context): String {
        val themeKey = context.getString(R.string.theme_key)
        val defaultTheme = context.getString(R.string.default_theme_value)
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(themeKey, defaultTheme) ?: defaultTheme
    }

    private fun getSelectedNightThemeKey(context: Context): String {
        val nightThemeKey = context.getString(R.string.night_theme_key)
        val defaultNightTheme = context.resources.getString(R.string.default_night_theme_value)
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(nightThemeKey, defaultNightTheme) ?: defaultNightTheme
    }

    @JvmStatic
    fun setTitleToAppCompatActivity(activity: Activity?, title: CharSequence) {
        if (activity is AppCompatActivity) {
            val actionBar = activity.supportActionBar
            actionBar?.title = title
        }
    }

    @JvmStatic
    fun isDeviceDarkThemeEnabled(context: Context): Boolean {
        val deviceTheme = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return when (deviceTheme) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    @JvmStatic
    fun setDayNightMode(context: Context) {
        setDayNightMode(context, getSelectedThemeKey(context))
    }

    @JvmStatic
    fun setDayNightMode(context: Context, selectedThemeKey: String) {
        val res = context.resources

        when (selectedThemeKey) {
            res.getString(R.string.light_theme_key) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            res.getString(R.string.dark_theme_key), res.getString(R.string.black_theme_key) ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    @JvmStatic
    fun shouldUseGridLayout(context: Context): Boolean {
        val listMode = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(
                context.getString(R.string.list_view_mode_key),
                context.getString(R.string.list_view_mode_value)
            ) ?: context.getString(R.string.list_view_mode_value)

        return when (listMode) {
            context.getString(R.string.list_view_mode_grid_key) -> true
            context.getString(R.string.list_view_mode_list_key) -> false
            context.getString(R.string.list_view_mode_card_key) -> false
            else -> {
                val configuration = context.resources.configuration
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)
            }
        }
    }

    @JvmStatic
    fun getGridSpanCountChannels(context: Context): Int {
        return getGridSpanCount(context, context.resources.getDimensionPixelSize(R.dimen.channel_item_grid_min_width))
    }

    @JvmStatic
    fun getGridSpanCountStreams(context: Context): Int {
        val res = context.resources
        return getGridSpanCount(
            context,
            res.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width) +
                    res.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2
        )
    }

    @JvmStatic
    fun getGridSpanCount(context: Context, minWidth: Int): Int {
        return (context.resources.displayMetrics.widthPixels / minWidth).coerceAtLeast(1)
    }

    @StyleRes
    private fun getThemeOrDefault(name: String, @StyleRes baseTheme: Int): Int {
        return when (name) {
            "LightTheme.YouTube" -> R.style.LightTheme_YouTube
            "DarkTheme.YouTube" -> R.style.DarkTheme_YouTube
            "BlackTheme.YouTube" -> R.style.BlackTheme_YouTube
            "LightTheme.SoundCloud" -> R.style.LightTheme_SoundCloud
            "DarkTheme.SoundCloud" -> R.style.DarkTheme_SoundCloud
            "BlackTheme.SoundCloud" -> R.style.BlackTheme_SoundCloud
            "LightTheme.PeerTube" -> R.style.LightTheme_PeerTube
            "DarkTheme.PeerTube" -> R.style.DarkTheme_PeerTube
            "BlackTheme.PeerTube" -> R.style.BlackTheme_PeerTube
            "LightTheme.media.ccc.de" -> R.style.LightTheme_media_ccc_de
            "DarkTheme.media.ccc.de" -> R.style.DarkTheme_media_ccc_de
            "BlackTheme.media.ccc.de" -> R.style.BlackTheme_media_ccc_de
            "LightTheme.Bandcamp" -> R.style.LightTheme_Bandcamp
            "DarkTheme.Bandcamp" -> R.style.DarkTheme_Bandcamp
            "BlackTheme.Bandcamp" -> R.style.BlackTheme_Bandcamp
            else -> baseTheme
        }
    }
}