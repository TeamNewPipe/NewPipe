/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.DownloadStorageActions
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_DOWNLOAD_FOLDER
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_FILE_CHARSET
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_LIMIT_QUEUE
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_MAXIMUM_RETRIES
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_PAUSE_ON_METERED_NETWORK
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_REPLACEMENT_CHARACTER
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_STORAGE_ASK
import net.newpipe.app.preferences.DownloadPreferences.KEY_DOWNLOAD_FOLDER_AUDIO
import net.newpipe.app.preferences.DownloadPreferences.KEY_DOWNLOAD_FOLDER_VIDEO
import net.newpipe.app.preferences.DownloadPreferences.KEY_FILE_CHARSET
import net.newpipe.app.preferences.DownloadPreferences.KEY_LIMIT_QUEUE
import net.newpipe.app.preferences.DownloadPreferences.KEY_MAXIMUM_RETRIES
import net.newpipe.app.preferences.DownloadPreferences.KEY_PAUSE_ON_METERED_NETWORK
import net.newpipe.app.preferences.DownloadPreferences.KEY_REPLACEMENT_CHARACTER
import net.newpipe.app.preferences.DownloadPreferences.KEY_STORAGE_ASK
import net.newpipe.app.preferences.DownloadPreferences.KEY_STORAGE_USE_SAF
import net.newpipe.app.preferences.DownloadPreferences.normalizedMaximumRetries
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DownloadSettingsViewModel(
    private val settings: Settings,
    private val storage: DownloadStorageActions
) : ViewModel() {

    val storageAsk: StateFlow<Boolean>
        field = booleanFlow(KEY_STORAGE_ASK, DEFAULT_STORAGE_ASK)

    val useStorageAccessFramework: StateFlow<Boolean>
        field = MutableStateFlow(storage.useStorageAccessFramework)

    val videoDownloadFolder: StateFlow<String>
        field = stringFlow(KEY_DOWNLOAD_FOLDER_VIDEO, DEFAULT_DOWNLOAD_FOLDER)

    val audioDownloadFolder: StateFlow<String>
        field = stringFlow(KEY_DOWNLOAD_FOLDER_AUDIO, DEFAULT_DOWNLOAD_FOLDER)

    val fileCharset: StateFlow<String>
        field = stringFlow(KEY_FILE_CHARSET, DEFAULT_FILE_CHARSET)

    val replacementCharacter: StateFlow<String>
        field = stringFlow(KEY_REPLACEMENT_CHARACTER, DEFAULT_REPLACEMENT_CHARACTER)

    val maximumRetries: StateFlow<String>
        field = MutableStateFlow(repairedMaximumRetries())

    val pauseOnMeteredNetwork: StateFlow<Boolean>
        field = booleanFlow(
            KEY_PAUSE_ON_METERED_NETWORK,
            DEFAULT_PAUSE_ON_METERED_NETWORK
        )

    val limitQueue: StateFlow<Boolean>
        field = booleanFlow(KEY_LIMIT_QUEUE, DEFAULT_LIMIT_QUEUE)

    fun setStorageAsk(value: Boolean) = storageAsk
        .persist(KEY_STORAGE_ASK, value)

    fun setUseStorageAccessFramework(value: Boolean) {
        useStorageAccessFramework.persist(KEY_STORAGE_USE_SAF, value)

        if (value) {
            setVideoDownloadFolder(DEFAULT_DOWNLOAD_FOLDER)
            setAudioDownloadFolder(DEFAULT_DOWNLOAD_FOLDER)
            return
        }

        val defaults = storage.defaultDownloadFolders()
        if (videoDownloadFolder.value.isEmpty()) {
            setVideoDownloadFolder(defaults.video)
        }
        if (audioDownloadFolder.value.isEmpty()) {
            setAudioDownloadFolder(defaults.audio)
        }
    }

    fun setVideoDownloadFolder(value: String) = videoDownloadFolder
        .persistFolder(KEY_DOWNLOAD_FOLDER_VIDEO, value)

    fun setAudioDownloadFolder(value: String) = audioDownloadFolder
        .persistFolder(KEY_DOWNLOAD_FOLDER_AUDIO, value)

    fun setFileCharset(value: String) = fileCharset
        .persist(KEY_FILE_CHARSET, value)

    fun setReplacementCharacter(value: String) = replacementCharacter
        .persist(KEY_REPLACEMENT_CHARACTER, value)

    fun setMaximumRetries(value: String) = maximumRetries
        .persist(KEY_MAXIMUM_RETRIES, value)

    fun setPauseOnMeteredNetwork(value: Boolean) = pauseOnMeteredNetwork
        .persist(KEY_PAUSE_ON_METERED_NETWORK, value)

    fun setLimitQueue(value: Boolean) = limitQueue.persist(KEY_LIMIT_QUEUE, value)

    private fun stringFlow(key: String, defaultValue: String) = MutableStateFlow(
        settings.getString(key, defaultValue)
    )

    private fun booleanFlow(key: String, defaultValue: Boolean) = MutableStateFlow(
        settings.getBoolean(key, defaultValue)
    )

    private fun MutableStateFlow<String>.persist(key: String, newValue: String) {
        settings.putString(key, newValue)
        value = newValue
    }

    private fun MutableStateFlow<Boolean>.persist(key: String, newValue: Boolean) {
        settings.putBoolean(key, newValue)
        value = newValue
    }

    private fun MutableStateFlow<String>.persistFolder(key: String, newValue: String) {
        if (newValue.isEmpty()) {
            settings.remove(key)
        } else {
            settings.putString(key, newValue)
        }
        value = newValue
    }

    private fun repairedMaximumRetries(): String {
        val stored = settings.getString(KEY_MAXIMUM_RETRIES, DEFAULT_MAXIMUM_RETRIES)
        val repaired = normalizedMaximumRetries(stored)
        if (repaired != stored) {
            settings.putString(KEY_MAXIMUM_RETRIES, repaired)
        }
        return repaired
    }
}
