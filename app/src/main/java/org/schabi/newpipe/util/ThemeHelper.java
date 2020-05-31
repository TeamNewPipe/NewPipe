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

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.annotation.AttrRes;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

public final class ThemeHelper {
    private ThemeHelper() { }

    /**
     * Apply the selected theme (on NewPipe settings) in the context
     * with the default style (see {@link #setTheme(Context, int)}).
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
        return getSelectedThemeString(context).equals(context.getResources()
                .getString(R.string.light_theme_key));
    }


    /**
     * Create and return a wrapped context with the default selected theme set.
     *
     * @param baseContext the base context for the wrapper
     * @return a wrapped-styled context
     */
    public static Context getThemedContext(final Context baseContext) {
        return new ContextThemeWrapper(baseContext, getThemeForService(baseContext, -1));
    }

    /**
     * Return the selected theme without being styled to any service.
     * See {@link #getThemeForService(Context, int)}.
     *
     * @param context context to get the selected theme
     * @return the selected style (the default one)
     */
    @StyleRes
    public static int getDefaultTheme(final Context context) {
        return getThemeForService(context, -1);
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
        String lightTheme = context.getResources().getString(R.string.light_theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_key);
        String blackTheme = context.getResources().getString(R.string.black_theme_key);

        String selectedTheme = getSelectedThemeString(context);

        int defaultTheme = R.style.DarkTheme;
        if (selectedTheme.equals(lightTheme)) {
            defaultTheme = R.style.LightTheme;
        } else if (selectedTheme.equals(blackTheme)) {
            defaultTheme = R.style.BlackTheme;
        } else if (selectedTheme.equals(darkTheme)) {
            defaultTheme = R.style.DarkTheme;
        }

        if (serviceId <= -1) {
            return defaultTheme;
        }

        final StreamingService service;
        try {
            service = NewPipe.getService(serviceId);
        } catch (ExtractionException ignored) {
            return defaultTheme;
        }

        String themeName = "DarkTheme";
        if (selectedTheme.equals(lightTheme)) {
            themeName = "LightTheme";
        } else if (selectedTheme.equals(blackTheme)) {
            themeName = "BlackTheme";
        } else if (selectedTheme.equals(darkTheme)) {
            themeName = "DarkTheme";
        }

        themeName += "." + service.getServiceInfo().getName();
        int resourceId = context
                .getResources()
                .getIdentifier(themeName, "style", context.getPackageName());

        if (resourceId > 0) {
            return resourceId;
        }

        return defaultTheme;
    }

    @StyleRes
    public static int getSettingsThemeStyle(final Context context) {
        String lightTheme = context.getResources().getString(R.string.light_theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_key);
        String blackTheme = context.getResources().getString(R.string.black_theme_key);

        String selectedTheme = getSelectedThemeString(context);

        if (selectedTheme.equals(lightTheme)) {
            return R.style.LightSettingsTheme;
        } else if (selectedTheme.equals(blackTheme)) {
            return R.style.BlackSettingsTheme;
        } else if (selectedTheme.equals(darkTheme)) {
            return R.style.DarkSettingsTheme;
        } else {
            // Fallback
            return R.style.DarkSettingsTheme;
        }
    }

    /**
     * Get a resource id from a resource styled according to the context's theme.
     *
     * @param context Android app context
     * @param attr    attribute reference of the resource
     * @return resource ID
     */
    public static int resolveResourceIdFromAttr(final Context context, @AttrRes final int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int attributeResourceId = a.getResourceId(0, 0);
        a.recycle();
        return attributeResourceId;
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

    private static String getSelectedThemeString(final Context context) {
        String themeKey = context.getString(R.string.theme_key);
        String defaultTheme = context.getResources().getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeKey, defaultTheme);
    }
}
