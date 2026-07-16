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

    // Special resolution values that need a localized label
    const val BEST_RESOLUTION = "best_resolution"
    const val LIMIT_DATA_USAGE_NONE = "limit_data_usage_none"

    // Video/audio format values.
    const val VIDEO_MP4 = "video_mp4"
    const val VIDEO_WEBM = "video_webm"
    const val VIDEO_3GP = "video_3gp"
    const val AUDIO_M4A = "audio_m4a"
    const val AUDIO_WEBM = "audio_webm"

    const val DEFAULT_RESOLUTION = "720p60"
    const val DEFAULT_POPUP_RESOLUTION = "480p"
    const val DEFAULT_LIMIT_MOBILE_DATA_USAGE = LIMIT_DATA_USAGE_NONE
    const val DEFAULT_SHOW_HIGHER_RESOLUTIONS = false
    const val DEFAULT_VIDEO_FORMAT = VIDEO_MP4
    const val DEFAULT_AUDIO_FORMAT = AUDIO_M4A
    const val DEFAULT_PREFER_ORIGINAL_AUDIO = true
    const val DEFAULT_PREFER_DESCRIPTIVE_AUDIO = false

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

    fun resolutions(showHigher: Boolean): List<String> = insertHighResolutions(BASE_RESOLUTIONS, showHigher)

    fun mobileDataResolutions(showHigher: Boolean): List<String> = insertHighResolutions(BASE_MOBILE_DATA_RESOLUTIONS, showHigher)

    private fun insertHighResolutions(base: List<String>, showHigher: Boolean): List<String> =
        if (!showHigher) base else base.toMutableList().apply { addAll(1, HIGH_RESOLUTIONS) }
}
