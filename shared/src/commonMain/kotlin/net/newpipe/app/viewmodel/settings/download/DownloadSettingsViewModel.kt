/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.platform.DownloadActions
import net.newpipe.app.screen.settings.BooleanPreference
import org.koin.core.annotation.KoinViewModel

// Keys mirror app/src/main/res/values/settings_keys.xml — keep in sync.
private const val DOWNLOADS_STORAGE_ASK_KEY = "downloads_storage_ask"
private const val STORAGE_USE_SAF_KEY = "storage_use_saf"
private const val DOWNLOAD_PATH_VIDEO_KEY = "download_path_video_key"
private const val DOWNLOAD_PATH_AUDIO_KEY = "download_path_audio_key"

@KoinViewModel
class DownloadSettingsViewModel(
    private val settings: ObservableSettings,
    private val actions: DownloadActions
) : ViewModel() {

    private val askPref = BooleanPreference(DOWNLOADS_STORAGE_ASK_KEY, false, settings, viewModelScope)
    private val useSafPref = BooleanPreference(STORAGE_USE_SAF_KEY, true, settings, viewModelScope)

    private val _videoPath = MutableStateFlow(settings.getStringOrNull(DOWNLOAD_PATH_VIDEO_KEY).orEmpty())
    private val _audioPath = MutableStateFlow(settings.getStringOrNull(DOWNLOAD_PATH_AUDIO_KEY).orEmpty())

    val storageAsk = askPref.state
    val useSaf = useSafPref.state
    val videoPath: StateFlow<String> = _videoPath.asStateFlow()
    val audioPath: StateFlow<String> = _audioPath.asStateFlow()

    fun toggleStorageAsk(v: Boolean) = askPref.toggle(v)
    fun toggleUseSaf(v: Boolean) = useSafPref.toggle(v)

    fun pickVideoPath() = actions.pickDirectory { uri ->
        settings.putString(DOWNLOAD_PATH_VIDEO_KEY, uri)
        _videoPath.value = uri
    }

    fun pickAudioPath() = actions.pickDirectory { uri ->
        settings.putString(DOWNLOAD_PATH_AUDIO_KEY, uri)
        _audioPath.value = uri
    }
}