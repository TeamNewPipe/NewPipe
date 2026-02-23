/*
 * SPDX-FileCopyrightText: 2017-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.regex.Matcher
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.getStringSafe

object FilenameUtils {
    private const val CHARSET_MOST_SPECIAL = "[\\n\\r|?*<\":\\\\>/']+"
    private const val CHARSET_ONLY_LETTERS_AND_DIGITS = "[^\\w\\d]+"

    /**
     * #143 #44 #42 #22: make sure that the filename does not contain illegal chars.
     *
     * @param context the context to retrieve strings and preferences from
     * @param title the title to create a filename from
     * @return the filename
     */
    @JvmStatic
    fun createFilename(context: Context, title: String): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val charsetLd = context.getString(R.string.charset_letters_and_digits_value)
        val charsetMs = context.getString(R.string.charset_most_special_value)
        val defaultCharset = context.getString(R.string.default_file_charset_value)

        val replacementChar = sharedPreferences.getStringSafe(
            context.getString(R.string.settings_file_replacement_character_key),
            "_"
        )
        val selectedCharset = sharedPreferences.getStringSafe(
            context.getString(R.string.settings_file_charset_key),
            ""
        ).ifEmpty { defaultCharset }

        val charset = when (selectedCharset) {
            charsetLd -> CHARSET_ONLY_LETTERS_AND_DIGITS
            charsetMs -> CHARSET_MOST_SPECIAL
            else -> selectedCharset // Is the user using a custom charset?
        }

        return createFilename(title, charset, Matcher.quoteReplacement(replacementChar))
    }

    /**
     * Create a valid filename.
     *
     * @param title the title to create a filename from
     * @param invalidCharacters patter matching invalid characters
     * @param replacementChar the replacement
     * @return the filename
     */
    private fun createFilename(
        title: String,
        invalidCharacters: String,
        replacementChar: String
    ): String {
        return title.replace(invalidCharacters.toRegex(), replacementChar)
    }
}
