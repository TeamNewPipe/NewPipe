/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.screen.settings.BooleanPreference
import net.newpipe.app.screen.settings.StringPreference
import org.koin.core.annotation.KoinViewModel

// Keys mirror app/src/main/res/values/settings_keys.xml verbatim so the
// Compose screen and the legacy fragment share SharedPreferences.
private const val DEFAULT_RESOLUTION_KEY = "default_resolution"
private const val DEFAULT_POPUP_RESOLUTION_KEY = "default_popup_resolution"
private const val LIMIT_MOBILE_DATA_USAGE_KEY = "limit_mobile_data_usage"
private const val SHOW_HIGHER_RESOLUTIONS_KEY = "show_higher_resolutions"
private const val DEFAULT_VIDEO_FORMAT_KEY = "default_video_format"
private const val DEFAULT_AUDIO_FORMAT_KEY = "default_audio_format"
private const val PREFER_ORIGINAL_AUDIO_KEY = "prefer_original_audio"
private const val PREFER_DESCRIPTIVE_AUDIO_KEY = "prefer_descriptive_audio"
private const val USE_EXTERNAL_VIDEO_PLAYER_KEY = "use_external_video_player"
private const val USE_EXTERNAL_AUDIO_PLAYER_KEY = "use_external_audio_player"
private const val SHOW_PLAY_WITH_KODI_KEY = "show_play_with_kodi"
private const val SEEKBAR_PREVIEW_THUMBNAIL_KEY = "seekbar_preview_thumbnail_key"

internal const val RESOLUTION_BEST = "best_resolution"
internal const val RESOLUTION_2160 = "2160p"
internal const val RESOLUTION_1440 = "1440p"
internal const val RESOLUTION_1080_60 = "1080p60"
internal const val RESOLUTION_1080 = "1080p"
internal const val RESOLUTION_720_60 = "720p60"
internal const val RESOLUTION_720 = "720p"
internal const val RESOLUTION_480 = "480p"
internal const val RESOLUTION_360 = "360p"
internal const val RESOLUTION_240 = "240p"
internal const val RESOLUTION_144 = "144p"

internal const val LIMIT_DATA_OFF = "off"
internal const val LIMIT_DATA_WIFI_ONLY = "wifi_only"

internal const val VIDEO_FORMAT_MP4 = "MPEG-4"
internal const val VIDEO_FORMAT_WEBM = "WebM"
internal const val VIDEO_FORMAT_3GP = "3GPP"

internal const val AUDIO_FORMAT_M4A = "m4a"
internal const val AUDIO_FORMAT_WEBM = "webm"
internal const val AUDIO_FORMAT_OGG = "ogg"

internal const val THUMBNAIL_HIGH = "seekbar_preview_thumbnail_high_quality"
internal const val THUMBNAIL_LOW = "seekbar_preview_thumbnail_low_quality"
internal const val THUMBNAIL_OFF = "seekbar_preview_thumbnail_none"

@KoinViewModel
class PlayerSettingsViewModel(
    settings: ObservableSettings
) : ViewModel() {

    // ── ListPreferences ──────────────────────────────────────────────────

    private val defaultResolutionPref = StringPreference(
        DEFAULT_RESOLUTION_KEY, RESOLUTION_BEST, settings, viewModelScope
    )
    private val defaultPopupResolutionPref = StringPreference(
        DEFAULT_POPUP_RESOLUTION_KEY, RESOLUTION_BEST, settings, viewModelScope
    )
    private val limitMobileDataUsagePref = StringPreference(
        LIMIT_MOBILE_DATA_USAGE_KEY, LIMIT_DATA_OFF, settings, viewModelScope
    )
    private val defaultVideoFormatPref = StringPreference(
        DEFAULT_VIDEO_FORMAT_KEY, VIDEO_FORMAT_MP4, settings, viewModelScope
    )
    private val defaultAudioFormatPref = StringPreference(
        DEFAULT_AUDIO_FORMAT_KEY, AUDIO_FORMAT_M4A, settings, viewModelScope
    )
    private val seekbarThumbnailPref = StringPreference(
        SEEKBAR_PREVIEW_THUMBNAIL_KEY, THUMBNAIL_HIGH, settings, viewModelScope
    )

    val defaultResolution: StateFlow<String> = defaultResolutionPref.state
    val defaultPopupResolution: StateFlow<String> = defaultPopupResolutionPref.state
    val limitMobileDataUsage: StateFlow<String> = limitMobileDataUsagePref.state
    val defaultVideoFormat: StateFlow<String> = defaultVideoFormatPref.state
    val defaultAudioFormat: StateFlow<String> = defaultAudioFormatPref.state
    val seekbarThumbnail: StateFlow<String> = seekbarThumbnailPref.state

    fun setDefaultResolution(value: String) = defaultResolutionPref.set(value)
    fun setDefaultPopupResolution(value: String) = defaultPopupResolutionPref.set(value)
    fun setLimitMobileDataUsage(value: String) = limitMobileDataUsagePref.set(value)
    fun setDefaultVideoFormat(value: String) = defaultVideoFormatPref.set(value)
    fun setDefaultAudioFormat(value: String) = defaultAudioFormatPref.set(value)
    fun setSeekbarThumbnail(value: String) = seekbarThumbnailPref.set(value)

    // ── Switches ─────────────────────────────────────────────────────────

    private val showHigherResolutionsPref = BooleanPreference(
        SHOW_HIGHER_RESOLUTIONS_KEY, false, settings, viewModelScope
    )
    private val preferOriginalAudioPref = BooleanPreference(
        PREFER_ORIGINAL_AUDIO_KEY, true, settings, viewModelScope
    )
    private val preferDescriptiveAudioPref = BooleanPreference(
        PREFER_DESCRIPTIVE_AUDIO_KEY, false, settings, viewModelScope
    )
    private val useExternalVideoPlayerPref = BooleanPreference(
        USE_EXTERNAL_VIDEO_PLAYER_KEY, false, settings, viewModelScope
    )
    private val useExternalAudioPlayerPref = BooleanPreference(
        USE_EXTERNAL_AUDIO_PLAYER_KEY, false, settings, viewModelScope
    )
    private val showPlayWithKodiPref = BooleanPreference(
        SHOW_PLAY_WITH_KODI_KEY, false, settings, viewModelScope
    )

    val showHigherResolutions = showHigherResolutionsPref.state
    val preferOriginalAudio = preferOriginalAudioPref.state
    val preferDescriptiveAudio = preferDescriptiveAudioPref.state
    val useExternalVideoPlayer = useExternalVideoPlayerPref.state
    val useExternalAudioPlayer = useExternalAudioPlayerPref.state
    val showPlayWithKodi = showPlayWithKodiPref.state

    fun toggleShowHigherResolutions(v: Boolean) {
        showHigherResolutionsPref.toggle(v)
        // Legacy parity: when high-res is turned off, fall back to "best"
        // for any resolution prefs currently set to a high-res value.
        if (!v) {
            val highResValues = setOf(RESOLUTION_2160, RESOLUTION_1440)
            if (defaultResolutionPref.state.value in highResValues) {
                defaultResolutionPref.set(RESOLUTION_BEST)
            }
            if (defaultPopupResolutionPref.state.value in highResValues) {
                defaultPopupResolutionPref.set(RESOLUTION_BEST)
            }
        }
    }

    fun togglePreferOriginalAudio(v: Boolean) = preferOriginalAudioPref.toggle(v)
    fun togglePreferDescriptiveAudio(v: Boolean) = preferDescriptiveAudioPref.toggle(v)
    fun toggleUseExternalVideoPlayer(v: Boolean) = useExternalVideoPlayerPref.toggle(v)
    fun toggleUseExternalAudioPlayer(v: Boolean) = useExternalAudioPlayerPref.toggle(v)
    fun toggleShowPlayWithKodi(v: Boolean) = showPlayWithKodiPref.toggle(v)
}