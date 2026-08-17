package org.schabi.newpipe.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import java.io.File
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty
import org.schabi.newpipe.settings.migration.MigrationManager
import org.schabi.newpipe.util.DeviceUtils

/**
 * Helper class for global settings.
 */
object NewPipeSettings {

    @JvmStatic
    fun initSettings(context: Context) {
        // first run migrations, then setDefaultValues, since the latter requires the correct types
        MigrationManager.runMigrationsIfNeeded(context)

        // readAgain is true so that if new settings are added their default value is set
        /*
        PreferenceManager.setDefaultValues(context, R.xml.main_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.video_audio_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.download_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.appearance_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.history_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.content_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.player_notification_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.update_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.debug_settings, true)
        PreferenceManager.setDefaultValues(context, R.xml.backup_restore_settings, true)
        */

        saveDefaultVideoDownloadDirectory(context)
        saveDefaultAudioDownloadDirectory(context)

        disableMediaTunnelingIfNecessary(context)
    }

    @JvmStatic
    fun saveDefaultVideoDownloadDirectory(context: Context) {
        saveDefaultDirectory(
            context,
            R.string.download_path_video_key,
            Environment.DIRECTORY_MOVIES
        )
    }

    @JvmStatic
    fun saveDefaultAudioDownloadDirectory(context: Context) {
        saveDefaultDirectory(
            context,
            R.string.download_path_audio_key,
            Environment.DIRECTORY_MUSIC
        )
    }

    private fun saveDefaultDirectory(
        context: Context,
        @StringRes keyID: Int,
        defaultDirectoryName: String
    ) {
        if (!useStorageAccessFramework(context)) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val key = context.getString(keyID)
            val downloadPath = prefs.getString(key, null)
            if (!isNullOrEmpty(downloadPath)) {
                return
            }

            prefs.edit()
                .putString(key, getNewPipeChildFolderPathForDir(getDir(defaultDirectoryName)))
                .apply()
        }
    }

    @JvmStatic
    fun getDir(defaultDirectoryName: String): File {
        return File(Environment.getExternalStorageDirectory(), defaultDirectoryName)
    }

    private fun getNewPipeChildFolderPathForDir(dir: File): String {
        return File(dir, "NewPipe").toURI().toString()
    }

    @JvmStatic
    fun useStorageAccessFramework(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        } else if (DeviceUtils.isFireTv()) {
            // There's a FireOS bug which prevents SAF open/close dialogs from being confirmed with
            // a remote (see #6455).
            return false
        }

        val key = context.getString(R.string.storage_use_saf)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        return prefs.getBoolean(key, true)
    }

    private fun showSearchSuggestions(
        context: Context,
        sharedPreferences: SharedPreferences,
        @StringRes key: Int
    ): Boolean {
        val enabledSearchSuggestions = sharedPreferences.getStringSet(
            context.getString(R.string.show_search_suggestions_key),
            null
        )

        return if (enabledSearchSuggestions == null) {
            true // defaults to true
        } else {
            enabledSearchSuggestions.contains(context.getString(key))
        }
    }

    @JvmStatic
    fun showLocalSearchSuggestions(
        context: Context,
        sharedPreferences: SharedPreferences
    ): Boolean {
        return showSearchSuggestions(
            context,
            sharedPreferences,
            R.string.show_local_search_suggestions_key
        )
    }

    @JvmStatic
    fun showRemoteSearchSuggestions(
        context: Context,
        sharedPreferences: SharedPreferences
    ): Boolean {
        return showSearchSuggestions(
            context,
            sharedPreferences,
            R.string.show_remote_search_suggestions_key
        )
    }

    private fun disableMediaTunnelingIfNecessary(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val disabledTunnelingKey = context.getString(R.string.disable_media_tunneling_key)
        val disabledTunnelingAutomaticallyKey =
            context.getString(R.string.disabled_media_tunneling_automatically_key)
        val blacklistVersionKey =
            context.getString(R.string.media_tunneling_device_blacklist_version)

        val lastMediaTunnelingUpdate = prefs.getInt(blacklistVersionKey, 0)
        val wasDeviceBlacklistUpdated =
            DeviceUtils.MEDIA_TUNNELING_DEVICE_BLACKLIST_VERSION != lastMediaTunnelingUpdate
        val wasMediaTunnelingEnabledByUser =
            prefs.getInt(disabledTunnelingAutomaticallyKey, -1) == 0 &&
                !prefs.getBoolean(disabledTunnelingKey, false)

        if (App.instance.isFirstRun ||
            (wasDeviceBlacklistUpdated && !wasMediaTunnelingEnabledByUser)
        ) {
            setMediaTunneling(context)
        }
    }

    /**
     * Check if device does not support media tunneling
     * and disable that exoplayer feature if necessary.
     * @see DeviceUtils.shouldSupportMediaTunneling
     * @param context
     */
    @JvmStatic
    fun setMediaTunneling(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!DeviceUtils.shouldSupportMediaTunneling()) {
            prefs.edit()
                .putBoolean(context.getString(R.string.disable_media_tunneling_key), true)
                .putInt(
                    context.getString(
                        R.string.disabled_media_tunneling_automatically_key
                    ),
                    1
                )
                .putInt(
                    context.getString(R.string.media_tunneling_device_blacklist_version),
                    DeviceUtils.MEDIA_TUNNELING_DEVICE_BLACKLIST_VERSION
                )
                .apply()
        } else {
            prefs.edit()
                .putInt(
                    context.getString(R.string.media_tunneling_device_blacklist_version),
                    DeviceUtils.MEDIA_TUNNELING_DEVICE_BLACKLIST_VERSION
                ).apply()
        }
    }
}
