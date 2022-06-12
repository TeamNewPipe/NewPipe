package org.schabi.newpipe.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty
import org.schabi.newpipe.util.DeviceUtils
import java.io.File

/*
 * Original Java class created by k3b on 07.01.2016.
 * Ported to Kotlin by handhimadrink on 06.12.2022.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * NewPipeSettings.kt is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Helper singleton for global settings.
 */
object NewPipeSettings {
    @JvmStatic fun initSettings(context: Context) {
        // check if there are entries in the prefs to determine whether this is the first app run
        var isFirstRun = true
        val prefKeys = PreferenceManager.getDefaultSharedPreferences(context).all.keys
        for (key in prefKeys) {
            // ACRA stores some info in the prefs during app initialization
            // which happens before this method is called. Therefore ignore ACRA-related keys.
            if (!key.startsWith("acra", ignoreCase = true)) {
                isFirstRun = false
                break
            }
        }
        // first run migrations, then setDefaultValues, since the latter requires the correct types
        SettingMigrations.initMigrations(context, isFirstRun)

        // readAgain is true so that if new settings are added their default value is set
        PreferenceManager.setDefaultValues(context, R.xml.main_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.video_audio_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.download_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.appearance_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.history_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.content_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.player_notification_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.update_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.debug_settings, true)

        saveDefaultVideoDownloadDirectory(context)
        saveDefaultAudioDownloadDirectory(context)
    }
    @JvmStatic fun saveDefaultVideoDownloadDirectory(context: Context) {
        saveDefaultDirectory(
            context, R.string.download_path_video_key,
            Environment.DIRECTORY_MOVIES
        )
    }

    @JvmStatic fun saveDefaultAudioDownloadDirectory(context: Context) {
        saveDefaultDirectory(
            context, R.string.download_path_audio_key,
            Environment.DIRECTORY_MUSIC
        )
    }

    @JvmStatic private fun saveDefaultDirectory(
        context: Context,
        keyID: Int,
        defaultDirectoryName: String
    ) {
        if (!useStorageAccessFramework(context)) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val key = context.getString(keyID)
            val downloadPath = prefs.getString(key, null)
            if (!isNullOrEmpty(downloadPath)) {
                return
            }

            val spEditor = prefs.edit()
            spEditor.putString(key, getNewPipeChildFolderPathForDir(getDir(defaultDirectoryName)))
            spEditor.apply()
        }
    }
    @JvmStatic fun getDir(defaultDirectoryName: String): File = File(Environment.getExternalStorageDirectory(), defaultDirectoryName)

    @JvmStatic fun getNewPipeChildFolderPathForDir(dir: File): String = File(dir, "NewPipe").toURI().toString()

    @JvmStatic fun useStorageAccessFramework(context: Context) = !(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || DeviceUtils.isFireTv()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.storage_use_saf), true)
// There's a FireOS bug which prevents SAF open/close dialogs from being confirmed with a
// remote (see #6455).

    @JvmStatic fun showSearchSuggestions(
        context: Context,
        sharedPreferences: SharedPreferences,
        @StringRes key: Int
    ) = sharedPreferences.getStringSet(context.getString(R.string.show_search_suggestions_key), null)?.contains(context.getString(key)) ?: true // defaults to true

    @JvmStatic fun showLocalSearchSuggestions(
        context: Context,
        sharedPreferences: SharedPreferences
    ): Boolean = showSearchSuggestions(context, sharedPreferences, R.string.show_local_search_suggestions_key)

    @JvmStatic fun showRemoteSearchSuggestions(
        context: Context,
        sharedPreferences: SharedPreferences
    ) = showSearchSuggestions(context, sharedPreferences, R.string.show_remote_search_suggestions_key)
}
