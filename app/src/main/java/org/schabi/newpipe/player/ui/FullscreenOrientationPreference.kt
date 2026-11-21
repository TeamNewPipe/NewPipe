/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager

/** A manual orientation override never forces vertical content into landscape by default. */
object FullscreenOrientationPreference {
    private const val KEY = "fullscreen_orientation_preference"

    enum class Mode { AUTO, PORTRAIT, LANDSCAPE }

    @JvmStatic
    fun targetOrientation(
        context: Context,
        fullscreen: Boolean,
        video: FullscreenOrientationPolicy.VideoContentOrientation
    ): Int {
        // On exit, preserve the current configuration; the player releases its lock
        // rather than requesting portrait while the phone is still in landscape.
        if (!fullscreen) return context.resources.configuration.orientation
        val preference = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY, "auto")
        val mode = when (preference) {
            "portrait" -> Mode.PORTRAIT
            "landscape" -> Mode.LANDSCAPE
            else -> Mode.AUTO
        }
        return targetOrientation(mode, fullscreen, video)
    }

    internal fun targetOrientation(
        mode: Mode,
        fullscreen: Boolean,
        video: FullscreenOrientationPolicy.VideoContentOrientation
    ): Int {
        if (!fullscreen) return Configuration.ORIENTATION_UNDEFINED
        return when (mode) {
            Mode.PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            Mode.LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            Mode.AUTO -> FullscreenOrientationPolicy.targetConfigurationOrientation(true, video)
        }
    }
}
