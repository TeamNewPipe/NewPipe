package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import java.util.regex.Matcher
import java.util.regex.Pattern

object FilenameUtils {
    private val CHARSET_MOST_SPECIAL: String = "[\\n\\r|?*<\":\\\\>/']+"
    private val CHARSET_ONLY_LETTERS_AND_DIGITS: String = "[^\\w\\d]+"

    /**
     * #143 #44 #42 #22: make sure that the filename does not contain illegal chars.
     *
     * @param context the context to retrieve strings and preferences from
     * @param title   the title to create a filename from
     * @return the filename
     */
    fun createFilename(context: Context?, title: String): String {
        val sharedPreferences: SharedPreferences = PreferenceManager
                .getDefaultSharedPreferences((context)!!)
        val charsetLd: String = context!!.getString(R.string.charset_letters_and_digits_value)
        val charsetMs: String = context.getString(R.string.charset_most_special_value)
        val defaultCharset: String = context.getString(R.string.default_file_charset_value)
        val replacementChar: String? = sharedPreferences.getString(
                context.getString(R.string.settings_file_replacement_character_key), "_")
        var selectedCharset: String? = sharedPreferences.getString(
                context.getString(R.string.settings_file_charset_key), null)
        val charset: String?
        if (selectedCharset == null || selectedCharset.isEmpty()) {
            selectedCharset = defaultCharset
        }
        if ((selectedCharset == charsetLd)) {
            charset = CHARSET_ONLY_LETTERS_AND_DIGITS
        } else if ((selectedCharset == charsetMs)) {
            charset = CHARSET_MOST_SPECIAL
        } else {
            charset = selectedCharset // Is the user using a custom charset?
        }
        val pattern: Pattern = Pattern.compile(charset)
        return createFilename(title, pattern, Matcher.quoteReplacement(replacementChar))
    }

    /**
     * Create a valid filename.
     *
     * @param title             the title to create a filename from
     * @param invalidCharacters patter matching invalid characters
     * @param replacementChar   the replacement
     * @return the filename
     */
    private fun createFilename(title: String, invalidCharacters: Pattern,
                               replacementChar: String): String {
        return title.replace(invalidCharacters.pattern().toRegex(), replacementChar)
    }
}
