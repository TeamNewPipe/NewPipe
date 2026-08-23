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
import android.content.SharedPreferences;
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
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

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
        applyThemeColor(context);
    }

    /**
     * Applies the user's Material You/manual color preference on top of the selected base theme.
     * Dynamic colors are only applied to activities, and are disabled for the black theme so the
     * user-selected pure-black background remains intact.
     */
    public static void applyThemeColor(final Context context) {
        if (shouldApplyDynamicColors(context)) {
            DynamicColors.applyToActivityIfAvailable((Activity) context);
        } else {
            applyThemeColorOverlay(context);
        }
    }

    public static boolean shouldApplyDynamicColors(final Context context) {
        return context instanceof Activity
                && isThemeColor(context, R.string.theme_color_follow_system_value)
                && !isBlackThemeSelected(context);
    }

    private static void applyThemeColorOverlay(final Context context) {
        final int overlay = getThemeColorOverlay(context);
        if (overlay != 0) {
            context.getTheme().applyStyle(overlay, true);
        }
    }

    @StyleRes
    private static int getThemeColorOverlay(final Context context) {
        if (isThemeColor(context, R.string.theme_color_pipeplay_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_PipePlay;
        } else if (isThemeColor(context, R.string.theme_color_neutral_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_Neutral;
        } else if (isThemeColor(context, R.string.theme_color_green_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_Green;
        } else if (isThemeColor(context, R.string.theme_color_blue_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_Blue;
        } else if (isThemeColor(context, R.string.theme_color_purple_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_Purple;
        } else if (isThemeColor(context, R.string.theme_color_orange_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_Orange;
        } else if (isThemeColor(context, R.string.theme_color_pink_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_Pink;
        } else if (isThemeColor(context, R.string.theme_color_red_value)) {
            return R.style.ThemeOverlay_PipePlay_ThemeColor_Red;
        }
        return 0;
    }

    private static boolean isThemeColor(final Context context, final int colorValueResId) {
        final String themeColorKey = context.getString(R.string.theme_color_key);
        final String defaultThemeColor = context.getString(R.string.default_theme_color_value);
        final String selectedThemeColor = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeColorKey, defaultThemeColor);
        return selectedThemeColor.equals(context.getString(colorValueResId));
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
     * Return true if the selected theme (on NewPipe settings) is the black theme.
     *
     * @param context context to get the preference
     * @return whether the black theme is selected
     */
    public static boolean isBlackThemeSelected(final Context context) {
        final String selectedThemeKey = getSelectedThemeKey(context);
        final Resources res = context.getResources();
        final String blackThemeKey = res.getString(R.string.black_theme_key);
        return selectedThemeKey.equals(blackThemeKey)
                || (selectedThemeKey.equals(res.getString(R.string.auto_device_theme_key))
                && isDeviceDarkThemeEnabled(context)
                && getSelectedNightThemeKey(context).equals(blackThemeKey));
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

        themeName += "." + (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_eye_protection_key), false)?
                "Collector": service.getServiceInfo().getName());
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
    public static Drawable resolveDrawable(
            @NonNull final Context context,
            @AttrRes final int attrResId
    ) {
        final TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return ContextCompat.getDrawable(context, typedValue.resourceId);
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
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(context.getString(R.string.grid_layout_enabled_key), true);
    }

    public static boolean shouldUseExperimentalNewUi(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(context.getString(R.string.use_experimental_new_ui_key), false);
    }

    public static boolean isGrid(ItemViewMode mode)  {
        return mode == ItemViewMode.GRID;
    }

    public static int getGridWidth(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width);
    }

    public static int getGridHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_height);
    }

    /**
     * Calculates the number of grid channel info items that can fit horizontally on the screen.
     *
     * @param context the context to use
     * @return the span count of grid channel info items
     */
    public static int getGridSpanCountChannels(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return getConfiguredGridColumns(context, preferences);
    }

    /**
     * Returns item view mode.
     * @param context to read preference and parse string
     * @return Returns one of ItemViewMode
     */
    public static ItemViewMode getItemViewMode(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(context.getString(R.string.card_mode_enabled_key), false)) {
            return ItemViewMode.CARD;
        }
        return shouldUseGridLayout(context) ? ItemViewMode.GRID : ItemViewMode.LIST;
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
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return getConfiguredGridColumns(context, preferences);
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

    private static int getConfiguredGridColumns(final Context context,
                                                final SharedPreferences preferences) {
        final boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final String key = landscape
                ? context.getString(R.string.grid_columns_landscape_key)
                : context.getString(R.string.grid_columns_key);
        final String defaultValue = landscape ? "4" : "2";
        final String value = preferences.getString(key, defaultValue);
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (final NumberFormatException e) {
            return Integer.parseInt(defaultValue);
        }
    }

}
