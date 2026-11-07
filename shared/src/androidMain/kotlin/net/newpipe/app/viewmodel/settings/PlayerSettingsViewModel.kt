/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_DISABLE_MEDIA_TUNNELING
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_PROGRESSIVE_LOAD_INTERVAL
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_USE_EXOPLAYER_DECODER_FALLBACK
import net.newpipe.app.preferences.ExoPlayerPreferences.KEY_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND
import net.newpipe.app.preferences.ExoPlayerPreferences.KEY_DISABLED_MEDIA_TUNNELING_AUTOMATICALLY
import net.newpipe.app.preferences.ExoPlayerPreferences.KEY_DISABLE_MEDIA_TUNNELING
import net.newpipe.app.preferences.ExoPlayerPreferences.KEY_PROGRESSIVE_LOAD_INTERVAL
import net.newpipe.app.preferences.ExoPlayerPreferences.KEY_USE_EXOPLAYER_DECODER_FALLBACK
import net.newpipe.app.preferences.ExoPlayerPreferences.MEDIA_TUNNELING_AUTO_DISABLED
import net.newpipe.app.preferences.ExoPlayerPreferences.MEDIA_TUNNELING_FLAG_UNSET
import net.newpipe.app.preferences.ExoPlayerPreferences.MEDIA_TUNNELING_USER_MANAGED
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PlayerSettingsViewModel(private val settings: Settings) : ViewModel() {

    val progressiveLoadInterval: StateFlow<String>
        field = stringFlow(KEY_PROGRESSIVE_LOAD_INTERVAL, DEFAULT_PROGRESSIVE_LOAD_INTERVAL)

    val useExoplayerDecoderFallback: StateFlow<Boolean>
        field = booleanFlow(
            KEY_USE_EXOPLAYER_DECODER_FALLBACK,
            DEFAULT_USE_EXOPLAYER_DECODER_FALLBACK
        )

    val disableMediaTunneling: StateFlow<Boolean>
        field = booleanFlow(KEY_DISABLE_MEDIA_TUNNELING, DEFAULT_DISABLE_MEDIA_TUNNELING)

    val alwaysUseSetOutputSurfaceWorkaround: StateFlow<Boolean>
        field = booleanFlow(
            KEY_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND,
            DEFAULT_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND
        )

    val mediaTunnelingAutoDisabled: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getInt(
                KEY_DISABLED_MEDIA_TUNNELING_AUTOMATICALLY,
                MEDIA_TUNNELING_FLAG_UNSET
            ) == MEDIA_TUNNELING_AUTO_DISABLED
        )

    fun setProgressiveLoadInterval(value: String) =
        progressiveLoadInterval.persist(KEY_PROGRESSIVE_LOAD_INTERVAL, value)

    fun setUseExoplayerDecoderFallback(value: Boolean) =
        useExoplayerDecoderFallback.persist(KEY_USE_EXOPLAYER_DECODER_FALLBACK, value)

    fun setDisableMediaTunneling(value: Boolean) {
        disableMediaTunneling.persist(KEY_DISABLE_MEDIA_TUNNELING, value)
        if (!value) {
            settings.putInt(
                KEY_DISABLED_MEDIA_TUNNELING_AUTOMATICALLY,
                MEDIA_TUNNELING_USER_MANAGED
            )
            mediaTunnelingAutoDisabled.value = false
        }
    }

    fun setAlwaysUseSetOutputSurfaceWorkaround(value: Boolean) =
        alwaysUseSetOutputSurfaceWorkaround.persist(
            KEY_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND,
            value
        )
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
