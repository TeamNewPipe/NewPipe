package org.schabi.newpipe.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.support.annotation.AttrRes;
import android.support.annotation.StyleRes;

import org.schabi.newpipe.R;

public class ThemeHelper {

    /**
     * Apply the selected theme (on NewPipe settings) in the context
     *
     * @param context context that the theme will be applied
     */
    public static void setTheme(Context context) {
        context.setTheme(getSelectedThemeStyle(context));
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
    public static int getSelectedThemeStyle(Context context) {
        String lightTheme = context.getResources().getString(R.string.light_theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_key);
        String blackTheme = context.getResources().getString(R.string.black_theme_key);

        String selectedTheme = getSelectedTheme(context);

        if (selectedTheme.equals(lightTheme)) return R.style.LightTheme;
        else if (selectedTheme.equals(blackTheme)) return R.style.BlackTheme;
        else if (selectedTheme.equals(darkTheme)) return R.style.DarkTheme;
            // Fallback
        else return R.style.DarkTheme;
    }

    public static String getSelectedTheme(Context context) {
        String themeKey = context.getString(R.string.theme_key);
        String defaultTheme = context.getResources().getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context).getString(themeKey, defaultTheme);
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
