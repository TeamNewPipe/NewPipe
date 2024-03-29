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

package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.info_list.ItemViewMode;

public final class ThemeHelper {
    private ThemeHelper() {
    }

    /**
     * Apply the selected theme (on NewPipe settings) in the context
     * with the default style (see {@link #setTheme(Context, int)}).
     *
     * ThemeHelper.setDayNightMode should be called before
     * the applying theme for the first time in session
     *
     * @param context context that the theme will be applied
     */
    public static void setTheme(final Context context) {
        setTheme(context, -1);
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
     *                  pass -1 to get the default style
     */
    public static void setTheme(final Context context, final int serviceId) {
        context.setTheme(getThemeForService(context, serviceId));
    }

    /**
     * Return true if the selected theme (on NewPipe settings) is the Light theme.
     *
     * @param context context to get the preference
     * @return whether the light theme is selected
     */
    public static boolean isLightThemeSelected(final Context context) {
        final String selectedThemeKey = getSelectedThemeKey(context);
        final Resources res = context.getResources();

        return selectedThemeKey.equals(res.getString(R.string.light_theme_key))
                || (selectedThemeKey.equals(res.getString(R.string.auto_device_theme_key))
                && !isDeviceDarkThemeEnabled(context));
    }

    /**
     * Return a dialog theme styled according to the (default) selected theme.
     *
     * @param context context to get the selected theme
     * @return the dialog style (the default one)
     */
    @StyleRes
    public static int getDialogTheme(final Context context) {
        return isLightThemeSelected(context) ? R.style.LightDialogTheme : R.style.DarkDialogTheme;
    }

    /**
     * Return a min-width dialog theme styled according to the (default) selected theme.
     *
     * @param context context to get the selected theme
     * @return the dialog style (the default one)
     */
    @StyleRes
    public static int getMinWidthDialogTheme(final Context context) {
        return isLightThemeSelected(context) ? R.style.LightDialogMinWidthTheme
                : R.style.DarkDialogMinWidthTheme;
    }

    /**
     * Return the selected theme styled according to the serviceId.
     *
     * @param context   context to get the selected theme
     * @param serviceId return a theme styled to this service,
     *                  -1 to get the default
     * @return the selected style (styled)
     */
    @StyleRes
    public static int getThemeForService(final Context context, final int serviceId) {
        final Resources res = context.getResources();
        final String lightThemeKey = res.getString(R.string.light_theme_key);
        final String blackThemeKey = res.getString(R.string.black_theme_key);
        final String automaticDeviceThemeKey = res.getString(R.string.auto_device_theme_key);

        final String selectedThemeKey = getSelectedThemeKey(context);


        int baseTheme = R.style.DarkTheme; // default to dark theme
        if (selectedThemeKey.equals(lightThemeKey)) {
            baseTheme = R.style.LightTheme;
        } else if (selectedThemeKey.equals(blackThemeKey)) {
            baseTheme = R.style.BlackTheme;
        } else if (selectedThemeKey.equals(automaticDeviceThemeKey)) {

            if (isDeviceDarkThemeEnabled(context)) {
                // use the dark theme variant preferred by the user
                final String selectedNightThemeKey = getSelectedNightThemeKey(context);
                if (selectedNightThemeKey.equals(blackThemeKey)) {
                    baseTheme = R.style.BlackTheme;
                } else {
                    baseTheme = R.style.DarkTheme;
                }
            } else {
                // there is only one day theme
                baseTheme = R.style.LightTheme;
            }
        }

        if (serviceId <= -1) {
            return baseTheme;
        }

        final StreamingService service;
        try {
            service = NewPipe.getService(serviceId);
        } catch (final ExtractionException ignored) {
            return baseTheme;
        }

        String themeName = "DarkTheme"; // default
        if (baseTheme == R.style.LightTheme) {
            themeName = "LightTheme";
        } else if (baseTheme == R.style.BlackTheme) {
            themeName = "BlackTheme";
        }

        themeName += "." + service.getServiceInfo().getName();
        final int resourceId = context.getResources()
                .getIdentifier(themeName, "style", context.getPackageName());

        if (resourceId > 0) {
            return resourceId;
        }
        return baseTheme;
    }

    @StyleRes
    public static int getSettingsThemeStyle(final Context context) {
        final Resources res = context.getResources();
        final String lightTheme = res.getString(R.string.light_theme_key);
        final String blackTheme = res.getString(R.string.black_theme_key);
        final String automaticDeviceTheme = res.getString(R.string.auto_device_theme_key);


        final String selectedTheme = getSelectedThemeKey(context);

        if (selectedTheme.equals(lightTheme)) {
            return R.style.LightSettingsTheme;
        } else if (selectedTheme.equals(blackTheme)) {
            return R.style.BlackSettingsTheme;
        } else if (selectedTheme.equals(automaticDeviceTheme)) {
            if (isDeviceDarkThemeEnabled(context)) {
                // use the dark theme variant preferred by the user
                final String selectedNightTheme = getSelectedNightThemeKey(context);
                if (selectedNightTheme.equals(blackTheme)) {
                    return R.style.BlackSettingsTheme;
                } else {
                    return R.style.DarkSettingsTheme;
                }
            } else {
                // there is only one day theme
                return R.style.LightSettingsTheme;
            }
        } else {
            // default to dark theme
            return R.style.DarkSettingsTheme;
        }
    }

    /**
     * Get a color from an attr styled according to the context's theme.
     *
     * @param context   Android app context
     * @param attrColor attribute reference of the resource
     * @return the color
     */
    public static int resolveColorFromAttr(final Context context, @AttrRes final int attrColor) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attrColor, value, true);

        if (value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }

        return value.data;
    }

    /**
     * Resolves a {@link Drawable} by it's id.
     *
     * @param context   Context
     * @param attrResId Resource id
     * @return the {@link Drawable}
     */
    public static Drawable resolveDrawable(@NonNull final Context context,
                                           @AttrRes final int attrResId) {
        final TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return AppCompatResources.getDrawable(context, typedValue.resourceId);
    }

    /**
     * Gets a runtime dimen from the {@code android} package. Should be used for dimens for which
     * normal accessing with {@code R.dimen.} is not available.
     *
     * @param context context
     * @param name    dimen resource name (e.g. navigation_bar_height)
     * @return the obtained dimension, in pixels, or 0 if the resource could not be resolved
     */
    public static int getAndroidDimenPx(@NonNull final Context context, final String name) {
        final int resId = context.getResources().getIdentifier(name, "dimen", "android");
        if (resId <= 0) {
            return 0;
        }
        return context.getResources().getDimensionPixelSize(resId);
    }

    private static String getSelectedThemeKey(final Context context) {
        final String themeKey = context.getString(R.string.theme_key);
        final String defaultTheme = context.getResources().getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeKey, defaultTheme);
    }

    private static String getSelectedNightThemeKey(final Context context) {
        final String nightThemeKey = context.getString(R.string.night_theme_key);
        final String defaultNightTheme = context.getResources()
                .getString(R.string.default_night_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(nightThemeKey, defaultNightTheme);
    }

    /**
     * Sets the title to the activity, if the activity is an {@link AppCompatActivity} and has an
     * action bar.
     *
     * @param activity the activity to set the title of
     * @param title    the title to set to the activity
     */
    public static void setTitleToAppCompatActivity(@Nullable final Activity activity,
                                                   final CharSequence title) {
        if (activity instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
            }
        }
    }

    /**
     * Get the device theme
     * <p>
     * It will return true if the device 's theme is dark, false otherwise.
     * <p>
     * From https://developer.android.com/guide/topics/ui/look-and-feel/darktheme#java
     *
     * @param context the context to use
     * @return true:dark theme, false:light or unknown
     */
    public static boolean isDeviceDarkThemeEnabled(final Context context) {
        final int deviceTheme = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        switch (deviceTheme) {
            case Configuration.UI_MODE_NIGHT_YES:
                return true;
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
            case Configuration.UI_MODE_NIGHT_NO:
            default:
                return false;
        }
    }

    public static void setDayNightMode(final Context context) {
        setDayNightMode(context, ThemeHelper.getSelectedThemeKey(context));
    }

    public static void setDayNightMode(final Context context, final String selectedThemeKey) {
        final Resources res = context.getResources();

        if (selectedThemeKey.equals(res.getString(R.string.light_theme_key))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (selectedThemeKey.equals(res.getString(R.string.dark_theme_key))
                || selectedThemeKey.equals(res.getString(R.string.black_theme_key))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    /**
     * Returns whether the grid layout or the list layout should be used. If the user set "auto"
     * mode in settings, decides based on screen orientation (landscape) and size.
     *
     * @param context the context to use
     * @return true:use grid layout, false:use list layout
     */
    public static boolean shouldUseGridLayout(final Context context) {
        final ItemViewMode mode = getItemViewMode(context);
        return mode == ItemViewMode.GRID;
    }

    /**
     * Calculates the number of grid channel info items that can fit horizontally on the screen.
     *
     * @param context the context to use
     * @return the span count of grid channel info items
     */
    public static int getGridSpanCountChannels(final Context context) {
        return getGridSpanCount(context,
                context.getResources().getDimensionPixelSize(R.dimen.channel_item_grid_min_width));
    }

    /**
     * Returns item view mode.
     * @param context to read preference and parse string
     * @return Returns one of ItemViewMode
     */
    public static ItemViewMode getItemViewMode(final Context context) {
        final String listMode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.list_view_mode_key),
                        context.getString(R.string.list_view_mode_value));
        final ItemViewMode result;
        if (listMode.equals(context.getString(R.string.list_view_mode_list_key))) {
            result = ItemViewMode.LIST;
        } else if (listMode.equals(context.getString(R.string.list_view_mode_grid_key))) {
            result = ItemViewMode.GRID;
        } else if (listMode.equals(context.getString(R.string.list_view_mode_card_key))) {
            result = ItemViewMode.CARD;
        } else {
            // Auto mode - evaluate whether to use Grid based on screen real estate.
            final Configuration configuration = context.getResources().getConfiguration();
            final boolean useGrid = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    && configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE);
            if (useGrid) {
                result = ItemViewMode.GRID;
            } else {
                result = ItemViewMode.LIST;
            }
        }
        return result;
    }

    /**
     * Calculates the number of grid stream info items that can fit horizontally on the screen. The
     * width of a grid stream info item is obtained from the thumbnail width plus the right and left
     * paddings.
     *
     * @param context the context to use
     * @return the span count of grid stream info items
     */
    public static int getGridSpanCountStreams(final Context context) {
        final Resources res = context.getResources();
        return getGridSpanCount(context,
                res.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
                        + res.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2);
    }

    /**
     * Calculates the number of grid items that can fit horizontally on the screen based on the
     * minimum width.
     *
     * @param context the context to use
     * @param minWidth the minimum width of items in the grid
     * @return the span count of grid list items
     */
    public static int getGridSpanCount(final Context context, final int minWidth) {
        return Math.max(1, context.getResources().getDisplayMetrics().widthPixels / minWidth);
    }
}
