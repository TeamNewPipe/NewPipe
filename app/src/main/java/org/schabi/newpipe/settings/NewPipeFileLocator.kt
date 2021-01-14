package org.schabi.newpipe.settings

import java.io.File

/**
 * Locates specific files of NewPipe based on the home directory of the app.
 */
class NewPipeFileLocator(private val homeDir: File) {

    val dbDir by lazy { File(homeDir, "/databases") }

    val db by lazy { File(homeDir, "/databases/newpipe.db") }

    val dbJournal by lazy { File(homeDir, "/databases/newpipe.db-journal") }

    val dbShm by lazy { File(homeDir, "/databases/newpipe.db-shm") }

    val dbWal by lazy { File(homeDir, "/databases/newpipe.db-wal") }

    val settings by lazy { File(homeDir, "/databases/newpipe.settings") }
}
