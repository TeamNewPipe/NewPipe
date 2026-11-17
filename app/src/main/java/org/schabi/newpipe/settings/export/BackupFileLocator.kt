package org.schabi.newpipe.settings.export

import android.content.Context
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Locates WizeStream files based on the home directory of the app.
 */
class BackupFileLocator(context: Context) {
    companion object {
        const val FILE_NAME_DB = "newpipe.db"

        @Deprecated(
            "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
            replaceWith = ReplaceWith("FILE_NAME_JSON_PREFS")
        )
        const val FILE_NAME_SERIALIZED_PREFS = "newpipe.settings"
        const val FILE_NAME_JSON_PREFS = "preferences.json"
        const val FILE_NAME_LOCAL_RECOMMENDATIONS = "local_recommendations.json"
        const val FILE_NAME_MANIFEST = "backup_manifest.json"
    }

    val db: Path = context.getDatabasePath(FILE_NAME_DB).toPath()
    val dbJournal: Path = db.resolveSibling("$FILE_NAME_DB-journal")
    val dbShm: Path = db.resolveSibling("$FILE_NAME_DB-shm")
    val dbWal: Path = db.resolveSibling("$FILE_NAME_DB-wal")
}
