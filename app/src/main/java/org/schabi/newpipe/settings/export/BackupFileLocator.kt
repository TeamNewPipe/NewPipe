package org.schabi.newpipe.settings.export

import java.nio.file.Path
import kotlin.io.path.div

/**
 * Locates specific files of NewPipe based on the home directory of the app.
 */
class BackupFileLocator(homeDir: Path) {
    companion object {
        const val FILE_NAME_DB = "newpipe.db"
        @Deprecated(
            "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
            replaceWith = ReplaceWith("FILE_NAME_JSON_PREFS")
        )
        const val FILE_NAME_SERIALIZED_PREFS = "newpipe.settings"
        const val FILE_NAME_JSON_PREFS = "preferences.json"
    }

    val dbDir = homeDir / "databases"
    val db = homeDir / FILE_NAME_DB
    val dbJournal = homeDir / "$FILE_NAME_DB-journal"
    val dbShm = dbDir / "$FILE_NAME_DB-shm"
    val dbWal = dbDir / "$FILE_NAME_DB-wal"
}
