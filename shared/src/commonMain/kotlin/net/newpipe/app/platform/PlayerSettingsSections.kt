/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific contents of the player settings screen: ExoPlayer settings on Android,
 * and each other platform's own player settings once it has any.
 */
interface PlayerSettingsSections {
    val isAvailable: Boolean

    @Composable
    fun Render()
}
