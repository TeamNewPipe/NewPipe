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
import net.newpipe.app.preferences.VideoAudioPreferences.BEST_RESOLUTION
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_AUDIO_FORMAT
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_AUTOPLAY
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_AUTO_QUEUE
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_CLEAR_QUEUE_CONFIRMATION
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_IGNORE_HARDWARE_MEDIA_BUTTONS
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_LEFT_GESTURE_CONTROL
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_LIMIT_MOBILE_DATA_USAGE
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_MINIMIZE_ON_EXIT
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_POPUP_REMEMBER_SIZE_POS
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_POPUP_RESOLUTION
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_PREFERRED_OPEN_ACTION
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_PREFER_DESCRIPTIVE_AUDIO
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_PREFER_ORIGINAL_AUDIO
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_RESOLUTION
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_RESUME_ON_AUDIO_FOCUS_GAIN
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_RIGHT_GESTURE_CONTROL
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_SEEKBAR_PREVIEW_THUMBNAIL
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_SEEK_DURATION_MS
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_SHOW_HIGHER_RESOLUTIONS
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_SHOW_PLAY_WITH_KODI
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_START_MAIN_PLAYER_FULLSCREEN
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_USE_EXTERNAL_AUDIO_PLAYER
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_USE_EXTERNAL_VIDEO_PLAYER
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_USE_INEXACT_SEEK
import net.newpipe.app.preferences.VideoAudioPreferences.DEFAULT_VIDEO_FORMAT
import net.newpipe.app.preferences.VideoAudioPreferences.HIGH_RESOLUTIONS
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_AUTOPLAY
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_AUTO_QUEUE
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_CLEAR_QUEUE_CONFIRMATION
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_DEFAULT_AUDIO_FORMAT
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_DEFAULT_POPUP_RESOLUTION
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_DEFAULT_RESOLUTION
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_DEFAULT_VIDEO_FORMAT
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_IGNORE_HARDWARE_MEDIA_BUTTONS
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_LEFT_GESTURE_CONTROL
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_LIMIT_MOBILE_DATA_USAGE
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_POPUP_REMEMBER_SIZE_POS
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_PREFERRED_OPEN_ACTION
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_PREFER_DESCRIPTIVE_AUDIO
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_PREFER_ORIGINAL_AUDIO
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_RESUME_ON_AUDIO_FOCUS_GAIN
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_RIGHT_GESTURE_CONTROL
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_SEEKBAR_PREVIEW_THUMBNAIL
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_SEEK_DURATION
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_SHOW_HIGHER_RESOLUTIONS
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_SHOW_PLAY_WITH_KODI
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_START_MAIN_PLAYER_FULLSCREEN
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_USE_EXTERNAL_AUDIO_PLAYER
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_USE_EXTERNAL_VIDEO_PLAYER
import net.newpipe.app.preferences.VideoAudioPreferences.KEY_USE_INEXACT_SEEK
import net.newpipe.app.preferences.VideoAudioPreferences.LIMIT_DATA_USAGE_NONE
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class VideoAudioSettingsViewModel(private val settings: Settings) : ViewModel() {

    val defaultResolution: StateFlow<String>
        field = stringFlow(KEY_DEFAULT_RESOLUTION, DEFAULT_RESOLUTION)

    val defaultPopupResolution: StateFlow<String>
        field = stringFlow(KEY_DEFAULT_POPUP_RESOLUTION, DEFAULT_POPUP_RESOLUTION)

    val mobileDataResolution: StateFlow<String>
        field = stringFlow(KEY_LIMIT_MOBILE_DATA_USAGE, DEFAULT_LIMIT_MOBILE_DATA_USAGE)

    val showHigherResolutions: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_HIGHER_RESOLUTIONS, DEFAULT_SHOW_HIGHER_RESOLUTIONS)

    val videoFormat: StateFlow<String>
        field = stringFlow(KEY_DEFAULT_VIDEO_FORMAT, DEFAULT_VIDEO_FORMAT)

    val audioFormat: StateFlow<String>
        field = stringFlow(KEY_DEFAULT_AUDIO_FORMAT, DEFAULT_AUDIO_FORMAT)

    val preferOriginalAudio: StateFlow<Boolean>
        field = booleanFlow(KEY_PREFER_ORIGINAL_AUDIO, DEFAULT_PREFER_ORIGINAL_AUDIO)

    val preferDescriptiveAudio: StateFlow<Boolean>
        field = booleanFlow(KEY_PREFER_DESCRIPTIVE_AUDIO, DEFAULT_PREFER_DESCRIPTIVE_AUDIO)

    val useExternalVideoPlayer: StateFlow<Boolean>
        field = booleanFlow(KEY_USE_EXTERNAL_VIDEO_PLAYER, DEFAULT_USE_EXTERNAL_VIDEO_PLAYER)

    val useExternalAudioPlayer: StateFlow<Boolean>
        field = booleanFlow(KEY_USE_EXTERNAL_AUDIO_PLAYER, DEFAULT_USE_EXTERNAL_AUDIO_PLAYER)

    val showPlayWithKodi: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_PLAY_WITH_KODI, DEFAULT_SHOW_PLAY_WITH_KODI)

    val seekbarPreviewThumbnail: StateFlow<String>
        field = stringFlow(KEY_SEEKBAR_PREVIEW_THUMBNAIL, DEFAULT_SEEKBAR_PREVIEW_THUMBNAIL)

    val preferredOpenAction: StateFlow<String>
        field = stringFlow(KEY_PREFERRED_OPEN_ACTION, DEFAULT_PREFERRED_OPEN_ACTION)

    val minimizeOnExit: StateFlow<String>
        field = stringFlow(KEY_MINIMIZE_ON_EXIT, DEFAULT_MINIMIZE_ON_EXIT)

    val startMainPlayerFullscreen: StateFlow<Boolean>
        field = booleanFlow(KEY_START_MAIN_PLAYER_FULLSCREEN, DEFAULT_START_MAIN_PLAYER_FULLSCREEN)

    val autoplay: StateFlow<String>
        field = stringFlow(KEY_AUTOPLAY, DEFAULT_AUTOPLAY)

    val autoQueue: StateFlow<Boolean>
        field = booleanFlow(KEY_AUTO_QUEUE, DEFAULT_AUTO_QUEUE)

    val resumeOnAudioFocusGain: StateFlow<Boolean>
        field = booleanFlow(KEY_RESUME_ON_AUDIO_FOCUS_GAIN, DEFAULT_RESUME_ON_AUDIO_FOCUS_GAIN)

    val leftGestureControl: StateFlow<String>
        field = stringFlow(KEY_LEFT_GESTURE_CONTROL, DEFAULT_LEFT_GESTURE_CONTROL)

    val rightGestureControl: StateFlow<String>
        field = stringFlow(KEY_RIGHT_GESTURE_CONTROL, DEFAULT_RIGHT_GESTURE_CONTROL)

    val popupRememberSizePos: StateFlow<Boolean>
        field = booleanFlow(KEY_POPUP_REMEMBER_SIZE_POS, DEFAULT_POPUP_REMEMBER_SIZE_POS)

    val useInexactSeek: StateFlow<Boolean>
        field = booleanFlow(KEY_USE_INEXACT_SEEK, DEFAULT_USE_INEXACT_SEEK)

    val seekDuration: StateFlow<String>
        field = stringFlow(KEY_SEEK_DURATION, DEFAULT_SEEK_DURATION_MS)

    val clearQueueConfirmation: StateFlow<Boolean>
        field = booleanFlow(KEY_CLEAR_QUEUE_CONFIRMATION, DEFAULT_CLEAR_QUEUE_CONFIRMATION)

    val ignoreHardwareMediaButtons: StateFlow<Boolean>
        field = booleanFlow(
            KEY_IGNORE_HARDWARE_MEDIA_BUTTONS,
            DEFAULT_IGNORE_HARDWARE_MEDIA_BUTTONS
        )

    fun setDefaultResolution(value: String) =
        defaultResolution.persist(KEY_DEFAULT_RESOLUTION, value)

    fun setDefaultPopupResolution(value: String) =
        defaultPopupResolution.persist(KEY_DEFAULT_POPUP_RESOLUTION, value)

    fun setMobileDataResolution(value: String) =
        mobileDataResolution.persist(KEY_LIMIT_MOBILE_DATA_USAGE, value)

    fun setShowHigherResolutions(value: Boolean) {
        showHigherResolutions.persist(KEY_SHOW_HIGHER_RESOLUTIONS, value)

        if (!value) {
            if (defaultResolution.value in HIGH_RESOLUTIONS) {
                setDefaultResolution(BEST_RESOLUTION)
            }
            if (defaultPopupResolution.value in HIGH_RESOLUTIONS) {
                setDefaultPopupResolution(BEST_RESOLUTION)
            }
            if (mobileDataResolution.value in HIGH_RESOLUTIONS) {
                setMobileDataResolution(LIMIT_DATA_USAGE_NONE)
            }
        }
    }

    fun setVideoFormat(value: String) = videoFormat.persist(KEY_DEFAULT_VIDEO_FORMAT, value)

    fun setAudioFormat(value: String) = audioFormat.persist(KEY_DEFAULT_AUDIO_FORMAT, value)

    fun setPreferOriginalAudio(value: Boolean) = preferOriginalAudio
        .persist(KEY_PREFER_ORIGINAL_AUDIO, value)

    fun setPreferDescriptiveAudio(value: Boolean) = preferDescriptiveAudio
        .persist(KEY_PREFER_DESCRIPTIVE_AUDIO, value)

    fun setUseExternalVideoPlayer(value: Boolean) = useExternalVideoPlayer
        .persist(KEY_USE_EXTERNAL_VIDEO_PLAYER, value)

    fun setUseExternalAudioPlayer(value: Boolean) = useExternalAudioPlayer
        .persist(KEY_USE_EXTERNAL_AUDIO_PLAYER, value)

    fun setShowPlayWithKodi(value: Boolean) = showPlayWithKodi
        .persist(KEY_SHOW_PLAY_WITH_KODI, value)

    fun setSeekbarPreviewThumbnail(value: String) = seekbarPreviewThumbnail
        .persist(KEY_SEEKBAR_PREVIEW_THUMBNAIL, value)

    fun setPreferredOpenAction(value: String) = preferredOpenAction
        .persist(KEY_PREFERRED_OPEN_ACTION, value)

    fun setMinimizeOnExit(value: String) = minimizeOnExit
        .persist(KEY_MINIMIZE_ON_EXIT, value)

    fun setStartMainPlayerFullscreen(value: Boolean) = startMainPlayerFullscreen
        .persist(KEY_START_MAIN_PLAYER_FULLSCREEN, value)

    fun setAutoplay(value: String) = autoplay.persist(KEY_AUTOPLAY, value)

    fun setAutoQueue(value: Boolean) = autoQueue.persist(KEY_AUTO_QUEUE, value)

    fun setResumeOnAudioFocusGain(value: Boolean) = resumeOnAudioFocusGain
        .persist(KEY_RESUME_ON_AUDIO_FOCUS_GAIN, value)

    fun setLeftGestureControl(value: String) = leftGestureControl
        .persist(KEY_LEFT_GESTURE_CONTROL, value)

    fun setRightGestureControl(value: String) = rightGestureControl
        .persist(KEY_RIGHT_GESTURE_CONTROL, value)

    fun setPopupRememberSizePos(value: Boolean) = popupRememberSizePos
        .persist(KEY_POPUP_REMEMBER_SIZE_POS, value)

    /**
     * Enabling inexact seek hides 5-second-step durations. If the current selection becomes
     * hidden it is bumped by 5 seconds, matching the legacy screen (which also showed a
     * toast; the screen shows a snackbar based on the same
     * [VideoAudioPreferences.adjustedSeekDurationMs]).
     */
    fun setUseInexactSeek(value: Boolean) {
        useInexactSeek.persist(KEY_USE_INEXACT_SEEK, value)
        VideoAudioPreferences.adjustedSeekDurationMs(seekDuration.value, value)
            ?.let(::setSeekDuration)
    }

    fun setSeekDuration(value: String) = seekDuration.persist(KEY_SEEK_DURATION, value)

    fun setClearQueueConfirmation(value: Boolean) = clearQueueConfirmation
        .persist(KEY_CLEAR_QUEUE_CONFIRMATION, value)

    fun setIgnoreHardwareMediaButtons(value: Boolean) = ignoreHardwareMediaButtons
        .persist(KEY_IGNORE_HARDWARE_MEDIA_BUTTONS, value)

    private fun stringFlow(key: String, defaultValue: String) = 
        MutableStateFlow(settings.getString(key, defaultValue))

    private fun booleanFlow(key: String, defaultValue: Boolean) = 
        MutableStateFlow(settings.getBoolean(key, defaultValue))

    private fun MutableStateFlow<String>.persist(key: String, newValue: String) {
        settings.putString(key, newValue)
        value = newValue
    }

    private fun MutableStateFlow<Boolean>.persist(key: String, newValue: Boolean) {
        settings.putBoolean(key, newValue)
        value = newValue
    }
}
