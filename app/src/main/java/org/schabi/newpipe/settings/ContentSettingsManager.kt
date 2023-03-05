package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.util.Log
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.JSONSerializer
import org.schabi.newpipe.util.ZipHelper
import java.io.IOException
import java.util.zip.ZipOutputStream

class ContentSettingsManager(private val fileLocator: NewPipeFileLocator) {
    companion object {
        const val TAG = "ContentSetManager"

        const val FILE_NAME_DB = NewPipeFileLocator.FILE_NAME_DB
        const val FILE_NAME_SETTINGS = NewPipeFileLocator.FILE_NAME_SETTINGS
    }

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, file: StoredFileHelper) {
        file.create()
        ZipOutputStream(SharpOutputStream(file.stream).buffered())
            .use { outZip ->
                ZipHelper.addFileToZip(outZip, fileLocator.db.path, FILE_NAME_DB)

                try {
                    JSONSerializer.toJson(preferences.all, fileLocator.settings.outputStream())
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Unable to export settings", e)
                }

                ZipHelper.addFileToZip(outZip, fileLocator.settings.path, FILE_NAME_SETTINGS)
            }
    }

    fun deleteSettingsFile() {
        fileLocator.settings.delete()
    }

    /**
     * Tries to create database directory if it does not exist.
     *
     * @return Whether the directory exists afterwards.
     */
    fun ensureDbDirectoryExists(): Boolean {
        return fileLocator.dbDir.exists() || fileLocator.dbDir.mkdir()
    }

    fun extractDb(file: StoredFileHelper): Boolean {
        val success = ZipHelper.extractFileFromZip(file, fileLocator.db.path, FILE_NAME_DB)
        if (success) {
            fileLocator.dbJournal.delete()
            fileLocator.dbWal.delete()
            fileLocator.dbShm.delete()
        }

        return success
    }

    fun extractSettings(file: StoredFileHelper): Boolean {
        return ZipHelper.extractFileFromZip(file, fileLocator.settings.path, FILE_NAME_SETTINGS)
    }

    fun loadSharedPreferences(preferences: SharedPreferences) {
        try {
            val preferenceEditor = preferences.edit()

            @Suppress("UNCHECKED_CAST")
            val entries = JSONSerializer.fromJson(fileLocator.settings.inputStream(), Map::class.java)
                as Map<String, *>

            preferenceEditor.clear()
            for ((key, value) in entries) {
                when (value) {
                    is Boolean -> {
                        preferenceEditor.putBoolean(key, value)
                    }
                    is Float -> {
                        preferenceEditor.putFloat(key, value)
                    }
                    is Int -> {
                        preferenceEditor.putInt(key, value)
                    }
                    is Long -> {
                        preferenceEditor.putLong(key, value)
                    }
                    is String -> {
                        preferenceEditor.putString(key, value)
                    }
                    is Set<*> -> {
                        // There are currently only Sets with type String possible
                        @Suppress("UNCHECKED_CAST")
                        preferenceEditor.putStringSet(key, value as Set<String>?)
                    }
                    // In JSON Lists are the same as Sets
                    is List<*> -> {
                        // There are currently only Sets with type String possible
                        @Suppress("UNCHECKED_CAST")
                        preferenceEditor.putStringSet(key, (value as List<String>?)?.let { HashSet(it) })
                    }
                }
            }
            if (!preferenceEditor.commit()) {
                throw IllegalStateException("Committing preferences was not successful")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to loadSharedPreferences", e)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Unable to loadSharedPreferences", e)
        }
    }
}
