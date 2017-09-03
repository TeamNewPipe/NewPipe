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

package org.schabi.newpipe.util;


import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.schabi.newpipe.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A way to save state to disk or in a in-memory map if it's just changing configurations (i.e. rotating the phone).
 */
public class StateSaver {
    private static final ConcurrentHashMap<String, Queue<Object>> stateObjectsHolder = new ConcurrentHashMap<>();
    private static final String TAG = "StateSaver";
    private static final String CACHE_DIR_NAME = "state_cache";

    public static final String KEY_SAVED_STATE = "key_saved_state";
    private static String cacheDirPath;

    private StateSaver() {
        //no instance
    }

    /**
     * Initialize the StateSaver, usually you want to call this in the Application class
     *
     * @param context used to get the available cache dir
     */
    public static void init(Context context) {
        File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir != null) cacheDirPath = externalCacheDir.getAbsolutePath();
        if (TextUtils.isEmpty(cacheDirPath)) cacheDirPath = context.getCacheDir().getAbsolutePath();
    }

    /**
     * Used for describe how to save/read the objects.
     * <p>
     * Queue was chosen by its FIFO property.
     */
    public interface WriteRead {
        /**
         * Generate a changing suffix that will name the cache file,
         * and be used to identify if it changed (thus reducing useless reading/saving).
         *
         * @return a unique value
         */
        String generateSuffix();

        /**
         * Add to this queue objects that you want to save.
         */
        void writeTo(Queue<Object> objectsToSave);

        /**
         * Poll saved objects from the queue in the order they were written.
         *
         * @param savedObjects queue of objects returned by {@link #writeTo(Queue)}
         */
        void readFrom(@NonNull Queue<Object> savedObjects) throws Exception;
    }

    /**
     * @see #tryToRestore(SavedState, WriteRead)
     */
    public static SavedState tryToRestore(Bundle outState, WriteRead writeRead) {
        if (outState == null || writeRead == null) return null;

        SavedState savedState = outState.getParcelable(KEY_SAVED_STATE);
        if (savedState == null) return null;

        return tryToRestore(savedState, writeRead);
    }

    /**
     * Try to restore the state from memory and disk, using the {@link StateSaver.WriteRead#readFrom(Queue)} from the writeRead.
     */
    private static SavedState tryToRestore(@NonNull SavedState savedState, @NonNull WriteRead writeRead) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "tryToRestore() called with: savedState = [" + savedState + "], writeRead = [" + writeRead + "]");
        }

        FileInputStream fileInputStream = null;
        try {
            Queue<Object> savedObjects = stateObjectsHolder.remove(savedState.prefixFileSaved);
            if (savedObjects != null) {
                writeRead.readFrom(savedObjects);
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "tryToSave: reading objects from holder > " + savedObjects + ", stateObjectsHolder > " + stateObjectsHolder);
                }
                return savedState;
            }

            File file = new File(savedState.pathFileSaved);
            if (!file.exists()) return null;

            fileInputStream = new FileInputStream(file);
            ObjectInputStream inputStream = new ObjectInputStream(fileInputStream);
            //noinspection unchecked
            savedObjects = (Queue<Object>) inputStream.readObject();
            if (savedObjects != null) {
                writeRead.readFrom(savedObjects);
            }

            return savedState;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * @see #tryToSave(boolean, String, String, WriteRead)
     */
    public static SavedState tryToSave(boolean isChangingConfig, @Nullable SavedState savedState, Bundle outState, WriteRead writeRead) {
        String currentSavedPrefix = savedState == null || TextUtils.isEmpty(savedState.prefixFileSaved)
                ? System.nanoTime() - writeRead.hashCode() + ""
                : savedState.prefixFileSaved;

        savedState = tryToSave(isChangingConfig, currentSavedPrefix, writeRead.generateSuffix(), writeRead);
        if (savedState != null) {
            outState.putParcelable(StateSaver.KEY_SAVED_STATE, savedState);
            return savedState;
        }

        return null;
    }

    /**
     * If it's not changing configuration (i.e. rotating screen), try to write the state from {@link StateSaver.WriteRead#writeTo(Queue)}
     * to the file with the name of prefixFileName + suffixFileName, in a cache folder got from the {@link #init(Context)}.
     * <p>
     * It checks if the file already exists and if it does, just return the path, so a good way to save is:
     * <li> A fixed prefix for the file
     * <li> A changing suffix
     */
    private static SavedState tryToSave(boolean isChangingConfig, final String prefixFileName, String suffixFileName, WriteRead writeRead) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "tryToSave() called with: isChangingConfig = [" + isChangingConfig + "], prefixFileName = [" + prefixFileName + "], suffixFileName = [" + suffixFileName + "], writeRead = [" + writeRead + "]");
        }

        Queue<Object> savedObjects = new LinkedList<>();
        writeRead.writeTo(savedObjects);

        if (isChangingConfig) {
            if (savedObjects.size() > 0) {
                stateObjectsHolder.put(prefixFileName, savedObjects);
                return new SavedState(prefixFileName, "");
            } else return null;
        }

        FileOutputStream fileOutputStream = null;
        try {
            File cacheDir = new File(cacheDirPath);
            if (!cacheDir.exists()) throw new RuntimeException("Cache dir does not exist > " + cacheDirPath);
            cacheDir = new File(cacheDir, CACHE_DIR_NAME);
            if (!cacheDir.exists()) {
                boolean mkdirResult = cacheDir.mkdir();
                if (!mkdirResult) return null;
            }

            if (TextUtils.isEmpty(suffixFileName)) suffixFileName = ".cache";
            File file = new File(cacheDir, prefixFileName + suffixFileName);
            if (file.exists() && file.length() > 0) {
                // If the file already exists, just return it
                return new SavedState(prefixFileName, file.getAbsolutePath());
            } else {
                // Delete any file that contains the prefix
                File[] files = cacheDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.contains(prefixFileName);
                    }
                });
                for (File file1 : files) file1.delete();
            }

            fileOutputStream = new FileOutputStream(file);
            ObjectOutputStream outputStream = new ObjectOutputStream(fileOutputStream);
            outputStream.writeObject(savedObjects);

            return new SavedState(prefixFileName, file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * Delete the cache file contained in the savedState and remove any possible-existing value in the memory-cache.
     */
    public static void onDestroy(SavedState savedState) {
        if (MainActivity.DEBUG) Log.d(TAG, "onDestroy() called with: savedState = [" + savedState + "]");

        if (savedState != null && !TextUtils.isEmpty(savedState.pathFileSaved)) {
            stateObjectsHolder.remove(savedState.prefixFileSaved);
            try {
                //noinspection ResultOfMethodCallIgnored
                new File(savedState.pathFileSaved).delete();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Clear all the files in cache (in memory and disk).
     */
    public static void clearStateFiles() {
        if (MainActivity.DEBUG) Log.d(TAG, "clearStateFiles() called");

        stateObjectsHolder.clear();
        File cacheDir = new File(cacheDirPath);
        if (!cacheDir.exists()) return;

        cacheDir = new File(cacheDir, CACHE_DIR_NAME);
        if (cacheDir.exists()) {
            for (File file : cacheDir.listFiles()) file.delete();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Inner
    //////////////////////////////////////////////////////////////////////////*/

    public static class SavedState implements Parcelable {
        public String prefixFileSaved;
        public String pathFileSaved;

        public SavedState(String prefixFileSaved, String pathFileSaved) {
            this.prefixFileSaved = prefixFileSaved;
            this.pathFileSaved = pathFileSaved;
        }

        protected SavedState(Parcel in) {
            prefixFileSaved = in.readString();
            pathFileSaved = in.readString();
        }

        @Override
        public String toString() {
            return prefixFileSaved + " > " + pathFileSaved;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(prefixFileSaved);
            dest.writeString(pathFileSaved);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }


}
