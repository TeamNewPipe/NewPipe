package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.regex.Pattern;

public class FilenameUtils {

    private static final String CHARSET_MOST_SPECIAL = "[\\n\\r|?*<\":\\\\>/']+";
    private static final String CHARSET_ONLY_LETTERS_AND_DIGITS = "[^\\w\\d]+";

    /**
     * #143 #44 #42 #22: make sure that the filename does not contain illegal chars.
     * @param context the context to retrieve strings and preferences from
     * @param title the title to create a filename from
     * @return the filename
     */
    public static String createFilename(Context context, String title) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        final String charset_ld = context.getString(R.string.charset_letters_and_digits_value);
        final String charset_ms = context.getString(R.string.charset_most_special_value);
        final String defaultCharset = context.getString(R.string.default_file_charset_value);

        final String replacementChar = sharedPreferences.getString(context.getString(R.string.settings_file_replacement_character_key), "_");
        String selectedCharset = sharedPreferences.getString(context.getString(R.string.settings_file_charset_key), null);

        final String charset;

        if (selectedCharset == null || selectedCharset.isEmpty()) selectedCharset = defaultCharset;

        if (selectedCharset.equals(charset_ld)) {
            charset = CHARSET_ONLY_LETTERS_AND_DIGITS;
        } else if (selectedCharset.equals(charset_ms)) {
            charset = CHARSET_MOST_SPECIAL;
        } else {
            charset = selectedCharset;// ¿is the user using a custom charset?
        }

        Pattern pattern = Pattern.compile(charset);

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