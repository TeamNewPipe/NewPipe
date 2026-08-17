package org.schabi.newpipe.settings.export

import android.content.SharedPreferences
import kotlinx.serialization.json.*
import java.io.FileNotFoundException
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper

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
        val stream = file.openAndTruncateStream()
        ZipOutputStream(SharpOutputStream(stream).buffered()).use { outZip ->
            // add the database
            val name = BackupFileLocator.FILE_NAME_DB
            ZipHelper.addFileToZip(outZip, name, fileLocator.db)

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

            ZipHelper.addFileToZip(
                outZip,
                BackupFileLocator.FILE_NAME_JSON_PREFS
            ) { byteOutput ->
                val jsonMap = preferences.all.mapValues { (_, value) ->
                    when (value) {
                        is Boolean -> JsonPrimitive(value)
                        is Float -> JsonPrimitive(value)
                        is Int -> JsonPrimitive(value)
                        is Long -> JsonPrimitive(value)
                        is String -> JsonPrimitive(value)
                        is Set<*> -> JsonArray(value.map { JsonPrimitive(it as String) })
                        else -> JsonNull
                    }
                }
                val element = JsonObject(jsonMap)
                val format = Json { prettyPrint = true }
                byteOutput.write(format.encodeToString(JsonObject.serializer(), element).toByteArray())
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
        val name = BackupFileLocator.FILE_NAME_DB
        val success = ZipHelper.extractFileFromZip(file, name, fileLocator.db)

        if (success) {
            fileLocator.dbJournal.deleteIfExists()
            fileLocator.dbWal.deleteIfExists()
            fileLocator.dbShm.deleteIfExists()
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
    @Throws(IOException::class, Exception::class)
    fun loadJsonPrefs(zipFile: StoredFileHelper, preferences: SharedPreferences) {
        ZipHelper.extractFileFromZip(zipFile, BackupFileLocator.FILE_NAME_JSON_PREFS) {
            val jsonObject = Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject

            val editor = preferences.edit()
            editor.clear()

            for ((key, value) in jsonObject) {
                when {
                    value is JsonPrimitive && value.isString -> editor.putString(key, value.content)
                    value is JsonPrimitive && value.booleanOrNull != null -> editor.putBoolean(key, value.boolean)
                    value is JsonPrimitive && value.intOrNull != null -> editor.putInt(key, value.int)
                    value is JsonPrimitive && value.longOrNull != null -> editor.putLong(key, value.long)
                    value is JsonPrimitive && value.floatOrNull != null -> editor.putFloat(key, value.float)
                    value is JsonArray -> {
                        editor.putStringSet(key, value.mapNotNull { e -> e.jsonPrimitive.contentOrNull }.toSet())
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
