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

        String themeKey = context.getString(R.string.theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_title);
        String blackTheme = context.getResources().getString(R.string.black_theme_title);

        String sp = PreferenceManager.getDefaultSharedPreferences(context).getString(themeKey, context.getResources().getString(R.string.light_theme_title));

        if (sp.equals(darkTheme)) context.setTheme(R.style.DarkTheme);
        else if (sp.equals(blackTheme)) context.setTheme(R.style.BlackTheme);
        else context.setTheme(R.style.AppTheme);
    }

    /**
     * Return true if the selected theme (on NewPipe settings) is the Light theme
     *
     * @param context context to get the preference
     */
    public static boolean isLightThemeSelected(Context context) {
        String themeKey = context.getString(R.string.theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_title);
        String blackTheme = context.getResources().getString(R.string.black_theme_title);

        String sp = PreferenceManager.getDefaultSharedPreferences(context).getString(themeKey, context.getResources().getString(R.string.light_theme_title));

        return !(sp.equals(darkTheme) || sp.equals(blackTheme));
    }
}
