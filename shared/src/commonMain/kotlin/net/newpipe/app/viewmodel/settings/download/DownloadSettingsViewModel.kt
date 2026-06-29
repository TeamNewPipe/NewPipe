/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.DownloadActions
import net.newpipe.app.viewmodel.settings.BooleanPreference
import net.newpipe.app.viewmodel.settings.StringPreference
import org.koin.core.annotation.KoinViewModel

// Keys mirror app/src/main/res/values/settings_keys.xml — keep in sync.
private const val DOWNLOADS_STORAGE_ASK_KEY = "downloads_storage_ask"
private const val STORAGE_USE_SAF_KEY = "storage_use_saf"
private const val DOWNLOAD_PATH_VIDEO_KEY = "download_path_video_key"
private const val DOWNLOAD_PATH_AUDIO_KEY = "download_path_audio_key"

@KoinViewModel
class DownloadSettingsViewModel(
    settings: ObservableSettings,
    private val actions: DownloadActions
) : ViewModel() {

    private val askPref = BooleanPreference(
        DOWNLOADS_STORAGE_ASK_KEY, false, settings, viewModelScope
    )
    private val useSafPref = BooleanPreference(
        STORAGE_USE_SAF_KEY, true, settings, viewModelScope
    )
    private val videoPathPref = StringPreference(
        DOWNLOAD_PATH_VIDEO_KEY, "", settings, viewModelScope
    )
    private val audioPathPref = StringPreference(
        DOWNLOAD_PATH_AUDIO_KEY, "", settings, viewModelScope
    )

    val storageAsk: StateFlow<Boolean> = askPref.state
    val useSaf: StateFlow<Boolean> = useSafPref.state
    val videoPath: StateFlow<String> = videoPathPref.state
    val audioPath: StateFlow<String> = audioPathPref.state

    fun toggleStorageAsk(v: Boolean) = askPref.toggle(v)
    fun toggleUseSaf(v: Boolean) = useSafPref.toggle(v)

    fun pickVideoPath() = actions.pickDirectory(videoPathPref::set)
    fun pickAudioPath() = actions.pickDirectory(audioPathPref::set)
}