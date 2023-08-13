package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.util.Log
import org.schabi.newpipe.MainActivity.DEBUG
import org.schabi.newpipe.streams.io.SharpOutputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.ZipHelper
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.ZipOutputStream

class ContentSettingsManager(private val fileLocator: NewPipeFileLocator) {
    companion object {
        const val TAG = "ContentSetManager"
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
                ZipHelper.addFileToZip(outZip, fileLocator.db.path, "newpipe.db")

                try {
                    ObjectOutputStream(fileLocator.settings.outputStream()).use { output ->
                        output.writeObject(preferences.all)
                        output.flush()
                    }
                } catch (e: IOException) {
                    if (DEBUG) {
                        Log.e(TAG, "Unable to exportDatabase", e)
                    }
                }

                ZipHelper.addFileToZip(outZip, fileLocator.settings.path, "newpipe.settings")
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
        val success = ZipHelper.extractFileFromZip(file, fileLocator.db.path, "newpipe.db")
        if (success) {
            fileLocator.dbJournal.delete()
            fileLocator.dbWal.delete()
            fileLocator.dbShm.delete()
        }

        return success
    }

    fun extractSettings(file: StoredFileHelper): Boolean {
        return ZipHelper.extractFileFromZip(file, fileLocator.settings.path, "newpipe.settings")
    }

    /**
     * Remove all shared preferences from the app and load the preferences supplied to the manager.
     */
    fun loadSharedPreferences(preferences: SharedPreferences) {
        try {
            val preferenceEditor = preferences.edit()

            ObjectInputStream(fileLocator.settings.inputStream()).use { input ->
                preferenceEditor.clear()
                @Suppress("UNCHECKED_CAST")
                val entries = input.readObject() as Map<String, *>
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
                    }
                }
                preferenceEditor.commit()
            }
        } catch (e: IOException) {
            if (DEBUG) {
                Log.e(TAG, "Unable to loadSharedPreferences", e)
            }
        } catch (e: ClassNotFoundException) {
            if (DEBUG) {
                Log.e(TAG, "Unable to loadSharedPreferences", e)
            }
        }
    }
}
