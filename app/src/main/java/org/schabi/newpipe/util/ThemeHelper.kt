/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * ThemeHelper.java is part of NewPipe
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
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.info_list.ItemViewMode
import kotlin.math.max

object ThemeHelper {
    /**
     * Apply the selected theme (on NewPipe settings) in the context
     * with the default style (see [.setTheme]).
     *
     * ThemeHelper.setDayNightMode should be called before
     * the applying theme for the first time in session
     *
     * @param context context that the theme will be applied
     */
    fun setTheme(context: Context) {
        setTheme(context, -1)
    }

    /**
     * Apply the selected theme (on NewPipe settings) in the context,
     * themed according with the styles defined for the service .
     *
     * ThemeHelper.setDayNightMode should be called before
     * the applying theme for the first time in session
     *
     * @param context   context that the theme will be applied
     * @param serviceId the theme will be styled to the service with this id,
     * pass -1 to get the default style
     */
    fun setTheme(context: Context, serviceId: Int) {
        context.setTheme(getThemeForService(context, serviceId))
    }

    /**
     * Return true if the selected theme (on NewPipe settings) is the Light theme.
     *
     * @param context context to get the preference
     * @return whether the light theme is selected
     */
    fun isLightThemeSelected(context: Context?): Boolean {
        val selectedThemeKey = getSelectedThemeKey(context)
        val res = context!!.resources
        return selectedThemeKey == res.getString(R.string.light_theme_key) || selectedThemeKey == res.getString(R.string.auto_device_theme_key) && !isDeviceDarkThemeEnabled(context)
    }

    /**
     * Return a dialog theme styled according to the (default) selected theme.
     *
     * @param context context to get the selected theme
     * @return the dialog style (the default one)
     */
    @StyleRes
    fun getDialogTheme(context: Context?): Int {
        return if (isLightThemeSelected(context)) R.style.LightDialogTheme else R.style.DarkDialogTheme
    }

    /**
     * Return a min-width dialog theme styled according to the (default) selected theme.
     *
     * @param context context to get the selected theme
     * @return the dialog style (the default one)
     */
    @StyleRes
    fun getMinWidthDialogTheme(context: Context?): Int {
        return if (isLightThemeSelected(context)) R.style.LightDialogMinWidthTheme else R.style.DarkDialogMinWidthTheme
    }

    /**
     * Return the selected theme styled according to the serviceId.
     *
     * @param context   context to get the selected theme
     * @param serviceId return a theme styled to this service,
     * -1 to get the default
     * @return the selected style (styled)
     */
    @StyleRes
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
            baseTheme = if (isDeviceDarkThemeEnabled(context)) {
                // use the dark theme variant preferred by the user
                val selectedNightThemeKey = getSelectedNightThemeKey(context)
                if (selectedNightThemeKey == blackThemeKey) {
                    R.style.BlackTheme
                } else {
                    R.style.DarkTheme
                }
            } else {
                // there is only one day theme
                R.style.LightTheme
            }
        }
        if (serviceId <= -1) {
            return baseTheme
        }
        val service: StreamingService
        service = try {
            NewPipe.getService(serviceId)
        } catch (ignored: ExtractionException) {
            return baseTheme
        }
        var themeName = "DarkTheme" // default
        if (baseTheme == R.style.LightTheme) {
            themeName = "LightTheme"
        } else if (baseTheme == R.style.BlackTheme) {
            themeName = "BlackTheme"
        }
        themeName += "." + service.serviceInfo.name
        val resourceId = context.resources
                .getIdentifier(themeName, "style", context.packageName)
        return if (resourceId > 0) {
            resourceId
        } else baseTheme
    }

    @StyleRes
    fun getSettingsThemeStyle(context: Context): Int {
        val res = context.resources
        val lightTheme = res.getString(R.string.light_theme_key)
        val blackTheme = res.getString(R.string.black_theme_key)
        val automaticDeviceTheme = res.getString(R.string.auto_device_theme_key)
        val selectedTheme = getSelectedThemeKey(context)
        return if (selectedTheme == lightTheme) {
            R.style.LightSettingsTheme
        } else if (selectedTheme == blackTheme) {
            R.style.BlackSettingsTheme
        } else if (selectedTheme == automaticDeviceTheme) {
            if (isDeviceDarkThemeEnabled(context)) {
                // use the dark theme variant preferred by the user
                val selectedNightTheme = getSelectedNightThemeKey(context)
                if (selectedNightTheme == blackTheme) {
                    R.style.BlackSettingsTheme
                } else {
                    R.style.DarkSettingsTheme
                }
            } else {
                // there is only one day theme
                R.style.LightSettingsTheme
            }
        } else {
            // default to dark theme
            R.style.DarkSettingsTheme
        }
    }

    /**
     * Get a color from an attr styled according to the context's theme.
     *
     * @param context   Android app context
     * @param attrColor attribute reference of the resource
     * @return the color
     */
    fun resolveColorFromAttr(context: Context, @AttrRes attrColor: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attrColor, value, true)
        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else value.data
    }

    /**
     * Resolves a [Drawable] by it's id.
     *
     * @param context   Context
     * @param attrResId Resource id
     * @return the [Drawable]
     */
    fun resolveDrawable(context: Context,
                        @AttrRes attrResId: Int): Drawable? {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrResId, typedValue, true)
        return AppCompatResources.getDrawable(context, typedValue.resourceId)
    }

    /**
     * Gets a runtime dimen from the `android` package. Should be used for dimens for which
     * normal accessing with `R.dimen.` is not available.
     *
     * @param context context
     * @param name    dimen resource name (e.g. navigation_bar_height)
     * @return the obtained dimension, in pixels, or 0 if the resource could not be resolved
     */
    fun getAndroidDimenPx(context: Context, name: String?): Int {
        val resId = context.resources.getIdentifier(name, "dimen", "android")
        return if (resId <= 0) {
            0
        } else context.resources.getDimensionPixelSize(resId)
    }

    private fun getSelectedThemeKey(context: Context?): String? {
        val themeKey = context!!.getString(R.string.theme_key)
        val defaultTheme = context.resources.getString(R.string.default_theme_value)
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeKey, defaultTheme)
    }

    private fun getSelectedNightThemeKey(context: Context): String? {
        val nightThemeKey = context.getString(R.string.night_theme_key)
        val defaultNightTheme = context.resources
                .getString(R.string.default_night_theme_value)
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(nightThemeKey, defaultNightTheme)
    }

    /**
     * Sets the title to the activity, if the activity is an [AppCompatActivity] and has an
     * action bar.
     *
     * @param activity the activity to set the title of
     * @param title    the title to set to the activity
     */
    fun setTitleToAppCompatActivity(activity: Activity?,
                                    title: CharSequence?) {
        if (activity is AppCompatActivity) {
            val actionBar = activity.supportActionBar
            actionBar?.setTitle(title)
        }
    }

    /**
     * Get the device theme
     *
     *
     * It will return true if the device 's theme is dark, false otherwise.
     *
     *
     * From https://developer.android.com/guide/topics/ui/look-and-feel/darktheme#java
     *
     * @param context the context to use
     * @return true:dark theme, false:light or unknown
     */
    fun isDeviceDarkThemeEnabled(context: Context?): Boolean {
        val deviceTheme = (context!!.resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK)
        return when (deviceTheme) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_UNDEFINED, Configuration.UI_MODE_NIGHT_NO -> false
            else -> false
        }
    }

    fun setDayNightMode(context: Context) {
        setDayNightMode(context, getSelectedThemeKey(context))
    }

    fun setDayNightMode(context: Context, selectedThemeKey: String?) {
        val res = context.resources
        if (selectedThemeKey == res.getString(R.string.light_theme_key)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else if (selectedThemeKey == res.getString(R.string.dark_theme_key) || selectedThemeKey == res.getString(R.string.black_theme_key)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /**
     * Returns whether the grid layout or the list layout should be used. If the user set "auto"
     * mode in settings, decides based on screen orientation (landscape) and size.
     *
     * @param context the context to use
     * @return true:use grid layout, false:use list layout
     */
    fun shouldUseGridLayout(context: Context): Boolean {
        val mode = getItemViewMode(context)
        return mode == ItemViewMode.GRID
    }

    /**
     * Calculates the number of grid channel info items that can fit horizontally on the screen.
     *
     * @param context the context to use
     * @return the span count of grid channel info items
     */
    fun getGridSpanCountChannels(context: Context): Int {
        return getGridSpanCount(context,
                context.resources.getDimensionPixelSize(R.dimen.channel_item_grid_min_width))
    }

    /**
     * Returns item view mode.
     * @param context to read preference and parse string
     * @return Returns one of ItemViewMode
     */
    fun getItemViewMode(context: Context): ItemViewMode {
        val listMode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.list_view_mode_key),
                        context.getString(R.string.list_view_mode_value))
        val result: ItemViewMode
        result = if (listMode == context.getString(R.string.list_view_mode_list_key)) {
            ItemViewMode.LIST
        } else if (listMode == context.getString(R.string.list_view_mode_grid_key)) {
            ItemViewMode.GRID
        } else if (listMode == context.getString(R.string.list_view_mode_card_key)) {
            ItemViewMode.CARD
        } else {
            // Auto mode - evaluate whether to use Grid based on screen real estate.
            val configuration = context.resources.configuration
            val useGrid = (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    && configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE))
            if (useGrid) {
                ItemViewMode.GRID
            } else {
                ItemViewMode.LIST
            }
        }
        return result
    }

    /**
     * Calculates the number of grid stream info items that can fit horizontally on the screen. The
     * width of a grid stream info item is obtained from the thumbnail width plus the right and left
     * paddings.
     *
     * @param context the context to use
     * @return the span count of grid stream info items
     */
    fun getGridSpanCountStreams(context: Context): Int {
        val res = context.resources
        return getGridSpanCount(context, res.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
                + res.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2)
    }

    /**
     * Calculates the number of grid items that can fit horizontally on the screen based on the
     * minimum width.
     *
     * @param context the context to use
     * @param minWidth the minimum width of items in the grid
     * @return the span count of grid list items
     */
    fun getGridSpanCount(context: Context, minWidth: Int): Int {
        return max(1.0, (context.resources.displayMetrics.widthPixels / minWidth).toDouble()).toInt()
    }
}
