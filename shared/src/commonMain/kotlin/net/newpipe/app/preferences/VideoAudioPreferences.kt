/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.preferences

/**
 * Preference keys, values, and defaults for the Video & Audio settings screen.
 */

object VideoAudioPreferences {
    const val KEY_DEFAULT_RESOLUTION = "default_resolution"
    const val KEY_DEFAULT_POPUP_RESOLUTION = "default_popup_resolution"
    const val KEY_LIMIT_MOBILE_DATA_USAGE = "limit_mobile_data_usage"
    const val KEY_SHOW_HIGHER_RESOLUTIONS = "show_higher_resolutions"
    const val KEY_DEFAULT_VIDEO_FORMAT = "default_video_format"
    const val KEY_DEFAULT_AUDIO_FORMAT = "default_audio_format"
    const val KEY_PREFER_ORIGINAL_AUDIO = "prefer_original_audio"
    const val KEY_PREFER_DESCRIPTIVE_AUDIO = "prefer_descriptive_audio"

    // Player section
    const val KEY_USE_EXTERNAL_VIDEO_PLAYER = "use_external_video_player"
    const val KEY_USE_EXTERNAL_AUDIO_PLAYER = "use_external_audio_player"
    const val KEY_SHOW_PLAY_WITH_KODI = "show_play_with_kodi"
    const val KEY_SEEKBAR_PREVIEW_THUMBNAIL = "seekbar_preview_thumbnail_key"

    // Behavior section
    const val KEY_PREFERRED_OPEN_ACTION = "preferred_open_action_key"
    const val KEY_MINIMIZE_ON_EXIT = "minimize_on_exit_key"
    const val KEY_START_MAIN_PLAYER_FULLSCREEN = "start_main_player_fullscreen_key"
    const val KEY_AUTOPLAY = "autoplay_key"
    const val KEY_AUTO_QUEUE = "auto_queue_key"
    const val KEY_RESUME_ON_AUDIO_FOCUS_GAIN = "resume_on_audio_focus_gain"
    const val KEY_LEFT_GESTURE_CONTROL = "left_gesture_control"
    const val KEY_RIGHT_GESTURE_CONTROL = "right_gesture_control"
    const val KEY_POPUP_REMEMBER_SIZE_POS = "popup_remember_size_pos_key"
    const val KEY_USE_INEXACT_SEEK = "use_inexact_seek_key"
    const val KEY_SEEK_DURATION = "seek_duration"
    const val KEY_CLEAR_QUEUE_CONFIRMATION = "clear_queue_confirmation_key"
    const val KEY_IGNORE_HARDWARE_MEDIA_BUTTONS = "ignore_hardware_media_buttons_key"

    // Special resolution values that need a localized label
    const val BEST_RESOLUTION = "best_resolution"
    const val LIMIT_DATA_USAGE_NONE = "limit_data_usage_none"

    // Video/audio format values.
    const val VIDEO_MP4 = "video_mp4"
    const val VIDEO_WEBM = "video_webm"
    const val VIDEO_3GP = "video_3gp"
    const val AUDIO_M4A = "audio_m4a"
    const val AUDIO_WEBM = "audio_webm"

    // Seekbar preview thumbnail values.
    const val SEEKBAR_THUMBNAIL_HIGH_QUALITY = "seekbar_preview_thumbnail_high_quality"
    const val SEEKBAR_THUMBNAIL_LOW_QUALITY = "seekbar_preview_thumbnail_low_quality"
    const val SEEKBAR_THUMBNAIL_NONE = "seekbar_preview_thumbnail_none"

    // Preferred open action values.
    const val OPEN_ACTION_SHOW_INFO = "show_info"
    const val OPEN_ACTION_VIDEO_PLAYER = "video_player"
    const val OPEN_ACTION_BACKGROUND_PLAYER = "background_player"
    const val OPEN_ACTION_POPUP_PLAYER = "popup_player"
    const val OPEN_ACTION_DOWNLOAD = "download"
    const val OPEN_ACTION_ADD_TO_PLAYLIST = "add_to_playlist"
    const val OPEN_ACTION_ENQUEUE = "enqueue"
    const val OPEN_ACTION_ALWAYS_ASK = "always_ask_player"

    // Minimize on exit values.
    const val MINIMIZE_ON_EXIT_NONE = "minimize_on_exit_none_key"
    const val MINIMIZE_ON_EXIT_BACKGROUND = "minimize_on_exit_background_key"
    const val MINIMIZE_ON_EXIT_POPUP = "minimize_on_exit_popup_key"

    // Autoplay values.
    const val AUTOPLAY_ALWAYS = "autoplay_always_key"
    const val AUTOPLAY_WIFI = "autoplay_wifi_key"
    const val AUTOPLAY_NEVER = "autoplay_never_key"

    // Player gesture values.
    const val GESTURE_BRIGHTNESS = "brightness_control"
    const val GESTURE_VOLUME = "volume_control"
    const val GESTURE_NONE = "none_control"

    const val DEFAULT_RESOLUTION = "720p60"
    const val DEFAULT_POPUP_RESOLUTION = "480p"
    const val DEFAULT_LIMIT_MOBILE_DATA_USAGE = LIMIT_DATA_USAGE_NONE
    const val DEFAULT_SHOW_HIGHER_RESOLUTIONS = false
    const val DEFAULT_VIDEO_FORMAT = VIDEO_MP4
    const val DEFAULT_AUDIO_FORMAT = AUDIO_M4A
    const val DEFAULT_PREFER_ORIGINAL_AUDIO = true
    const val DEFAULT_PREFER_DESCRIPTIVE_AUDIO = false
    const val DEFAULT_USE_EXTERNAL_VIDEO_PLAYER = false
    const val DEFAULT_USE_EXTERNAL_AUDIO_PLAYER = false
    const val DEFAULT_SHOW_PLAY_WITH_KODI = false
    const val DEFAULT_SEEKBAR_PREVIEW_THUMBNAIL = SEEKBAR_THUMBNAIL_HIGH_QUALITY
    const val DEFAULT_PREFERRED_OPEN_ACTION = OPEN_ACTION_ALWAYS_ASK
    const val DEFAULT_MINIMIZE_ON_EXIT = MINIMIZE_ON_EXIT_BACKGROUND
    const val DEFAULT_START_MAIN_PLAYER_FULLSCREEN = false
    const val DEFAULT_AUTOPLAY = AUTOPLAY_WIFI
    const val DEFAULT_AUTO_QUEUE = false
    const val DEFAULT_RESUME_ON_AUDIO_FOCUS_GAIN = false
    const val DEFAULT_LEFT_GESTURE_CONTROL = GESTURE_BRIGHTNESS
    const val DEFAULT_RIGHT_GESTURE_CONTROL = GESTURE_VOLUME
    const val DEFAULT_POPUP_REMEMBER_SIZE_POS = true
    const val DEFAULT_USE_INEXACT_SEEK = false
    const val DEFAULT_SEEK_DURATION_MS = "10000"
    const val DEFAULT_CLEAR_QUEUE_CONFIRMATION = false
    const val DEFAULT_IGNORE_HARDWARE_MEDIA_BUTTONS = false

    private const val MILLIS_PER_SECOND = 1000

    /** Higher resolutions, hidden behind the "show higher resolutions" toggle. */
    val HIGH_RESOLUTIONS = listOf("2160p", "1440p")

    /** Base playback resolutions, highest quality first, "best" pinned at the top. */
    val BASE_RESOLUTIONS = listOf(
        BEST_RESOLUTION, "1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p"
    )

    /** Base mobile-data limit resolutions, "no limit" pinned at the top. */
    val BASE_MOBILE_DATA_RESOLUTIONS = listOf(
        LIMIT_DATA_USAGE_NONE, "1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p"
    )

    val VIDEO_FORMATS = listOf(VIDEO_MP4 to "MPEG-4", VIDEO_WEBM to "WebM", VIDEO_3GP to "3GP")

    val AUDIO_FORMATS = listOf(AUDIO_M4A to "M4A", AUDIO_WEBM to "WebM")

    /** Seek durations in milliseconds, stored as strings like the legacy app. */
    val SEEK_DURATIONS_MS = listOf("5000", "10000", "15000", "20000", "25000", "30000")

    fun resolutions(showHigher: Boolean): List<String> = insertHighResolutions(BASE_RESOLUTIONS, showHigher)

    fun mobileDataResolutions(showHigher: Boolean): List<String> = insertHighResolutions(BASE_MOBILE_DATA_RESOLUTIONS, showHigher)

    fun seekDurationSeconds(durationMs: String): Int = durationMs.toInt() / MILLIS_PER_SECOND

    /** ExoPlayer can't inexact-seek in 5-second steps in audio, so those durations are hidden. */
    fun seekDurationsMs(inexactSeek: Boolean): List<String> = if (!inexactSeek) SEEK_DURATIONS_MS else SEEK_DURATIONS_MS.filter { seekDurationSeconds(it) % 10 != 5 }

    /**
     * The seek duration to switch to when [inexactSeek] hides the currently selected one or null when no adjustment is needed.
     */
    fun adjustedSeekDurationMs(currentMs: String, inexactSeek: Boolean): String? {
        if (!inexactSeek) return null
        val seconds = seekDurationSeconds(currentMs)
        return if (seconds % 10 == 5) ((seconds + 5) * MILLIS_PER_SECOND).toString() else null
    }

    private fun insertHighResolutions(base: List<String>, showHigher: Boolean): List<String> = if (!showHigher) base else base.toMutableList().apply { addAll(1, HIGH_RESOLUTIONS) }
}
