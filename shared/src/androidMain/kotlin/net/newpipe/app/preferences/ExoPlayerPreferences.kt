/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.preferences

object ExoPlayerPreferences {
    const val KEY_PROGRESSIVE_LOAD_INTERVAL = "progressive_load_interval"
    const val KEY_USE_EXOPLAYER_DECODER_FALLBACK = "use_exoplayer_decoder_fallback_key"
    const val KEY_DISABLE_MEDIA_TUNNELING = "disable_media_tunneling_key"
    const val KEY_DISABLED_MEDIA_TUNNELING_AUTOMATICALLY = "disabled_media_tunneling_automatically_key"
    const val KEY_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND = "always_use_exoplayer_set_output_surface_workaround_key"

    const val PROGRESSIVE_LOAD_INTERVAL_EXOPLAYER_DEFAULT = "exoplayer_default"

    const val DEFAULT_PROGRESSIVE_LOAD_INTERVAL = "64"
    const val DEFAULT_USE_EXOPLAYER_DECODER_FALLBACK = false
    const val DEFAULT_DISABLE_MEDIA_TUNNELING = false
    const val DEFAULT_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND = false

    const val MEDIA_TUNNELING_AUTO_DISABLED = 1
    const val MEDIA_TUNNELING_USER_MANAGED = 0
    const val MEDIA_TUNNELING_FLAG_UNSET = -1

    val PROGRESSIVE_LOAD_INTERVALS = listOf("1", "16", "64", "256", PROGRESSIVE_LOAD_INTERVAL_EXOPLAYER_DEFAULT)
}
