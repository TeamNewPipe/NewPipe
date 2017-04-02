package org.schabi.newpipe.util;

import android.content.Context;
import android.preference.PreferenceManager;

import org.schabi.newpipe.R;

public class ThemeHelper {

    /**
     * Apply the selected theme (on NewPipe settings) in the context
     *
     * @param context               context that the theme will be applied
     * @param useActionbarTheme     whether to use an action bar theme or not
     */
    public static void setTheme(Context context, boolean useActionbarTheme) {
        String themeKey = context.getString(R.string.theme_key);
        String darkTheme = context.getResources().getString(R.string.dark_theme_title);
        String blackTheme = context.getResources().getString(R.string.black_theme_title);

        String sp = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeKey, context.getResources().getString(R.string.light_theme_title));

        if (useActionbarTheme) {
            if (sp.equals(darkTheme)) context.setTheme(R.style.DarkTheme);
            else if (sp.equals(blackTheme)) context.setTheme(R.style.BlackTheme);
            else context.setTheme(R.style.AppTheme);
        } else {
            if (sp.equals(darkTheme)) context.setTheme(R.style.DarkTheme_NoActionBar);
            else if (sp.equals(blackTheme)) context.setTheme(R.style.BlackTheme_NoActionBar);
            else context.setTheme(R.style.AppTheme_NoActionBar);
        }
    }
}
