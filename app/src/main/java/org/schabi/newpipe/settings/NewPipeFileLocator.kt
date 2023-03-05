package org.schabi.newpipe.settings

import java.io.File

/**
 * Locates specific files of NewPipe based on the home directory of the app.
 */
class NewPipeFileLocator(private val homeDir: File) {
    companion object {
        const val FILE_NAME_DB = "newpipe.db"
        const val FILE_NAME_SETTINGS = "newpipe.settings.json"
    }

    val dbDir by lazy { File(homeDir, "/databases") }

    val db by lazy { File(homeDir, "/databases/$FILE_NAME_DB") }

    val dbJournal by lazy { File(homeDir, "/databases/$FILE_NAME_DB-journal") }

    val dbShm by lazy { File(homeDir, "/databases/$FILE_NAME_DB-shm") }

    val dbWal by lazy { File(homeDir, "/databases/$FILE_NAME_DB-wal") }

    val settings by lazy { File(homeDir, "/databases/$FILE_NAME_SETTINGS") }
}
