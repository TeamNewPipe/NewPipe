package org.schabi.newpipe.settings.export

import android.content.SharedPreferences
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper
import java.io.FileNotFoundException
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.zip.ZipOutputStream

class ImportExportManager(private val fileLocator: BackupFileLocator) {
    companion object {
        const val TAG = "ImportExportManager"
    }

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, file: StoredFileHelper) {
        // truncate the file before writing to it, otherwise if the new content is smaller than the
        // previous file size, the file will retain part of the previous content and be corrupted
        ZipOutputStream(SharpOutputStream(file.openAndTruncateStream()).buffered()).use { outZip ->
            // add the database
            ZipHelper.addFileToZip(
                outZip,
                BackupFileLocator.FILE_NAME_DB,
                fileLocator.db.path,
            )

            // add the legacy vulnerable serialized preferences (will be removed in the future)
            ZipHelper.addFileToZip(
                outZip,
                BackupFileLocator.FILE_NAME_SERIALIZED_PREFS
            ) { byteOutput ->
                ObjectOutputStream(byteOutput).use { output ->
                    output.writeObject(preferences.all)
                    output.flush()
                }
            }

            // add the JSON preferences
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
        }
    }

    /**
     * Tries to create database directory if it does not exist.
     *
     * @return Whether the directory exists afterwards.
     */
    fun ensureDbDirectoryExists(): Boolean {
        return fileLocator.dbDir.exists() || fileLocator.dbDir.mkdir()
    }

    /**
     * Extracts the database from the given file to the app's database directory.
     * The current app's database will be overwritten.
     * @param file the .zip file to extract the database from
     * @return true if the database was successfully extracted, false otherwise
     */
    fun extractDb(file: StoredFileHelper): Boolean {
        val success = ZipHelper.extractFileFromZip(
            file,
            BackupFileLocator.FILE_NAME_DB,
            fileLocator.db.path,
        )

        if (success) {
            fileLocator.dbJournal.delete()
            fileLocator.dbWal.delete()
            fileLocator.dbShm.delete()
        }

        return success
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

    /**
     * Remove all shared preferences from the app and load the preferences supplied to the manager.
     */
    @Deprecated(
        "Serializing preferences with Java's ObjectOutputStream is vulnerable to injections",
        replaceWith = ReplaceWith("loadJsonPrefs")
    )
    @Throws(IOException::class, ClassNotFoundException::class)
    fun loadSerializedPrefs(zipFile: StoredFileHelper, preferences: SharedPreferences) {
        ZipHelper.extractFileFromZip(zipFile, BackupFileLocator.FILE_NAME_SERIALIZED_PREFS) {
            PreferencesObjectInputStream(it).use { input ->
                @Suppress("UNCHECKED_CAST")
                val entries = input.readObject() as Map<String, *>

                val editor = preferences.edit()
                editor.clear()

                for ((key, value) in entries) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> {
                            // There are currently only Sets with type String possible
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>?)
                        }
                    }
                }

                if (!editor.commit()) {
                    throw IOException("Unable to commit loadSerializedPrefs")
                }
            }
        }.let { fileExists ->
            if (!fileExists) {
                throw FileNotFoundException(BackupFileLocator.FILE_NAME_SERIALIZED_PREFS)
            }
        }
    }

    /**
     * Remove all shared preferences from the app and load the preferences supplied to the manager.
     */
    @Throws(IOException::class, JsonParserException::class)
    fun loadJsonPrefs(zipFile: StoredFileHelper, preferences: SharedPreferences) {
        ZipHelper.extractFileFromZip(zipFile, BackupFileLocator.FILE_NAME_JSON_PREFS) {
            val jsonObject = JsonParser.`object`().from(it)

            val editor = preferences.edit()
            editor.clear()

            for ((key, value) in jsonObject) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is JsonArray -> {
                        editor.putStringSet(key, value.mapNotNull { e -> e as? String }.toSet())
                    }
                }
            }

            if (!editor.commit()) {
                throw IOException("Unable to commit loadJsonPrefs")
            }
        }.let { fileExists ->
            if (!fileExists) {
                throw FileNotFoundException(BackupFileLocator.FILE_NAME_JSON_PREFS)
            }
        }
    }
}
