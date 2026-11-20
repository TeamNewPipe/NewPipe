package org.schabi.newpipe.settings.export

import android.content.Context
import android.content.SharedPreferences
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import org.schabi.newpipe.database.Migrations
import org.schabi.newpipe.local.feed.archive.FeedArchiveBackup
import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

class ImportExportManager(private val fileLocator: BackupFileLocator) {
    companion object {
        const val TAG = "ImportExportManager"
        private const val MANIFEST_FORMAT_VERSION = 1
        private const val SQLITE_HEADER_LENGTH = 16
        private const val SQLITE_USER_VERSION_OFFSET = 60
        private const val SQLITE_HEADER_TO_VERSION_LENGTH =
            SQLITE_USER_VERSION_OFFSET - SQLITE_HEADER_LENGTH
        private const val SQLITE_MAGIC = "SQLite format 3\u0000"
        private const val MAX_MIGRATION_PREFERENCES_BYTES = 1_048_576
        private const val MAX_MIGRATION_PREFERENCE_ENTRIES = 2_048
        private const val MAX_MIGRATION_PREFERENCE_KEY_LENGTH = 1_024
    }

    data class BackupContents(
        val hasDatabase: Boolean,
        val hasJsonPreferences: Boolean,
        val hasSerializedPreferences: Boolean,
        val hasLocalRecommendations: Boolean,
        val hasManifest: Boolean,
        val manifest: String?,
        val source: BackupSource,
        val backupFormatVersion: Int?
    ) {
        val hasRecognizableBackupData: Boolean
            get() = hasDatabase || hasJsonPreferences || hasSerializedPreferences ||
                hasLocalRecommendations
    }

    enum class BackupSource {
        WIZESTREAM,
        FOREIGN,
        LEGACY_OR_UNKNOWN,
        INVALID_MANIFEST,
        UNSUPPORTED_WIZESTREAM
    }

    data class DatabaseRollback internal constructor(
        internal val backup: Path,
        internal val previousDatabaseExisted: Boolean
    )

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    @JvmOverloads
    fun exportDatabase(
        preferences: SharedPreferences,
        file: StoredFileHelper,
        database: Path = fileLocator.db,
        localRecommendations: SharedPreferences? = null,
        archiveContext: Context? = null
    ) {
        // truncate the file before writing to it, otherwise if the new content is smaller than the
        // previous file size, the file will retain part of the previous content and be corrupted
        ZipOutputStream(SharpOutputStream(file.openAndTruncateStream()).buffered()).use { outZip ->
            // add the database
            val name = BackupFileLocator.FILE_NAME_DB
            ZipHelper.addFileToZip(outZip, name, database)

            // add the JSON preferences; legacy serialized preferences are still supported when
            // importing old backups, but new backups avoid writing that vulnerable format.
            ZipHelper.addFileToZip(
                outZip,
                BackupFileLocator.FILE_NAME_JSON_PREFS
            ) { byteOutput ->
                JsonWriter
                    .indent("")
                    .on(byteOutput)
                    .`object`(preferences.all)
                    .done()
            }

            if (localRecommendations != null) {
                ZipHelper.addFileToZip(
                    outZip,
                    BackupFileLocator.FILE_NAME_LOCAL_RECOMMENDATIONS
                ) { byteOutput ->
                    JsonWriter
                        .indent("")
                        .on(byteOutput)
                        .`object`(localRecommendations.all)
                        .done()
                }
            }

            // The archive is a separate optional database, not part of Room's newpipe.db.
            val includesFeedArchive = archiveContext?.let {
                FeedArchiveBackup.exportIfPresent(it, outZip)
            } ?: false

            ZipHelper.addFileToZip(
                outZip,
                BackupFileLocator.FILE_NAME_MANIFEST
            ) { byteOutput ->
                JsonWriter
                    .indent("")
                    .on(byteOutput)
                    .`object`()
                    .value("appName", "WizeStream")
                    .value("backupFormatVersion", MANIFEST_FORMAT_VERSION)
                    .value("createdTimestamp", System.currentTimeMillis())
                    .value("includesDatabase", true)
                    .value("includesPreferences", true)
                    .value("includesLocalRecommendations", localRecommendations != null)
                    .value("includesFeedArchive", includesFeedArchive)
                    .value(
                        "includesSponsorBlockSettings",
                        preferences.all.keys.any {
                            it.startsWith("sponsor_block_")
                        }
                    )
                    .end()
                    .done()
            }
        }
    }

    /**
     * Tries to create database directory if it does not exist.
     */
    @Throws(IOException::class)
    fun ensureDbDirectoryExists() {
        fileLocator.db.createParentDirectories()
    }

    /**
     * Extracts the database from the given file to the app's database directory.
     * The current app's database will be overwritten.
     * @param file the .zip file to extract the database from
     * @return true if the database was successfully extracted, false otherwise
     */
    fun extractDb(file: StoredFileHelper): Boolean {
        val importedDb = try {
            stageDb(file)
        } catch (error: IOException) {
            return false
        }
        return try {
            val rollback = replaceDb(importedDb)
            finishDbReplacement(rollback)
            true
        } catch (error: IOException) {
            false
        } finally {
            discardStagedDb(importedDb)
        }
    }

    /** Extracts a database into a temporary sibling without touching the live database. */
    @Throws(IOException::class)
    fun stageDb(file: StoredFileHelper): Path {
        val name = BackupFileLocator.FILE_NAME_DB
        val importedDb = fileLocator.db.resolveSibling("${fileLocator.db.fileName}.import")
        importedDb.deleteIfExists()

        try {
            if (!ZipHelper.extractFileFromZip(file, name, importedDb)) {
                throw FileNotFoundException("Backup database entry '$name' is missing")
            }
            val databaseVersion = readSqliteUserVersion(importedDb)
                ?: throw IOException("The backup database is not a valid SQLite database")
            if (databaseVersion !in Migrations.DB_VER_1..Migrations.DB_VER_CURRENT) {
                throw IOException(
                    "Unsupported backup database version $databaseVersion; " +
                        "supported versions are ${Migrations.DB_VER_1}-" +
                        "${Migrations.DB_VER_CURRENT}"
                )
            }
            return importedDb
        } catch (error: IOException) {
            try {
                importedDb.deleteIfExists()
            } catch (cleanupError: IOException) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    /** Stages a foreign NewPipe-style database for selective, read-only migration. */
    fun stageMigrationDb(file: StoredFileHelper): Path? {
        val importedDb = fileLocator.db.resolveSibling("${fileLocator.db.fileName}.migration")
        importedDb.deleteIfExists()
        return try {
            if (!ZipHelper.extractFileFromZip(
                    file,
                    BackupFileLocator.FILE_NAME_DB,
                    importedDb
                ) || readSqliteUserVersion(importedDb) == null
            ) {
                importedDb.deleteIfExists()
                null
            } else {
                importedDb
            }
        } catch (error: IOException) {
            importedDb.deleteIfExists()
            null
        }
    }

    /** Replaces the live database while retaining a rollback copy until the caller commits. */
    @Throws(IOException::class)
    fun replaceDb(importedDb: Path): DatabaseRollback {
        val rollback = fileLocator.db.resolveSibling("${fileLocator.db.fileName}.rollback")
        rollback.deleteIfExists()
        val previousDatabaseExisted = Files.exists(fileLocator.db)
        if (previousDatabaseExisted) {
            Files.copy(fileLocator.db, rollback, REPLACE_EXISTING)
        }

        try {
            moveReplacing(importedDb, fileLocator.db)
            deleteLiveDatabaseSidecars()
        } catch (error: IOException) {
            if (previousDatabaseExisted && Files.exists(rollback)) {
                Files.copy(rollback, fileLocator.db, REPLACE_EXISTING)
            }
            rollback.deleteIfExists()
            throw error
        }
        return DatabaseRollback(rollback, previousDatabaseExisted)
    }

    fun rollbackDb(recovery: DatabaseRollback) {
        if (recovery.previousDatabaseExisted) {
            Files.copy(recovery.backup, fileLocator.db, REPLACE_EXISTING)
        } else {
            fileLocator.db.deleteIfExists()
        }
        deleteLiveDatabaseSidecars()
        recovery.backup.deleteIfExists()
    }

    fun finishDbReplacement(recovery: DatabaseRollback) {
        recovery.backup.deleteIfExists()
    }

    fun discardStagedDb(importedDb: Path) {
        importedDb.deleteIfExists()
        importedDb.resolveSibling("${importedDb.fileName}-journal").deleteIfExists()
        importedDb.resolveSibling("${importedDb.fileName}-wal").deleteIfExists()
        importedDb.resolveSibling("${importedDb.fileName}-shm").deleteIfExists()
    }

    private fun moveReplacing(source: Path, destination: Path) {
        try {
            Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            Files.move(source, destination, REPLACE_EXISTING)
        }
    }

    private fun deleteLiveDatabaseSidecars() {
        fileLocator.dbJournal.deleteIfExists()
        fileLocator.dbWal.deleteIfExists()
        fileLocator.dbShm.deleteIfExists()
    }

    @Throws(IOException::class)
    private fun readSqliteUserVersion(database: java.nio.file.Path): Int? {
        return try {
            DataInputStream(Files.newInputStream(database).buffered()).use { input ->
                val header = ByteArray(SQLITE_HEADER_LENGTH)
                input.readFully(header)
                if (header.toString(Charsets.US_ASCII) != SQLITE_MAGIC) {
                    return null
                }
                if (input.skipBytes(SQLITE_HEADER_TO_VERSION_LENGTH)
                    != SQLITE_HEADER_TO_VERSION_LENGTH
                ) {
                    return null
                }
                input.readInt()
            }
        } catch (error: EOFException) {
            null
        }
    }

    @Deprecated(
        "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
        replaceWith = ReplaceWith("exportHasJsonPrefs")
    )
    fun exportHasSerializedPrefs(zipFile: StoredFileHelper): Boolean {
        return ZipHelper.zipContainsFile(zipFile, BackupFileLocator.FILE_NAME_SERIALIZED_PREFS)
    }

    fun exportHasJsonPrefs(zipFile: StoredFileHelper): Boolean {
        return ZipHelper.zipContainsFile(zipFile, BackupFileLocator.FILE_NAME_JSON_PREFS)
    }

    @Throws(IOException::class)
    fun inspectBackup(zipFile: StoredFileHelper): BackupContents {
        var hasDatabase = false
        var hasJsonPreferences = false
        var hasSerializedPreferences = false
        var hasLocalRecommendations = false
        var hasManifest = false
        var manifest: String? = null

        ZipInputStream(SharpInputStream(zipFile.stream).buffered()).use { inZip ->
            var entry = inZip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    BackupFileLocator.FILE_NAME_DB -> hasDatabase = true

                    BackupFileLocator.FILE_NAME_JSON_PREFS -> hasJsonPreferences = true

                    BackupFileLocator.FILE_NAME_SERIALIZED_PREFS -> hasSerializedPreferences = true

                    BackupFileLocator.FILE_NAME_LOCAL_RECOMMENDATIONS ->
                        hasLocalRecommendations = true

                    BackupFileLocator.FILE_NAME_MANIFEST -> {
                        hasManifest = true
                        manifest = inZip.readBytes().decodeToString()
                    }
                }
                entry = inZip.nextEntry
            }
        }

        val manifestInfo = inspectManifest(hasManifest, manifest)
        return BackupContents(
            hasDatabase,
            hasJsonPreferences,
            hasSerializedPreferences,
            hasLocalRecommendations,
            hasManifest,
            manifest,
            manifestInfo.first,
            manifestInfo.second
        )
    }

    private fun inspectManifest(
        hasManifest: Boolean,
        manifest: String?
    ): Pair<BackupSource, Int?> {
        if (!hasManifest) {
            return BackupSource.LEGACY_OR_UNKNOWN to null
        }
        return try {
            val json = JsonParser.`object`().from(checkNotNull(manifest))
            val appName = json.getString("appName")
            val formatVersion = json.getInt("backupFormatVersion")
            when {
                appName != "WizeStream" -> BackupSource.FOREIGN to formatVersion

                formatVersion != MANIFEST_FORMAT_VERSION ->
                    BackupSource.UNSUPPORTED_WIZESTREAM to formatVersion

                else -> BackupSource.WIZESTREAM to formatVersion
            }
        } catch (error: JsonParserException) {
            BackupSource.INVALID_MANIFEST to null
        }
    }

    /**
     * Remove all shared preferences from the app and load the preferences supplied to the manager.
     */
    @Deprecated(
        "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
        replaceWith = ReplaceWith("loadJsonPrefs")
    )
    @Throws(IOException::class, ClassNotFoundException::class)
    fun loadSerializedPrefs(zipFile: StoredFileHelper, preferences: SharedPreferences) {
        replacePreferences(preferences, readSerializedPrefs(zipFile))
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IOException::class, ClassNotFoundException::class)
    fun readSerializedPrefs(zipFile: StoredFileHelper): Map<String, Any?> {
        var entries: Map<String, Any?>? = null
        ZipHelper.extractFileFromZip(zipFile, BackupFileLocator.FILE_NAME_SERIALIZED_PREFS) {
            PreferencesObjectInputStream(it).use { input ->
                entries = sanitizePreferences(input.readObject() as Map<String, *>)
            }
        }.let { fileExists ->
            if (!fileExists) {
                throw FileNotFoundException(BackupFileLocator.FILE_NAME_SERIALIZED_PREFS)
            }
        }
        return checkNotNull(entries)
    }

    /**
     * Remove all shared preferences from the app and load the preferences supplied to the manager.
     */
    @Throws(IOException::class, JsonParserException::class)
    fun loadJsonPrefs(zipFile: StoredFileHelper, preferences: SharedPreferences) {
        replacePreferences(preferences, readJsonPrefs(zipFile))
    }

    @Throws(IOException::class, JsonParserException::class)
    fun readJsonPrefs(zipFile: StoredFileHelper): Map<String, Any?> {
        return readJsonPreferences(zipFile, BackupFileLocator.FILE_NAME_JSON_PREFS)
    }

    @Throws(IOException::class, JsonParserException::class)
    fun readLocalRecommendations(zipFile: StoredFileHelper): Map<String, Any?> {
        return readJsonPreferences(
            zipFile,
            BackupFileLocator.FILE_NAME_LOCAL_RECOMMENDATIONS
        )
    }

    private fun readJsonPreferences(
        zipFile: StoredFileHelper,
        entryName: String
    ): Map<String, Any?> {
        var entries: Map<String, Any?>? = null
        ZipHelper.extractFileFromZip(zipFile, entryName) {
            val jsonObject = JsonParser.`object`().from(it)
            val parsedEntries = mutableMapOf<String, Any?>()

            for ((key, value) in jsonObject) {
                when (value) {
                    is Boolean, is Float, is Int, is Long, is String ->
                        parsedEntries[key] = value

                    is JsonArray ->
                        parsedEntries[key] = value.mapNotNull { e -> e as? String }.toSet()
                }
            }
            entries = parsedEntries
        }.let { fileExists ->
            if (!fileExists) {
                throw FileNotFoundException(entryName)
            }
        }
        return checkNotNull(entries)
    }

    /**
     * Reads preferences for selective migration. JSON is always preferred. The legacy serialized
     * format is accepted only when the caller has already identified a known PipePipe database.
     */
    @Throws(IOException::class, JsonParserException::class, ClassNotFoundException::class)
    fun readMigrationPrefs(
        zipFile: StoredFileHelper,
        allowPipePipeSerializedPreferences: Boolean
    ): Map<String, Any?> {
        if (exportHasJsonPrefs(zipFile)) {
            return readJsonPrefs(zipFile)
        }
        if (!allowPipePipeSerializedPreferences ||
            !ZipHelper.zipContainsFile(zipFile, BackupFileLocator.FILE_NAME_SERIALIZED_PREFS)
        ) {
            return emptyMap()
        }
        return readBoundedSerializedMigrationPrefs(zipFile)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readBoundedSerializedMigrationPrefs(
        zipFile: StoredFileHelper
    ): Map<String, Any?> {
        var entries: Map<String, Any?>? = null
        ZipHelper.extractFileFromZip(zipFile, BackupFileLocator.FILE_NAME_SERIALIZED_PREFS) {
            val serialized = ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) {
                        break
                    }
                    total += count
                    if (total > MAX_MIGRATION_PREFERENCES_BYTES) {
                        throw IOException("PipePipe preferences exceed the migration size limit")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            PreferencesObjectInputStream(ByteArrayInputStream(serialized)).use { input ->
                val rawEntries = input.readObject() as? Map<*, *>
                    ?: throw IOException("PipePipe preferences are not a map")
                if (rawEntries.size > MAX_MIGRATION_PREFERENCE_ENTRIES) {
                    throw IOException("PipePipe preferences contain too many entries")
                }
                val stringKeyedEntries = rawEntries.mapNotNull { (key, value) ->
                    (key as? String)
                        ?.takeIf { it.length <= MAX_MIGRATION_PREFERENCE_KEY_LENGTH }
                        ?.let { it to value }
                }.toMap()
                if (stringKeyedEntries.size != rawEntries.size) {
                    throw IOException("PipePipe preferences contain invalid keys")
                }
                entries = sanitizePreferences(stringKeyedEntries)
            }
        }.let { fileExists ->
            if (!fileExists) {
                throw FileNotFoundException(BackupFileLocator.FILE_NAME_SERIALIZED_PREFS)
            }
        }
        return checkNotNull(entries)
    }

    fun replacePreferences(preferences: SharedPreferences, entries: Map<String, *>) {
        val editor = preferences.edit()
        editor.clear()
        for ((key, value) in sanitizePreferences(entries)) {
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        if (!editor.commit()) {
            throw IOException("Unable to commit imported preferences")
        }
    }

    private fun sanitizePreferences(entries: Map<String, *>): Map<String, Any?> {
        return entries.mapNotNull { (key, value) ->
            when (value) {
                is Boolean, is Float, is Int, is Long, is String -> key to value

                is Set<*> -> {
                    val strings = value.filterIsInstance<String>().toSet()
                    if (strings.size == value.size) key to strings else null
                }

                else -> null
            }
        }.toMap()
    }
}
