package org.schabi.newpipe.settings.export

import java.io.File

/**
 * Locates specific files of NewPipe based on the home directory of the app.
 */
class BackupFileLocator(private val homeDir: File) {
    companion object {
        const val FILE_NAME_DB = "newpipe.db"
        @Deprecated(
            "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
            replaceWith = ReplaceWith("FILE_NAME_JSON_PREFS")
        )
        const val FILE_NAME_SERIALIZED_PREFS = "newpipe.settings"
        const val FILE_NAME_JSON_PREFS = "preferences.json"
    }

    val dbDir by lazy { File(homeDir, "/databases") }

    val db by lazy { File(dbDir, FILE_NAME_DB) }

    val dbJournal by lazy { File(dbDir, "$FILE_NAME_DB-journal") }

    val dbShm by lazy { File(dbDir, "$FILE_NAME_DB-shm") }

    val dbWal by lazy { File(dbDir, "$FILE_NAME_DB-wal") }
}
