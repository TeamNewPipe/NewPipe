/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.preferences.VideoAudioPreferences
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class VideoAudioSettingsViewModel(private val settings: Settings) : ViewModel() {

    val defaultResolution: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                VideoAudioPreferences.KEY_DEFAULT_RESOLUTION,
                VideoAudioPreferences.DEFAULT_RESOLUTION
            )
        )

    val defaultPopupResolution: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                VideoAudioPreferences.KEY_DEFAULT_POPUP_RESOLUTION,
                VideoAudioPreferences.DEFAULT_POPUP_RESOLUTION
            )
        )

    val mobileDataResolution: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                VideoAudioPreferences.KEY_LIMIT_MOBILE_DATA_USAGE,
                VideoAudioPreferences.DEFAULT_LIMIT_MOBILE_DATA_USAGE
            )
        )

    val showHigherResolutions: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                VideoAudioPreferences.KEY_SHOW_HIGHER_RESOLUTIONS,
                VideoAudioPreferences.DEFAULT_SHOW_HIGHER_RESOLUTIONS
            )
        )

    val videoFormat: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                VideoAudioPreferences.KEY_DEFAULT_VIDEO_FORMAT,
                VideoAudioPreferences.DEFAULT_VIDEO_FORMAT
            )
        )

    val audioFormat: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                VideoAudioPreferences.KEY_DEFAULT_AUDIO_FORMAT,
                VideoAudioPreferences.DEFAULT_AUDIO_FORMAT
            )
        )

    val preferOriginalAudio: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                VideoAudioPreferences.KEY_PREFER_ORIGINAL_AUDIO,
                VideoAudioPreferences.DEFAULT_PREFER_ORIGINAL_AUDIO
            )
        )

    val preferDescriptiveAudio: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                VideoAudioPreferences.KEY_PREFER_DESCRIPTIVE_AUDIO,
                VideoAudioPreferences.DEFAULT_PREFER_DESCRIPTIVE_AUDIO
            )
        )

    fun setDefaultResolution(value: String) {
        settings.putString(VideoAudioPreferences.KEY_DEFAULT_RESOLUTION, value)
        defaultResolution.value = value
    }

    fun setDefaultPopupResolution(value: String) {
        settings.putString(VideoAudioPreferences.KEY_DEFAULT_POPUP_RESOLUTION, value)
        defaultPopupResolution.value = value
    }

    fun setMobileDataResolution(value: String) {
        settings.putString(VideoAudioPreferences.KEY_LIMIT_MOBILE_DATA_USAGE, value)
        mobileDataResolution.value = value
    }

    fun setShowHigherResolutions(value: Boolean) {
        settings.putBoolean(VideoAudioPreferences.KEY_SHOW_HIGHER_RESOLUTIONS, value)
        showHigherResolutions.value = value

        if (!value) {
            if (defaultResolution.value in VideoAudioPreferences.HIGH_RESOLUTIONS) {
                setDefaultResolution(VideoAudioPreferences.BEST_RESOLUTION)
            }
            if (defaultPopupResolution.value in VideoAudioPreferences.HIGH_RESOLUTIONS) {
                setDefaultPopupResolution(VideoAudioPreferences.BEST_RESOLUTION)
            }
            if (mobileDataResolution.value in VideoAudioPreferences.HIGH_RESOLUTIONS) {
                setMobileDataResolution(VideoAudioPreferences.LIMIT_DATA_USAGE_NONE)
            }
        }
    }

    fun setVideoFormat(value: String) {
        settings.putString(VideoAudioPreferences.KEY_DEFAULT_VIDEO_FORMAT, value)
        videoFormat.value = value
    }

    fun setAudioFormat(value: String) {
        settings.putString(VideoAudioPreferences.KEY_DEFAULT_AUDIO_FORMAT, value)
        audioFormat.value = value
    }

    fun setPreferOriginalAudio(value: Boolean) {
        settings.putBoolean(VideoAudioPreferences.KEY_PREFER_ORIGINAL_AUDIO, value)
        preferOriginalAudio.value = value
    }

    fun setPreferDescriptiveAudio(value: Boolean) {
        settings.putBoolean(VideoAudioPreferences.KEY_PREFER_DESCRIPTIVE_AUDIO, value)
        preferDescriptiveAudio.value = value
    }
}
