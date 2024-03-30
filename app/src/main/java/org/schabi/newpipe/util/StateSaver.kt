/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * StateSaver.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.util

import android.content.Context
import android.util.Log
import androidx.core.os.BundleCompat
import org.schabi.newpipe.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap

/**
 * A way to save state to disk or in a in-memory map
 * if it's just changing configurations (i.e. rotating the phone).
 */
object StateSaver {
    const val KEY_SAVED_STATE = "key_saved_state"
    private val STATE_OBJECTS_HOLDER = ConcurrentHashMap<String, Queue<Any>>()
    private const val TAG = "StateSaver"
    private const val CACHE_DIR_NAME = "state_cache"
    private var cacheDirPath: String? = null

    /**
     * Initialize the StateSaver, usually you want to call this in the Application class.
     *
     * @param context used to get the available cache dir
     */
    fun init(context: Context) {
        val externalCacheDir = context.externalCacheDir
        if (externalCacheDir != null) {
            cacheDirPath = externalCacheDir.absolutePath
        }
        if (TextUtils.isEmpty(cacheDirPath)) {
            cacheDirPath = context.cacheDir.absolutePath
        }
    }

    /**
     * @param outState
     * @param writeRead
     * @return the saved state
     * @see .tryToRestore
     */
    fun tryToRestore(outState: Bundle?, writeRead: WriteRead?): SavedState? {
        if (outState == null || writeRead == null) {
            return null
        }
        val savedState = BundleCompat.getParcelable(
                outState, KEY_SAVED_STATE, SavedState::class.java)
                ?: return null
        return tryToRestore(savedState, writeRead)
    }

    /**
     * Try to restore the state from memory and disk,
     * using the [StateSaver.WriteRead.readFrom] from the writeRead.
     *
     * @param savedState
     * @param writeRead
     * @return the saved state
     */
    private fun tryToRestore(savedState: SavedState,
                             writeRead: WriteRead): SavedState? {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "tryToRestore() called with: savedState = [" + savedState + "], "
                    + "writeRead = [" + writeRead + "]")
        }
        try {
            var savedObjects = STATE_OBJECTS_HOLDER.remove(savedState.prefixFileSaved)!!
            if (savedObjects != null) {
                writeRead.readFrom(savedObjects)
                if (MainActivity.Companion.DEBUG) {
                    Log.d(TAG, "tryToSave: reading objects from holder > " + savedObjects
                            + ", stateObjectsHolder > " + STATE_OBJECTS_HOLDER)
                }
                return savedState
            }
            val file = File(savedState.pathFileSaved)
            if (!file.exists()) {
                if (MainActivity.Companion.DEBUG) {
                    Log.d(TAG, "Cache file doesn't exist: " + file.absolutePath)
                }
                return null
            }
            FileInputStream(file).use { fileInputStream -> ObjectInputStream(fileInputStream).use { inputStream -> savedObjects = inputStream.readObject() as Queue<Any> } }
            if (savedObjects != null) {
                writeRead.readFrom(savedObjects)
            }
            return savedState
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore state", e)
        }
        return null
    }

    /**
     * @param isChangingConfig
     * @param savedState
     * @param outState
     * @param writeRead
     * @return the saved state or `null`
     * @see .tryToSave
     */
    fun tryToSave(isChangingConfig: Boolean,
                  savedState: SavedState?, outState: Bundle,
                  writeRead: WriteRead): SavedState? {
        val currentSavedPrefix: String
        currentSavedPrefix = if (savedState == null || TextUtils.isEmpty(savedState.prefixFileSaved)) {
            // Generate unique prefix
            (System.nanoTime() - writeRead.hashCode()).toString() + ""
        } else {
            // Reuse prefix
            savedState.prefixFileSaved
        }
        val newSavedState = tryToSave(isChangingConfig, currentSavedPrefix,
                writeRead.generateSuffix(), writeRead)
        if (newSavedState != null) {
            outState.putParcelable(KEY_SAVED_STATE, newSavedState)
            return newSavedState
        }
        return null
    }

    /**
     * If it's not changing configuration (i.e. rotating screen),
     * try to write the state from [StateSaver.WriteRead.writeTo]
     * to the file with the name of prefixFileName + suffixFileName,
     * in a cache folder got from the [.init].
     *
     *
     * It checks if the file already exists and if it does, just return the path,
     * so a good way to save is:
     *
     *
     *  * A fixed prefix for the file
     *  * A changing suffix
     *
     *
     * @param isChangingConfig
     * @param prefixFileName
     * @param suffixFileName
     * @param writeRead
     * @return the saved state or `null`
     */
    private fun tryToSave(isChangingConfig: Boolean, prefixFileName: String,
                          suffixFileName: String?, writeRead: WriteRead): SavedState? {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "tryToSave() called with: "
                    + "isChangingConfig = [" + isChangingConfig + "], "
                    + "prefixFileName = [" + prefixFileName + "], "
                    + "suffixFileName = [" + suffixFileName + "], "
                    + "writeRead = [" + writeRead + "]")
        }
        val savedObjects = LinkedList<Any?>()
        writeRead.writeTo(savedObjects)
        if (isChangingConfig) {
            return if (savedObjects.size > 0) {
                STATE_OBJECTS_HOLDER[prefixFileName] = savedObjects
                SavedState(prefixFileName, "")
            } else {
                if (MainActivity.Companion.DEBUG) {
                    Log.d(TAG, "Nothing to save")
                }
                null
            }
        }
        try {
            var cacheDir = File(cacheDirPath)
            if (!cacheDir.exists()) {
                throw RuntimeException("Cache dir does not exist > " + cacheDirPath)
            }
            cacheDir = File(cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdir()) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG,
                                "Failed to create cache directory " + cacheDir.absolutePath)
                    }
                    return null
                }
            }
            val file = File(cacheDir, prefixFileName
                    + if (TextUtils.isEmpty(suffixFileName)) ".cache" else suffixFileName)
            if (file.exists() && file.length() > 0) {
                // If the file already exists, just return it
                return SavedState(prefixFileName, file.absolutePath)
            } else {
                // Delete any file that contains the prefix
                val files = cacheDir.listFiles { dir: File?, name: String -> name.contains(prefixFileName) }
                for (fileToDelete in files) {
                    fileToDelete.delete()
                }
            }
            FileOutputStream(file).use { fileOutputStream -> ObjectOutputStream(fileOutputStream).use { outputStream -> outputStream.writeObject(savedObjects) } }
            return SavedState(prefixFileName, file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state", e)
        }
        return null
    }

    /**
     * Delete the cache file contained in the savedState.
     * Also remove any possible-existing value in the memory-cache.
     *
     * @param savedState the saved state to delete
     */
    fun onDestroy(savedState: SavedState?) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onDestroy() called with: savedState = [$savedState]")
        }
        if (savedState != null && !savedState.pathFileSaved.isEmpty()) {
            STATE_OBJECTS_HOLDER.remove(savedState.prefixFileSaved)
            try {
                File(savedState.pathFileSaved).delete()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Clear all the files in cache (in memory and disk).
     */
    fun clearStateFiles() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "clearStateFiles() called")
        }
        STATE_OBJECTS_HOLDER.clear()
        var cacheDir = File(cacheDirPath)
        if (!cacheDir.exists()) {
            return
        }
        cacheDir = File(cacheDir, CACHE_DIR_NAME)
        if (cacheDir.exists()) {
            val list = cacheDir.listFiles()
            if (list != null) {
                for (file in list) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Used for describing how to save/read the objects.
     *
     *
     * Queue was chosen by its FIFO property.
     */
    interface WriteRead {
        /**
         * Generate a changing suffix that will name the cache file,
         * and be used to identify if it changed (thus reducing useless reading/saving).
         *
         * @return a unique value
         */
        fun generateSuffix(): String?

        /**
         * Add to this queue objects that you want to save.
         *
         * @param objectsToSave the objects to save
         */
        fun writeTo(objectsToSave: Queue<Any?>)

        /**
         * Poll saved objects from the queue in the order they were written.
         *
         * @param savedObjects queue of objects returned by [.writeTo]
         */
        @Throws(Exception::class)
        fun readFrom(savedObjects: Queue<Any>)
    }
}
