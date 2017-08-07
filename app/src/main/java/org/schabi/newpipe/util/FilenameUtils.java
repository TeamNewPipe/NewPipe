package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.regex.Pattern;

public class FilenameUtils {

    /**
     * #143 #44 #42 #22: make sure that the filename does not contain illegal chars.
     * @param context the context to retrieve strings and preferences from
     * @param title the title to create a filename from
     * @return the filename
     */
    public static String createFilename(Context context, String title) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(R.string.settings_file_charset_key);
        final String value = sharedPreferences.getString(key, context.getString(R.string.default_file_charset_value));
        Pattern pattern = Pattern.compile(value);

        final String replacementChar = sharedPreferences.getString(context.getString(R.string.settings_file_replacement_character_key), "_");
        return createFilename(title, pattern, replacementChar);
    }

    /**
     * Create a valid filename
     * @param title the title to create a filename from
     * @param invalidCharacters patter matching invalid characters
     * @param replacementChar the replacement
     * @return the filename
     */
    private static String createFilename(String title, Pattern invalidCharacters, String replacementChar) {
        return title.replaceAll(invalidCharacters.pattern(), replacementChar);
    }
}