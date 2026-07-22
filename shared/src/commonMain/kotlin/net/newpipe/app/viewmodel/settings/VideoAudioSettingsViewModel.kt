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
        field = stringFlow(VideoAudioPreferences.KEY_DEFAULT_RESOLUTION, VideoAudioPreferences.DEFAULT_RESOLUTION)

    val defaultPopupResolution: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_DEFAULT_POPUP_RESOLUTION, VideoAudioPreferences.DEFAULT_POPUP_RESOLUTION)

    val mobileDataResolution: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_LIMIT_MOBILE_DATA_USAGE, VideoAudioPreferences.DEFAULT_LIMIT_MOBILE_DATA_USAGE)

    val showHigherResolutions: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_SHOW_HIGHER_RESOLUTIONS, VideoAudioPreferences.DEFAULT_SHOW_HIGHER_RESOLUTIONS)

    val videoFormat: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_DEFAULT_VIDEO_FORMAT, VideoAudioPreferences.DEFAULT_VIDEO_FORMAT)

    val audioFormat: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_DEFAULT_AUDIO_FORMAT, VideoAudioPreferences.DEFAULT_AUDIO_FORMAT)

    val preferOriginalAudio: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_PREFER_ORIGINAL_AUDIO, VideoAudioPreferences.DEFAULT_PREFER_ORIGINAL_AUDIO)

    val preferDescriptiveAudio: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_PREFER_DESCRIPTIVE_AUDIO, VideoAudioPreferences.DEFAULT_PREFER_DESCRIPTIVE_AUDIO)

    val useExternalVideoPlayer: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_USE_EXTERNAL_VIDEO_PLAYER, VideoAudioPreferences.DEFAULT_USE_EXTERNAL_VIDEO_PLAYER)

    val useExternalAudioPlayer: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_USE_EXTERNAL_AUDIO_PLAYER, VideoAudioPreferences.DEFAULT_USE_EXTERNAL_AUDIO_PLAYER)

    val showPlayWithKodi: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_SHOW_PLAY_WITH_KODI, VideoAudioPreferences.DEFAULT_SHOW_PLAY_WITH_KODI)

    val seekbarPreviewThumbnail: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_SEEKBAR_PREVIEW_THUMBNAIL, VideoAudioPreferences.DEFAULT_SEEKBAR_PREVIEW_THUMBNAIL)

    val preferredOpenAction: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_PREFERRED_OPEN_ACTION, VideoAudioPreferences.DEFAULT_PREFERRED_OPEN_ACTION)

    val minimizeOnExit: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT, VideoAudioPreferences.DEFAULT_MINIMIZE_ON_EXIT)

    val startMainPlayerFullscreen: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_START_MAIN_PLAYER_FULLSCREEN, VideoAudioPreferences.DEFAULT_START_MAIN_PLAYER_FULLSCREEN)

    val autoplay: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_AUTOPLAY, VideoAudioPreferences.DEFAULT_AUTOPLAY)

    val autoQueue: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_AUTO_QUEUE, VideoAudioPreferences.DEFAULT_AUTO_QUEUE)

    val resumeOnAudioFocusGain: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_RESUME_ON_AUDIO_FOCUS_GAIN, VideoAudioPreferences.DEFAULT_RESUME_ON_AUDIO_FOCUS_GAIN)

    val leftGestureControl: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_LEFT_GESTURE_CONTROL, VideoAudioPreferences.DEFAULT_LEFT_GESTURE_CONTROL)

    val rightGestureControl: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_RIGHT_GESTURE_CONTROL, VideoAudioPreferences.DEFAULT_RIGHT_GESTURE_CONTROL)

    val popupRememberSizePos: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_POPUP_REMEMBER_SIZE_POS, VideoAudioPreferences.DEFAULT_POPUP_REMEMBER_SIZE_POS)

    val useInexactSeek: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_USE_INEXACT_SEEK, VideoAudioPreferences.DEFAULT_USE_INEXACT_SEEK)

    val seekDuration: StateFlow<String>
        field = stringFlow(VideoAudioPreferences.KEY_SEEK_DURATION, VideoAudioPreferences.DEFAULT_SEEK_DURATION_MS)

    val clearQueueConfirmation: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_CLEAR_QUEUE_CONFIRMATION, VideoAudioPreferences.DEFAULT_CLEAR_QUEUE_CONFIRMATION)

    val ignoreHardwareMediaButtons: StateFlow<Boolean>
        field = booleanFlow(VideoAudioPreferences.KEY_IGNORE_HARDWARE_MEDIA_BUTTONS, VideoAudioPreferences.DEFAULT_IGNORE_HARDWARE_MEDIA_BUTTONS)

    fun setDefaultResolution(value: String) = defaultResolution.persist(VideoAudioPreferences.KEY_DEFAULT_RESOLUTION, value)

    fun setDefaultPopupResolution(value: String) = defaultPopupResolution.persist(VideoAudioPreferences.KEY_DEFAULT_POPUP_RESOLUTION, value)

    fun setMobileDataResolution(value: String) = mobileDataResolution.persist(VideoAudioPreferences.KEY_LIMIT_MOBILE_DATA_USAGE, value)

    fun setShowHigherResolutions(value: Boolean) {
        showHigherResolutions.persist(VideoAudioPreferences.KEY_SHOW_HIGHER_RESOLUTIONS, value)

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

    fun setVideoFormat(value: String) = videoFormat.persist(VideoAudioPreferences.KEY_DEFAULT_VIDEO_FORMAT, value)

    fun setAudioFormat(value: String) = audioFormat.persist(VideoAudioPreferences.KEY_DEFAULT_AUDIO_FORMAT, value)

    fun setPreferOriginalAudio(value: Boolean) = preferOriginalAudio.persist(VideoAudioPreferences.KEY_PREFER_ORIGINAL_AUDIO, value)

    fun setPreferDescriptiveAudio(value: Boolean) = preferDescriptiveAudio.persist(VideoAudioPreferences.KEY_PREFER_DESCRIPTIVE_AUDIO, value)

    fun setUseExternalVideoPlayer(value: Boolean) = useExternalVideoPlayer.persist(VideoAudioPreferences.KEY_USE_EXTERNAL_VIDEO_PLAYER, value)

    fun setUseExternalAudioPlayer(value: Boolean) = useExternalAudioPlayer.persist(VideoAudioPreferences.KEY_USE_EXTERNAL_AUDIO_PLAYER, value)

    fun setShowPlayWithKodi(value: Boolean) = showPlayWithKodi.persist(VideoAudioPreferences.KEY_SHOW_PLAY_WITH_KODI, value)

    fun setSeekbarPreviewThumbnail(value: String) = seekbarPreviewThumbnail.persist(VideoAudioPreferences.KEY_SEEKBAR_PREVIEW_THUMBNAIL, value)

    fun setPreferredOpenAction(value: String) = preferredOpenAction.persist(VideoAudioPreferences.KEY_PREFERRED_OPEN_ACTION, value)

    fun setMinimizeOnExit(value: String) = minimizeOnExit.persist(VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT, value)

    fun setStartMainPlayerFullscreen(value: Boolean) = startMainPlayerFullscreen.persist(VideoAudioPreferences.KEY_START_MAIN_PLAYER_FULLSCREEN, value)

    fun setAutoplay(value: String) = autoplay.persist(VideoAudioPreferences.KEY_AUTOPLAY, value)

    fun setAutoQueue(value: Boolean) = autoQueue.persist(VideoAudioPreferences.KEY_AUTO_QUEUE, value)

    fun setResumeOnAudioFocusGain(value: Boolean) = resumeOnAudioFocusGain.persist(VideoAudioPreferences.KEY_RESUME_ON_AUDIO_FOCUS_GAIN, value)

    fun setLeftGestureControl(value: String) = leftGestureControl.persist(VideoAudioPreferences.KEY_LEFT_GESTURE_CONTROL, value)

    fun setRightGestureControl(value: String) = rightGestureControl.persist(VideoAudioPreferences.KEY_RIGHT_GESTURE_CONTROL, value)

    fun setPopupRememberSizePos(value: Boolean) = popupRememberSizePos.persist(VideoAudioPreferences.KEY_POPUP_REMEMBER_SIZE_POS, value)

    /**
     * Enabling inexact seek hides 5-second-step durations. If the current selection becomes
     * hidden it is bumped by 5 seconds, matching the legacy screen (which also showed a toast;
     * the screen shows a snackbar based on the same [VideoAudioPreferences.adjustedSeekDurationMs]).
     */
    fun setUseInexactSeek(value: Boolean) {
        useInexactSeek.persist(VideoAudioPreferences.KEY_USE_INEXACT_SEEK, value)
        VideoAudioPreferences.adjustedSeekDurationMs(seekDuration.value, value)?.let(::setSeekDuration)
    }

    fun setSeekDuration(value: String) = seekDuration.persist(VideoAudioPreferences.KEY_SEEK_DURATION, value)

    fun setClearQueueConfirmation(value: Boolean) = clearQueueConfirmation.persist(VideoAudioPreferences.KEY_CLEAR_QUEUE_CONFIRMATION, value)

    fun setIgnoreHardwareMediaButtons(value: Boolean) = ignoreHardwareMediaButtons.persist(VideoAudioPreferences.KEY_IGNORE_HARDWARE_MEDIA_BUTTONS, value)

    private fun stringFlow(key: String, defaultValue: String) = MutableStateFlow(settings.getString(key, defaultValue))

    private fun booleanFlow(key: String, defaultValue: Boolean) = MutableStateFlow(settings.getBoolean(key, defaultValue))

    private fun MutableStateFlow<String>.persist(key: String, newValue: String) {
        settings.putString(key, newValue)
        value = newValue
    }

    private fun MutableStateFlow<Boolean>.persist(key: String, newValue: Boolean) {
        settings.putBoolean(key, newValue)
        value = newValue
    }
}
