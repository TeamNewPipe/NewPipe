package org.schabi.newpipe.util;

import android.content.Context;
import android.preference.PreferenceManager;

import org.schabi.newpipe.R;

public class ThemeHelper {

    /**
     * Apply the selected theme (on NewPipe settings) in the context
     *
     * @param context context that the theme will be applied
     */
    public static void setTheme(Context context) {
        String lightTheme = context.getResources().getString(R.string.light_theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_key);
        String blackTheme = context.getResources().getString(R.string.black_theme_key);

        String selectedTheme = getSelectedTheme(context);

        if (selectedTheme.equals(lightTheme)) context.setTheme(R.style.LightTheme);
        else if (selectedTheme.equals(blackTheme)) context.setTheme(R.style.BlackTheme);
        else if (selectedTheme.equals(darkTheme)) context.setTheme(R.style.DarkTheme);
            // Fallback
        else context.setTheme(R.style.DarkTheme);
    }

    /**
     * Return true if the selected theme (on NewPipe settings) is the Light theme
     *
     * @param context context to get the preference
     */
    public static boolean isLightThemeSelected(Context context) {
        return getSelectedTheme(context).equals(context.getResources().getString(R.string.light_theme_key));
    }

    public static String getSelectedTheme(Context context) {
        String themeKey = context.getString(R.string.theme_key);
        String defaultTheme = context.getResources().getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context).getString(themeKey, defaultTheme);
    }
}
