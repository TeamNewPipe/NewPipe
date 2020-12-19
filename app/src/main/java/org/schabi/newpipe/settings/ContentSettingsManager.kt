package org.schabi.newpipe.settings

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.schabi.newpipe.util.ZipHelper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ContentSettingsManager(
        private val databasesDir: File,
        private val newpipeDb: File,
        private val newpipeDbJournal: File,
        private var newpipeDbShm: File,
        private val newpipeDbWal: File,
        private val newpipeSettings: File,
) {

    constructor(homeDir: File) : this(
            File(homeDir, "/databases"),
            File(homeDir, "/databases/newpipe.db"),
            File(homeDir, "/databases/newpipe.db-journal"),
            File(homeDir, "/databases/newpipe.db-shm"),
            File(homeDir, "/databases/newpipe.db-wal"),
            File(homeDir, "/databases/newpipe.settings")
    )

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, outputPath: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputPath)))
            .use { outZip ->
                ZipHelper.addFileToZip(outZip, newpipeDb.path, "newpipe.db")

                try {
                    ObjectOutputStream(FileOutputStream(newpipeSettings)).use { output ->
                        output.writeObject(preferences.all)
                        output.flush()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                ZipHelper.addFileToZip(outZip, newpipeSettings.path, "newpipe.settings")
            }
    }

    fun isValidZipFile(filePath: String): Boolean {
        try {
            ZipFile(filePath).use {
                return@isValidZipFile true
            }
        } catch (ioe: IOException) {
            return false
        }
    }

    /**
     * Tries to create database directory if it does not exist.
     *
     * @return Whether the directory exists afterwards.
     */
    fun ensureDbDirectoryExists(): Boolean {
        return !databasesDir.exists() && !databasesDir.mkdir()
    }

    fun extractDb(filePath: String): Boolean {
        val success = ZipHelper.extractFileFromZip(filePath, newpipeDb.path, "newpipe.db")
        if (success) {
            newpipeDbJournal.delete()
            newpipeDbWal.delete()
            newpipeDbShm.delete()
        }

        return success
    }

    fun containSettings(filePath: String): Boolean {
        return ZipHelper
                .extractFileFromZip(filePath, newpipeSettings.path, "newpipe.settings")
    }

    fun loadSharedPreferences(preferences: SharedPreferences) {
        try {
            val preferenceEditor = preferences.edit()

            ObjectInputStream(FileInputStream(newpipeSettings)).use { input ->
                preferenceEditor.clear()
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
                    }
                }
                preferenceEditor.commit()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }

}
