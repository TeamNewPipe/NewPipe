/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.preferences

/**
 * Preference keys, values, and defaults for the Download settings screen.
 *
 */
object DownloadPreferences {
    const val KEY_STORAGE_ASK = "downloads_storage_ask"
    const val KEY_STORAGE_USE_SAF = "storage_use_saf"
    const val KEY_DOWNLOAD_FOLDER_VIDEO = "download_path"
    const val KEY_DOWNLOAD_FOLDER_AUDIO = "download_path_audio"
    const val KEY_FILE_CHARSET = "file_rename_charset"
    const val KEY_REPLACEMENT_CHARACTER = "file_replacement_character"
    const val KEY_MAXIMUM_RETRIES = "downloads_max_retry"
    const val KEY_PAUSE_ON_METERED_NETWORK = "cross_network_downloads"
    const val KEY_LIMIT_QUEUE = "downloads_queue_limit"

    const val CHARSET_LETTERS_AND_DIGITS = "CHARSET_LETTERS_AND_DIGITS"
    const val CHARSET_MOST_SPECIAL = "CHARSET_MOST_SPECIAL"

    const val DEFAULT_STORAGE_ASK = false
    const val DEFAULT_STORAGE_USE_SAF = true

    const val DEFAULT_DOWNLOAD_FOLDER = ""
    const val DEFAULT_FILE_CHARSET = CHARSET_MOST_SPECIAL
    const val DEFAULT_REPLACEMENT_CHARACTER = "_"
    const val DEFAULT_MAXIMUM_RETRIES = "3"
    const val DEFAULT_PAUSE_ON_METERED_NETWORK = false
    const val DEFAULT_LIMIT_QUEUE = true

    val FILE_CHARSETS = listOf(CHARSET_LETTERS_AND_DIGITS, CHARSET_MOST_SPECIAL)

    const val MAXIMUM_RETRIES_NONE = "0"

    val MAXIMUM_RETRY_COUNTS = listOf(
        MAXIMUM_RETRIES_NONE, "1", "2", "3", "4", "5", "7", "10", "15"
    )

    fun normalizedMaximumRetries(storedValue: String): String = when (storedValue.toIntOrNull()) {
        null -> MAXIMUM_RETRIES_NONE
        else -> storedValue
    }
}
