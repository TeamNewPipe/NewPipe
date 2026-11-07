/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.Composable
import org.koin.core.annotation.Singleton

@Singleton(binds = [PlayerSettingsSections::class])
class JVMPlayerSettingsSections : PlayerSettingsSections {
    override val isAvailable = false

    @Composable
    override fun Render() = Unit
}
