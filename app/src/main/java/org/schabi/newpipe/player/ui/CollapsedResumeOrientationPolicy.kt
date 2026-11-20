/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.content.pm.ActivityInfo

/** Prevents a stale fullscreen landscape request from surviving a collapsed-player resume. */
object CollapsedResumeOrientationPolicy {
    @JvmStatic
    fun shouldRestore(
        collapsed: Boolean,
        videoSelected: Boolean,
        fullscreenOrEntering: Boolean,
        nativePip: Boolean,
        nonPhoneWindow: Boolean,
        requestedOrientation: Int
    ): Boolean {
        if (!collapsed || !videoSelected || nativePip || nonPhoneWindow) return false
        return fullscreenOrEntering || requestedOrientation in setOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        )
    }
}
