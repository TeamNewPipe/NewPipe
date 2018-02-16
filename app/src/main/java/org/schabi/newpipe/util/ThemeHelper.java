package org.schabi.newpipe.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.support.annotation.AttrRes;
import android.support.annotation.StyleRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

public class ThemeHelper {

    /**
     * Apply the selected theme (on NewPipe settings) in the context
     * with the default style (see {@link #setTheme(Context, int)}).
     *
     * @param context context that the theme will be applied
     */
    public static void setTheme(Context context) {
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
    public static void setTheme(Context context, int serviceId) {
        context.setTheme(getThemeForService(context, serviceId));
    }

    /**
     * Return true if the selected theme (on NewPipe settings) is the Light theme
     *
     * @param context context to get the preference
     */
    public static boolean isLightThemeSelected(Context context) {
        return getSelectedTheme(context).equals(context.getResources().getString(R.string.light_theme_key));
    }

    @StyleRes
    public static int getThemeForService(Context context, int serviceId) {
        String lightTheme = context.getResources().getString(R.string.light_theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_key);
        String blackTheme = context.getResources().getString(R.string.black_theme_key);

        String selectedTheme = getSelectedTheme(context);

        int defaultTheme = R.style.DarkTheme;
        if (selectedTheme.equals(lightTheme)) defaultTheme = R.style.LightTheme;
        else if (selectedTheme.equals(blackTheme)) defaultTheme = R.style.BlackTheme;
        else if (selectedTheme.equals(darkTheme)) defaultTheme = R.style.DarkTheme;

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
        if (selectedTheme.equals(lightTheme)) themeName = "LightTheme";
        else if (selectedTheme.equals(blackTheme)) themeName = "BlackTheme";
        else if (selectedTheme.equals(darkTheme)) themeName = "DarkTheme";

        themeName += "." + service.getServiceInfo().getName();
        int resourceId = context.getResources().getIdentifier(themeName, "style", context.getPackageName());

        if (resourceId > 0) {
            return resourceId;
        }

        return defaultTheme;
    }

    public static String getSelectedTheme(Context context) {
        String themeKey = context.getString(R.string.theme_key);
        String defaultTheme = context.getResources().getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context).getString(themeKey, defaultTheme);
    }

    @StyleRes
    public static int getSettingsThemeStyle(Context context) {
        String lightTheme = context.getResources().getString(R.string.light_theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_key);
        String blackTheme = context.getResources().getString(R.string.black_theme_key);

        String selectedTheme = getSelectedTheme(context);

        if (selectedTheme.equals(lightTheme)) return R.style.LightSettingsTheme;
        else if (selectedTheme.equals(blackTheme)) return R.style.BlackSettingsTheme;
        else if (selectedTheme.equals(darkTheme)) return R.style.DarkSettingsTheme;
            // Fallback
        else return R.style.DarkSettingsTheme;
    }

    /**
     * Get a resource id from a resource styled according to the the context's theme.
     */
    public static int resolveResourceIdFromAttr(Context context, @AttrRes int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int attributeResourceId = a.getResourceId(0, 0);
        a.recycle();
        return attributeResourceId;
    }
}
