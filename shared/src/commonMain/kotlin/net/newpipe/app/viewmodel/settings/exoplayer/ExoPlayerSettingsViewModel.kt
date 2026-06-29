/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings.exoplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.viewmodel.settings.BooleanPreference
import net.newpipe.app.viewmodel.settings.StringPreference
import org.koin.core.annotation.KoinViewModel

// Keys mirror app/src/main/res/values/settings_keys.xml verbatim so the
// Compose screen and the legacy fragment share SharedPreferences.
private const val PROGRESSIVE_LOAD_INTERVAL_KEY = "progressive_load_interval"
private const val USE_EXOPLAYER_DECODER_FALLBACK_KEY = "use_exoplayer_decoder_fallback_key"
private const val DISABLE_MEDIA_TUNNELING_KEY = "disable_media_tunneling_key"
private const val ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND_KEY =
    "always_use_exoplayer_set_output_surface_workaround_key"

internal const val PROGRESSIVE_LOAD_INTERVAL_1 = "1"
internal const val PROGRESSIVE_LOAD_INTERVAL_16 = "16"
internal const val PROGRESSIVE_LOAD_INTERVAL_64 = "64"
internal const val PROGRESSIVE_LOAD_INTERVAL_256 = "256"
internal const val PROGRESSIVE_LOAD_INTERVAL_EXOPLAYER_DEFAULT = "exoplayer_default"

@KoinViewModel
class ExoPlayerSettingsViewModel(
    settings: ObservableSettings
) : ViewModel() {

    private val progressiveLoadIntervalPref = StringPreference(
        PROGRESSIVE_LOAD_INTERVAL_KEY, PROGRESSIVE_LOAD_INTERVAL_64, settings, viewModelScope
    )
    private val useDecoderFallbackPref = BooleanPreference(
        USE_EXOPLAYER_DECODER_FALLBACK_KEY, false, settings, viewModelScope
    )
    private val disableMediaTunnelingPref = BooleanPreference(
        DISABLE_MEDIA_TUNNELING_KEY, false, settings, viewModelScope
    )
    private val alwaysUseSetOutputSurfaceWorkaroundPref = BooleanPreference(
        ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND_KEY, false, settings, viewModelScope
    )

    val progressiveLoadInterval: StateFlow<String> = progressiveLoadIntervalPref.state
    val useDecoderFallback: StateFlow<Boolean> = useDecoderFallbackPref.state
    val disableMediaTunneling: StateFlow<Boolean> = disableMediaTunnelingPref.state
    val alwaysUseSetOutputSurfaceWorkaround: StateFlow<Boolean> =
        alwaysUseSetOutputSurfaceWorkaroundPref.state

    fun setProgressiveLoadInterval(value: String) = progressiveLoadIntervalPref.set(value)
    fun toggleUseDecoderFallback(value: Boolean) = useDecoderFallbackPref.toggle(value)
    fun toggleDisableMediaTunneling(value: Boolean) = disableMediaTunnelingPref.toggle(value)
    fun toggleAlwaysUseSetOutputSurfaceWorkaround(value: Boolean) =
        alwaysUseSetOutputSurfaceWorkaroundPref.toggle(value)
}
