/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.Composable
import net.newpipe.app.screen.ExoPlayerSettingsSections
import org.koin.core.annotation.Singleton

@Singleton(binds = [PlayerSettingsSections::class])
class AndroidPlayerSettingsSections : PlayerSettingsSections {
    override val isAvailable = true

    @Composable
    override fun Render() = ExoPlayerSettingsSections()
}
